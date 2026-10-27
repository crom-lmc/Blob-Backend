package com.blog.util;

import cn.hutool.core.util.StrUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 全文检索辅助：BOOLEAN MODE 查询构造与命中高亮。
 * 纯进程内实现，不依赖任何外部中间件 / 检索服务。
 *
 * <p>跨端契约：返回的 highlight 字段只允许出现 {@code <mark>} 标签，其余内容必须是已转义的纯文本，
 * 不得混入原始富文本 / Markdown；前端以 v-html 直接渲染、不做二次转义或客户端高亮。</p>
 */
public final class SearchSupport {

    /** 布尔模式下具有特殊含义的字符，需剔除避免语法错误 */
    private static final Pattern SPECIAL = Pattern.compile("[+\\-~><()*\"@\\\\]");
    /** 已有的 &lt;mark&gt; 标签，替换前先剥离，避免嵌套 */
    private static final Pattern EXISTING_MARK = Pattern.compile("</?mark>", Pattern.CASE_INSENSITIVE);
    /** 兜底清洗：剥离任何残留的 HTML 标签，保证 highlight 只含 &lt;mark&gt; 与被转义纯文本 */
    private static final Pattern STRIP_TAG = Pattern.compile("<[^>]+>");

    private static final int SNIPPET_MAX = 200;

    private SearchSupport() {
    }

    /**
     * 构造 BOOLEAN MODE 查询串：按空白分词，词间以空格分隔（不强制 + 必须包含）。
     * 依赖 MySQL ngram 解析器对中文自动切分。
     *
     * <p>注意：MySQL 5.7 ngram 最小词长为 2，单个汉字（如「锁」）在 ngram 索引中
     * 不存在对应 token。若给每个词加 +（AND 必须包含），单字词无法命中会导致
     * 整条布尔查询归零（返回 0 条）。因此这里只用空格分隔，由 BOOLEAN MODE 按
     * 「命中词越多相关度越高」排序，兼顾召回与精度。</p>
     */
    public static String toBooleanQuery(String keyword) {
        if (StrUtil.isBlank(keyword)) {
            return "";
        }
        String cleaned = SPECIAL.matcher(keyword).replaceAll(" ");
        List<String> tokens = new ArrayList<>();
        for (String t : cleaned.split("\\s+")) {
            t = t.trim();
            if (!t.isEmpty()) {
                tokens.add(t);
            }
        }
        return String.join(" ", tokens);
    }

    /**
     * 从关键词中解析出用于高亮/匹配的词项（长度 >= 2，过滤单字噪声）。
     */
    public static List<String> termsOf(String keyword) {
        List<String> terms = new ArrayList<>();
        if (StrUtil.isBlank(keyword)) {
            return terms;
        }
        String cleaned = SPECIAL.matcher(keyword).replaceAll(" ");
        for (String t : cleaned.split("\\s+")) {
            t = t.trim();
            if (t.length() >= 2) {
                terms.add(t);
            }
        }
        return terms;
    }

    /**
     * 统一的高亮入口（供全量检索与 LIKE 回退分支共用，保证前后端表现一致）：
     * 优先用「摘要」高亮；摘要不含命中词则用「标题」；再否则从「正文 search_text」截取命中片段高亮
     * （类似百度摘要）；若均无命中则退化为短片段。
     *
     * @param summary 文章摘要（可为空）
     * @param title   文章标题（可为空）
     * @param body    正文纯文本（search_text，可为空）
     * @param keyword 用户输入关键词
     */
    public static String highlightBest(String summary, String title, String body, String keyword) {
        List<String> terms = termsOf(keyword);
        if (terms.isEmpty()) {
            // 单字/短词：ngram 无法命中，截取含原词的片段（不打标，避免噪声），至少保证召回可见
            return snippetAroundFirst(summary, title, body, keyword);
        }
        if (hasAny(summary, terms)) {
            return highlight(summary, keyword);
        }
        if (hasAny(title, terms)) {
            return highlight(title, keyword);
        }
        if (hasAny(body, terms)) {
            return highlight(body, keyword);
        }
        String base = firstNonBlank(summary, title, body);
        return base == null ? "" : ellipsis(base, SNIPPET_MAX);
    }

    /**
     * 对纯文本中的命中词包裹 &lt;mark&gt;（文本须为已 XSS 清洗的纯文本，安全）。
     * 单字噪声大，跳过；按词长降序替换避免短词截断长词；片段以首个命中词为中心。
     */
    public static String highlight(String text, String keyword) {
        if (StrUtil.isBlank(text)) {
            return "";
        }
        if (StrUtil.isBlank(keyword)) {
            return ellipsis(text, SNIPPET_MAX);
        }
        List<String> terms = termsOf(keyword);
        if (terms.isEmpty()) {
            return ellipsis(text, SNIPPET_MAX);
        }
        terms.sort((a, b) -> b.length() - a.length());

        // 先剥离残留 HTML 标签与已有 <mark>，再做高亮，确保结果只含 <mark> 与纯文本
        String plain = STRIP_TAG.matcher(EXISTING_MARK.matcher(text).replaceAll("")).replaceAll(" ");
        String lower = plain.toLowerCase();
        int pos = -1;
        for (String t : terms) {
            int idx = lower.indexOf(t.toLowerCase());
            if (idx >= 0 && (pos < 0 || idx < pos)) {
                pos = idx;
            }
        }
        int start = pos < 0 ? 0 : Math.max(0, pos - 40);
        int end = Math.min(plain.length(), start + SNIPPET_MAX);
        String snippet = (start > 0 ? "…" : "") + plain.substring(start, end) + (end < plain.length() ? "…" : "");

        for (String term : terms) {
            if (term.isEmpty()) {
                continue;
            }
            snippet = Pattern.compile(Pattern.quote(term), Pattern.CASE_INSENSITIVE)
                    .matcher(snippet)
                    .replaceAll("<mark>" + term + "</mark>");
        }
        return snippet;
    }

    /**
     * 单字/短词（无 >=2 长度词项）时：在 摘要/标题/正文 中定位原词并截取居中片段（不打标）。
     */
    private static String snippetAroundFirst(String summary, String title, String body, String keyword) {
        String kw = StrUtil.isBlank(keyword) ? "" : keyword.trim();
        if (kw.length() >= 1) {
            for (String c : new String[]{summary, title, body}) {
                if (StrUtil.isNotBlank(c)) {
                    int idx = c.toLowerCase().indexOf(kw.toLowerCase());
                    if (idx >= 0) {
                        int start = Math.max(0, idx - 40);
                        int end = Math.min(c.length(), start + SNIPPET_MAX);
                        return (start > 0 ? "…" : "") + c.substring(start, end) + (end < c.length() ? "…" : "");
                    }
                }
            }
        }
        String base = firstNonBlank(summary, title, body);
        return base == null ? "" : ellipsis(base, SNIPPET_MAX);
    }

    private static boolean hasAny(String text, List<String> terms) {
        if (StrUtil.isBlank(text)) {
            return false;
        }
        String lower = text.toLowerCase();
        for (String t : terms) {
            if (lower.contains(t.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private static String firstNonBlank(String... texts) {
        for (String t : texts) {
            if (StrUtil.isNotBlank(t)) {
                return t;
            }
        }
        return null;
    }

    private static String ellipsis(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
