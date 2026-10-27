package com.blog.util;

import cn.hutool.core.util.StrUtil;
import com.blog.module.article.dto.TocItem;
import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.footnotes.FootnoteExtension;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.ext.toc.TocExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.DataHolder;
import com.vladsch.flexmark.util.data.MutableDataSet;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 渲染工具：
 * 1. 使用 flexmark 渲染 HTML（表格、任务列表、删除线、自动链接、脚注、TOC）；
 * 2. 公式（$$...$$ / $...$）先抽出占位，渲染后还原，交由前端 KaTeX 渲染；
 * 3. 统一做 XSS 白名单过滤；
 * 4. 为标题生成锚点 id 并抽取目录（TOC）。
 */
@Slf4j
public final class MarkdownUtils {

    private static final DataHolder OPTIONS = new MutableDataSet()
            .set(Parser.EXTENSIONS, Arrays.asList(
                    TablesExtension.create(),
                    StrikethroughExtension.create(),
                    AutolinkExtension.create(),
                    TaskListExtension.create(),
                    FootnoteExtension.create(),
                    TocExtension.create()))
            .set(HtmlRenderer.SOFT_BREAK, "<br />\n")
            .set(TablesExtension.CLASS_NAME, "md-table");

    private static final Parser PARSER = Parser.builder(OPTIONS).build();
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder(OPTIONS).build();

    /** 块级公式 $$...$$ */
    private static final Pattern MATH_BLOCK = Pattern.compile("\\$\\$([\\s\\S]+?)\\$\\$");
    /** 行内公式 $...$ */
    private static final Pattern MATH_INLINE = Pattern.compile("(?<!\\$)\\$([^$\\n]+?)\\$(?!\\$)");

    /** XSS 白名单：在 relaxed 基础上放开代码块、公式容器、表格等必要属性 */
    private static final Safelist SAFELIST = Safelist.relaxed()
            .addTags("div", "span", "figure", "figcaption", "input", "section")
            .addAttributes(":all", "class", "id")
            .addAttributes("img", "loading", "width", "height", "decoding")
            .addAttributes("a", "target", "rel", "id")
            .addAttributes("input", "type", "checked", "disabled", "readonly")
            .addAttributes("td", "colspan", "rowspan", "align")
            .addAttributes("th", "colspan", "rowspan", "align")
            .addAttributes("ol", "start")
            .addAttributes("pre", "data-lang")
            .addProtocols("a", "href", "http", "https", "mailto")
            .addProtocols("img", "src", "http", "https");

    private MarkdownUtils() {
    }

    /**
     * 渲染 Markdown 为安全 HTML，并抽取目录。
     */
    public static RenderResult render(String markdown) {
        if (StrUtil.isBlank(markdown)) {
            return new RenderResult("", List.of());
        }
        // 1. 抽出公式，避免 markdown 转义破坏公式内容
        List<String> mathTexts = new ArrayList<>();
        String md = MATH_BLOCK.matcher(markdown)
                .replaceAll(m -> placeholder(mathTexts, m.group(1), true));
        md = MATH_INLINE.matcher(md)
                .replaceAll(m -> placeholder(mathTexts, m.group(1), false));

        // 2. Markdown → HTML
        Node document = PARSER.parse(md);
        String html = RENDERER.render(document);

        // 3. 还原公式（保留原始 LaTeX，交给前端 KaTeX）
        html = restoreMath(html, mathTexts);

        // 4. XSS 过滤
        String safeHtml = Jsoup.clean(html, SAFELIST);

        // 5. 标题锚点 + 目录
        return decorate(safeHtml);
    }

    /**
     * 只渲染 HTML（不生成目录）。
     */
    public static String renderToHtml(String markdown) {
        return render(markdown).getHtml();
    }

    /**
     * 从已渲染并清洗过的 HTML 中抽取目录（避免详情页重复渲染 Markdown）。
     */
    public static List<TocItem> buildTocFromHtml(String html) {
        if (StrUtil.isBlank(html)) {
            return List.of();
        }
        Document document = Jsoup.parseBodyFragment(html);
        List<TocItem> toc = new ArrayList<>();
        int index = 0;
        for (Element heading : document.select("h1,h2,h3,h4,h5,h6")) {
            String id = heading.attr("id");
            if (id == null || id.isBlank()) {
                id = "heading-" + (++index);
            }
            int level = Character.digit(heading.tagName().charAt(1), 10);
            if (level >= 2 && level <= 4) {
                toc.add(new TocItem(id, level, heading.text()));
            }
        }
        return toc;
    }

    /**
     * 统计字数：中文按字计，英文按单词计。
     */
    public static int countWords(String markdown) {
        if (StrUtil.isBlank(markdown)) {
            return 0;
        }
        // 去掉代码块与 HTML 标签后再统计
        String text = markdown.replaceAll("```[\\s\\S]*?```", " ")
                .replaceAll("`[^`]*`", " ")
                .replaceAll("<[^>]+>", " ");
        int chinese = text.replaceAll("[^\\u4e00-\\u9fa5]", "").length();
        String ascii = text.replaceAll("[\\u4e00-\\u9fa5]", " ");
        int words = ascii.trim().isEmpty() ? 0 : ascii.trim().split("\\s+").length;
        return chinese + words;
    }

    /**
     * 预计阅读时长（分钟），按每分钟 300 字估算。
     */
    public static int readingMinutes(int wordCount) {
        return Math.max(1, (int) Math.round(wordCount / 300.0));
    }

    /**
     * 去掉 HTML 标签，用于生成摘要。
     */
    public static String plainText(String html, int maxLength) {
        if (StrUtil.isBlank(html)) {
            return "";
        }
        String text = Jsoup.parse(html).text();
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "…";
    }

    private static String placeholder(List<String> store, String latex, boolean block) {
        int index = store.size();
        store.add(block ? "@@MATHBLOCK" + index + "@@" : "@@MATHINLINE" + index + "@@");
        return "@@MATH" + index + "@@";
    }

    private static String restoreMath(String html, List<String> store) {
        String result = html;
        for (int i = 0; i < store.size(); i++) {
            String raw = store.get(i);
            boolean block = raw.startsWith("@@MATHBLOCK");
            String latex = block
                    ? raw.substring("@@MATHBLOCK".length(), raw.length() - 2)
                    : raw.substring("@@MATHINLINE".length(), raw.length() - 2);
            String wrapped = block
                    ? "<div class=\"math math-block\">\\[" + latex + "\\]</div>"
                    : "<span class=\"math math-inline\">\\(" + latex + "\\)</span>";
            result = result.replace("@@MATH" + i + "@@", Matcher.quoteReplacement(wrapped));
        }
        return result;
    }

    /**
     * 为 h1~h6 生成锚点 id，并抽取 h2~h4 作为目录。
     */
    private static RenderResult decorate(String safeHtml) {
        Document document = Jsoup.parseBodyFragment(safeHtml);
        List<TocItem> toc = new ArrayList<>();
        int index = 0;
        for (Element heading : document.select("h1,h2,h3,h4,h5,h6")) {
            String text = heading.text();
            String id = "heading-" + (++index);
            heading.attr("id", id);
            int level = Character.digit(heading.tagName().charAt(1), 10);
            if (level >= 2 && level <= 4) {
                toc.add(new TocItem(id, level, text));
            }
        }
        return new RenderResult(document.body().html(), toc);
    }

    /**
     * 渲染结果：HTML + 目录。
     */
    @Data
    public static class RenderResult {
        private final String html;
        private final List<TocItem> toc;

        public RenderResult(String html, List<TocItem> toc) {
            this.html = html;
            this.toc = toc;
        }
    }
}
