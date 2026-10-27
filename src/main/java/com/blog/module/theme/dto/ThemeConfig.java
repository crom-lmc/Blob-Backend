package com.blog.module.theme.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 主题配置模型（对应 t_theme.config_json）。
 * <p>
 * 前后端唯一约定：后台保存该 JSON，后端据此生成 CSS 变量字符串下发前台，
 * 前台所有样式只允许引用 CSS 变量，禁止硬编码颜色/字号/圆角。
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ThemeConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    private Meta meta = new Meta();

    private Color color = new Color();

    private Font font = new Font();

    private Radius radius = new Radius();

    private Space space = new Space();

    private Layout layout = new Layout();

    private List<HomeBlock> homeBlocks = new ArrayList<>();

    private Dark dark = new Dark();

    /** 自定义 CSS（追加在变量之后，优先级最高，保存时做安全校验） */
    private String customCss = "";

    /** 自定义 &lt;head&gt; 片段（统计代码、字体外链等） */
    private String customHeadHtml = "";

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Meta implements Serializable {
        private String name = "默认主题";
        private int version = 1;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Color implements Serializable {
        private String primary = "#4f46e5";
        private String primaryHover = "#4338ca";
        private String primarySubtle = "#eef2ff";
        private String bg = "#ffffff";
        private String bgSubtle = "#f7f8fa";
        private String surface = "#ffffff";
        private String text = "#1f2328";
        private String textMuted = "#6b7280";
        private String textInvert = "#ffffff";
        private String border = "#e5e7eb";
        private String link = "#4f46e5";
        private String success = "#16a34a";
        private String warning = "#f59e0b";
        private String danger = "#dc2626";
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Font implements Serializable {
        private String familyBody = "system-ui, -apple-system, 'PingFang SC', 'Microsoft YaHei', sans-serif";
        private String familyHeading = "inherit";
        private String familyCode = "'JetBrains Mono', Consolas, monospace";
        /** 基准字号 px */
        private int sizeBase = 16;
        /** 标题字号缩放比例 */
        private double scaleRatio = 1.25;
        private double lineHeight = 1.75;
        /** 字间距 px */
        private double letterSpacing = 0;
        private int headingWeight = 700;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Radius implements Serializable {
        private int sm = 6;
        private int md = 10;
        private int lg = 16;
        private int full = 9999;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Space implements Serializable {
        /** 间距单位 px（compact=2 / comfortable=4 / spacious=6） */
        private int unit = 4;
        /** 正文宽度 px */
        private int contentWidth = 760;
        /** 容器宽度 px */
        private int containerWidth = 1200;
        /** 区块间距 px */
        private int sectionGap = 32;
        /** 卡片内边距 px */
        private int cardPadding = 20;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Layout implements Serializable {
        /** list / grid / magazine */
        private String homeLayout = "list";
        /** left / right / none */
        private String sidebar = "right";
        /** flat / elevated / bordered */
        private String cardStyle = "flat";
        /** compact / comfortable / spacious */
        private String density = "comfortable";
        /** fixed / static */
        private String headerStyle = "fixed";
        /** top / left / background */
        private String coverPosition = "top";
        private boolean showToc = true;
        private boolean showBreadcrumb = true;
        private boolean showExcerpt = true;
        private List<String> articleMetaOrder = new ArrayList<>(
                List.of("date", "category", "tags", "views"));
        private List<String> postCardFields = new ArrayList<>(
                List.of("cover", "title", "excerpt", "meta", "tags"));
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HomeBlock implements Serializable {
        /** hero / featured / latest / tagCloud / newsletter */
        private String type = "latest";
        private boolean enabled = true;
        private int order = 1;
        /** 各区块自定义属性，结构由 type 决定 */
        private Map<String, Object> props = new LinkedHashMap<>();
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Dark implements Serializable {
        private boolean enabled = true;
        /** light / dark / system */
        private String defaultMode = "system";
        private DarkTokens tokens = new DarkTokens();
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DarkTokens implements Serializable {
        private String bg = "#12141a";
        private String surface = "#1b1e26";
        private String text = "#e6e8ee";
        private String textMuted = "#9aa1b1";
        private String border = "#2a2e38";
    }
}
