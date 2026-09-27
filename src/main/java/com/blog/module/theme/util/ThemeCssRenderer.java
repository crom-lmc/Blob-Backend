package com.blog.module.theme.util;

import com.blog.module.theme.dto.ThemeConfig;
import com.blog.util.CssUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Token → CSS 变量映射（前端唯一约定）。
 * <p>
 * 生成的 CSS 结构：
 * <pre>
 * :root{ --color-primary: ...; --font-size-h1: ...; ... }
 * [data-theme="dark"]{ --color-bg: ...; ... }
 * /* customCss *\/
 * </pre>
 */
public final class ThemeCssRenderer {

    private ThemeCssRenderer() {
    }

    /**
     * 生成完整的 CSS 变量文本。
     */
    public static String render(ThemeConfig config) {
        if (config == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        appendRoot(sb, config);
        appendDark(sb, config);
        appendCustom(sb, config);
        return sb.toString();
    }

    private static void appendRoot(StringBuilder sb, ThemeConfig config) {
        Map<String, String> vars = new LinkedHashMap<>();

        // 颜色
        ThemeConfig.Color color = nullSafe(config.getColor(), new ThemeConfig.Color());
        put(vars, "color-primary", color.getPrimary());
        put(vars, "color-primary-hover", color.getPrimaryHover());
        put(vars, "color-primary-subtle", color.getPrimarySubtle());
        put(vars, "color-bg", color.getBg());
        put(vars, "color-bg-subtle", color.getBgSubtle());
        put(vars, "color-surface", color.getSurface());
        put(vars, "color-text", color.getText());
        put(vars, "color-text-muted", color.getTextMuted());
        put(vars, "color-text-invert", color.getTextInvert());
        put(vars, "color-border", color.getBorder());
        put(vars, "color-link", color.getLink());
        put(vars, "color-success", color.getSuccess());
        put(vars, "color-warning", color.getWarning());
        put(vars, "color-danger", color.getDanger());

        // 字体
        ThemeConfig.Font font = nullSafe(config.getFont(), new ThemeConfig.Font());
        put(vars, "font-body", font.getFamilyBody());
        put(vars, "font-heading", font.getFamilyHeading());
        put(vars, "font-code", font.getFamilyCode());
        put(vars, "font-size-base", px(font.getSizeBase()));
        put(vars, "font-scale-ratio", String.valueOf(font.getScaleRatio()));
        put(vars, "line-height", String.valueOf(font.getLineHeight()));
        put(vars, "letter-spacing", px(font.getLetterSpacing()));
        put(vars, "heading-weight", String.valueOf(font.getHeadingWeight()));

        // 标题字号：sizeBase × scaleRatio^n
        double base = font.getSizeBase() <= 0 ? 16 : font.getSizeBase();
        double ratio = font.getScaleRatio() <= 1 ? 1.25 : font.getScaleRatio();
        vars.put("--font-size-h1", px(round2(base * Math.pow(ratio, 4))));
        vars.put("--font-size-h2", px(round2(base * Math.pow(ratio, 3))));
        vars.put("--font-size-h3", px(round2(base * Math.pow(ratio, 2))));
        vars.put("--font-size-h4", px(round2(base * ratio)));
        vars.put("--font-size-h5", px(round2(base)));
        vars.put("--font-size-h6", px(round2(base * 0.875)));

        // 圆角
        ThemeConfig.Radius radius = nullSafe(config.getRadius(), new ThemeConfig.Radius());
        put(vars, "radius-sm", px(radius.getSm()));
        put(vars, "radius-md", px(radius.getMd()));
        put(vars, "radius-lg", px(radius.getLg()));
        put(vars, "radius-full", px(radius.getFull()));

        // 间距（密度通过覆盖 space-unit 实现）
        ThemeConfig.Space space = nullSafe(config.getSpace(), new ThemeConfig.Space());
        int unit = densityUnit(config, space.getUnit());
        put(vars, "space-unit", px(unit));
        for (int i = 1; i <= 8; i++) {
            vars.put("--space-" + i, px(unit * i));
        }
        put(vars, "content-width", px(space.getContentWidth()));
        put(vars, "container-width", px(space.getContainerWidth()));
        put(vars, "section-gap", px(space.getSectionGap()));
        put(vars, "card-padding", px(space.getCardPadding()));

        // 布局相关
        ThemeConfig.Layout layout = nullSafe(config.getLayout(), new ThemeConfig.Layout());
        put(vars, "header-position", "fixed".equalsIgnoreCase(layout.getHeaderStyle()) ? "fixed" : "static");
        put(vars, "card-shadow", cardShadow(layout.getCardStyle()));
        put(vars, "card-border", cardBorder(layout.getCardStyle()));
        put(vars, "cover-position", layout.getCoverPosition());

        sb.append(":root{\n");
        vars.forEach((key, value) -> sb.append("  ").append(key).append(": ").append(value).append(";\n"));
        sb.append("}\n");
    }

    private static void appendDark(StringBuilder sb, ThemeConfig config) {
        ThemeConfig.Dark dark = config.getDark();
        if (dark == null || !dark.isEnabled() || dark.getTokens() == null) {
            return;
        }
        Map<String, String> vars = new LinkedHashMap<>();
        ThemeConfig.DarkTokens tokens = dark.getTokens();
        if (notBlank(tokens.getBg())) {
            vars.put("--color-bg", tokens.getBg());
        }
        if (notBlank(tokens.getSurface())) {
            vars.put("--color-surface", tokens.getSurface());
        }
        if (notBlank(tokens.getText())) {
            vars.put("--color-text", tokens.getText());
        }
        if (notBlank(tokens.getTextMuted())) {
            vars.put("--color-text-muted", tokens.getTextMuted());
        }
        if (notBlank(tokens.getBorder())) {
            vars.put("--color-border", tokens.getBorder());
        }
        // 深色下弱化背景层级
        if (notBlank(tokens.getBg())) {
            vars.put("--color-bg-subtle", tokens.getBg());
        }
        if (vars.isEmpty()) {
            return;
        }
        sb.append("\n[data-theme=\"dark\"]{\n");
        vars.forEach((key, value) -> sb.append("  ").append(key).append(": ").append(value).append(";\n"));
        sb.append("}\n");
    }

    private static void appendCustom(StringBuilder sb, ThemeConfig config) {
        String css = config.getCustomCss();
        if (css == null || css.isBlank()) {
            return;
        }
        // 生成前再次兜底校验，避免历史脏数据被下发到前台
        try {
            CssUtils.checkCustomCss(css);
        } catch (RuntimeException e) {
            return;
        }
        sb.append("\n/* ===== 自定义 CSS ===== */\n").append(css.trim()).append('\n');
    }

    /**
     * 密度映射：compact=2 / comfortable=4 / spacious=6
     */
    private static int densityUnit(ThemeConfig config, int fallback) {
        String density = config.getLayout() == null ? null : config.getLayout().getDensity();
        if (density == null) {
            return fallback;
        }
        return switch (density.toLowerCase()) {
            case "compact" -> 2;
            case "spacious" -> 6;
            default -> 4;
        };
    }

    private static String cardShadow(String cardStyle) {
        if (cardStyle == null) {
            return "none";
        }
        return switch (cardStyle.toLowerCase()) {
            case "elevated" -> "0 4px 16px rgba(15, 23, 42, 0.08)";
            case "bordered" -> "none";
            default -> "none";
        };
    }

    private static String cardBorder(String cardStyle) {
        if (cardStyle == null) {
            return "1px solid var(--color-border)";
        }
        return switch (cardStyle.toLowerCase()) {
            case "elevated" -> "1px solid transparent";
            default -> "1px solid var(--color-border)";
        };
    }

    private static void put(Map<String, String> vars, String name, String value) {
        if (notBlank(value)) {
            vars.put("--" + name, value);
        }
    }

    private static void put(Map<String, String> vars, String name, int value) {
        vars.put("--" + name, px(value));
    }

    private static String px(double value) {
        String text = Math.abs(value - Math.round(value)) < 0.001
                ? String.valueOf(Math.round(value))
                : String.valueOf(round2(value));
        return text + "px";
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static <T> T nullSafe(T value, T fallback) {
        return value == null ? fallback : value;
    }
}
