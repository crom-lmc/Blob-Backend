package com.blog.util;

import com.blog.common.BusinessException;
import com.blog.common.ErrorCode;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * CSS 安全校验：自定义 CSS / 自定义 HTML 片段的安全把关。
 */
public final class CssUtils {

    /** 允许 #rgb / #rrggbb / #rrggbbaa */
    private static final Pattern HEX_COLOR = Pattern.compile("^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$");

    /** 危险特征：外链样式表、脚本执行、IE 表达式、data URI 中的脚本 */
    private static final String[] DANGEROUS = {
            "@import", "javascript:", "vbscript:", "expression(", "<script", "</script",
            "url(javascript", "@charset", "behavior:", "-moz-binding"
    };

    private CssUtils() {
    }

    /**
     * 校验自定义 CSS，不合法时抛出业务异常。
     */
    public static void checkCustomCss(String css) {
        if (css == null || css.isBlank()) {
            return;
        }
        if (css.length() > 20000) {
            throw new BusinessException(ErrorCode.THEME_CSS_UNSAFE, "自定义 CSS 长度不能超过 20000 字符");
        }
        String lower = css.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        for (String danger : DANGEROUS) {
            if (lower.contains(danger)) {
                throw new BusinessException(ErrorCode.THEME_CSS_UNSAFE,
                        "自定义 CSS 包含不允许的内容：" + danger + "（禁止 @import 外链与 javascript: 等脚本）");
            }
        }
    }

    /**
     * 校验自定义 HTML 片段（只允许常规标签，禁止脚本与事件属性）。
     */
    public static void checkCustomHtml(String html) {
        if (html == null || html.isBlank()) {
            return;
        }
        if (html.length() > 20000) {
            throw new BusinessException(ErrorCode.THEME_CSS_UNSAFE, "自定义 HTML 长度不能超过 20000 字符");
        }
        String lower = html.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        String[] forbidden = {"<script", "<iframe", "<object", "<embed", "javascript:", "onload=", "onerror=", "onclick="};
        for (String f : forbidden) {
            if (lower.contains(f)) {
                throw new BusinessException(ErrorCode.THEME_CSS_UNSAFE, "自定义 HTML 包含不允许的内容：" + f);
            }
        }
    }

    public static boolean isHexColor(String value) {
        return value != null && HEX_COLOR.matcher(value.trim()).matches();
    }

    /**
     * camelCase → kebab-case
     */
    public static String kebab(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                sb.append('-').append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
