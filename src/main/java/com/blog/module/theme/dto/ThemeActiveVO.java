package com.blog.module.theme.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 前台使用的生效主题视图对象：{ version, tokens, css, layout }
 */
@Data
public class ThemeActiveVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String name;

    /** 版本号，前台据此判断是否需要刷新本地缓存 */
    private int version;

    /** 完整配置 token */
    private ThemeConfig tokens;

    /** 生成的 CSS 变量文本，前端直接注入 <style id="theme-vars"> */
    private String css;

    private ThemeConfig.Layout layout;

    private String customCss;

    private String customHeadHtml;

    private boolean darkEnabled;

    private String defaultMode;

    private LocalDateTime updatedAt;
}
