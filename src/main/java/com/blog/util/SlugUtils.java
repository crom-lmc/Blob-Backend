package com.blog.util;

import cn.hutool.core.util.StrUtil;

import java.util.Locale;

/**
 * 生成 URL 友好的别名（保留中文，中文 slug 由浏览器自动编码）。
 */
public final class SlugUtils {

    private SlugUtils() {
    }

    public static String slugify(String text) {
        String s = StrUtil.trimToEmpty(text).toLowerCase(Locale.ROOT);
        // 空白、下划线、斜杠统一为连字符
        s = s.replaceAll("[\\s_/\\\\]+", "-");
        // 仅保留字母、数字、连字符（\p{L} 包含中文）
        s = s.replaceAll("[^\\p{L}\\p{N}-]", "");
        s = s.replaceAll("-{2,}", "-");
        s = s.replaceAll("^-+|-+$", "");
        if (s.length() > 100) {
            s = s.substring(0, 100).replaceAll("-+$", "");
        }
        return StrUtil.isBlank(s) ? "post-" + System.currentTimeMillis() : s;
    }
}
