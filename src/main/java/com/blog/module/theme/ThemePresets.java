package com.blog.module.theme;

import com.blog.module.theme.dto.ThemeConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 内置预设主题：默认蓝、暗夜、极简黑白、暖橙、森绿。
 * 预设在默认主题 JSON 的基础上覆盖颜色 / 深色 token / 少量布局参数派生而来。
 */
@Slf4j
@Component
public class ThemePresets {

    private static final String DEFAULT_THEME_JSON = "theme/default-theme.json";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile ThemeConfig defaultConfig;

    /**
     * 读取默认主题配置（classpath:theme/default-theme.json）。
     */
    public ThemeConfig defaultConfig() {
        if (defaultConfig == null) {
            synchronized (this) {
                if (defaultConfig == null) {
                    defaultConfig = loadDefault();
                }
            }
        }
        return clone(defaultConfig);
    }

    public String defaultConfigJson() {
        try {
            return objectMapper.writeValueAsString(defaultConfig());
        } catch (Exception e) {
            throw new IllegalStateException("默认主题序列化失败", e);
        }
    }

    /**
     * 所有预设主题。
     */
    public List<PresetVO> presets() {
        List<PresetVO> list = new ArrayList<>();
        list.add(preset("default-blue", "默认蓝", "清新靛蓝，适合技术博客", cfg -> {
        }));
        list.add(preset("dark-night", "暗夜", "深色为主，护眼沉浸", cfg -> {
            ThemeConfig.Color c = cfg.getColor();
            c.setPrimary("#7c9cff");
            c.setPrimaryHover("#6b8af8");
            c.setPrimarySubtle("#1e2438");
            c.setBg("#12141a");
            c.setBgSubtle("#171a21");
            c.setSurface("#1b1e26");
            c.setText("#e6e8ee");
            c.setTextMuted("#9aa1b1");
            c.setTextInvert("#0f1115");
            c.setBorder("#2a2e38");
            c.setLink("#7c9cff");
            cfg.getDark().setEnabled(true);
            cfg.getDark().setDefaultMode("dark");
            cfg.getLayout().setCardStyle("bordered");
        }));
        list.add(preset("mono", "极简黑白", "无彩色，强调排版与留白", cfg -> {
            ThemeConfig.Color c = cfg.getColor();
            c.setPrimary("#111827");
            c.setPrimaryHover("#000000");
            c.setPrimarySubtle("#f3f4f6");
            c.setBg("#ffffff");
            c.setBgSubtle("#fafafa");
            c.setSurface("#ffffff");
            c.setText("#111827");
            c.setTextMuted("#6b7280");
            c.setTextInvert("#ffffff");
            c.setBorder("#e5e7eb");
            c.setLink("#111827");
            cfg.getRadius().setSm(2);
            cfg.getRadius().setMd(4);
            cfg.getRadius().setLg(6);
            cfg.getLayout().setCardStyle("bordered");
            cfg.getLayout().setDensity("comfortable");
            cfg.getDark().getTokens().setBg("#0b0b0c");
            cfg.getDark().getTokens().setSurface("#141416");
            cfg.getDark().getTokens().setText("#ededf0");
            cfg.getDark().getTokens().setBorder("#26262a");
        }));
        list.add(preset("warm-orange", "暖橙", "温暖明快，适合生活随笔", cfg -> {
            ThemeConfig.Color c = cfg.getColor();
            c.setPrimary("#ea580c");
            c.setPrimaryHover("#c2410c");
            c.setPrimarySubtle("#fff7ed");
            c.setLink("#ea580c");
            c.setBg("#fffdf9");
            c.setBgSubtle("#fff7ed");
            c.setSurface("#ffffff");
            c.setText("#292524");
            c.setTextMuted("#78716c");
            c.setBorder("#fde68a");
            cfg.getRadius().setSm(8);
            cfg.getRadius().setMd(14);
            cfg.getRadius().setLg(20);
            cfg.getLayout().setCardStyle("elevated");
            cfg.getDark().getTokens().setBg("#1a1410");
            cfg.getDark().getTokens().setSurface("#241c16");
            cfg.getDark().getTokens().setText("#f5ede3");
            cfg.getDark().getTokens().setBorder("#3a2c22");
        }));
        list.add(preset("forest-green", "森绿", "自然沉静，长时间阅读友好", cfg -> {
            ThemeConfig.Color c = cfg.getColor();
            c.setPrimary("#15803d");
            c.setPrimaryHover("#166534");
            c.setPrimarySubtle("#f0fdf4");
            c.setLink("#15803d");
            c.setBg("#fbfdfb");
            c.setBgSubtle("#f0fdf4");
            c.setSurface("#ffffff");
            c.setText("#1a2e22");
            c.setTextMuted("#5b6b60");
            c.setBorder("#d1fae5");
            cfg.getFont().setLineHeight(1.8);
            cfg.getLayout().setCardStyle("flat");
            cfg.getLayout().setDensity("spacious");
            cfg.getDark().getTokens().setBg("#101714");
            cfg.getDark().getTokens().setSurface("#182019");
            cfg.getDark().getTokens().setText("#e3efe6");
            cfg.getDark().getTokens().setBorder("#26332a");
        }));
        return list;
    }

    private PresetVO preset(String key, String name, String description,
                            java.util.function.Consumer<ThemeConfig> customizer) {
        ThemeConfig config = defaultConfig();
        if (config.getMeta() == null) {
            config.setMeta(new ThemeConfig.Meta());
        }
        config.getMeta().setName(name);
        customizer.accept(config);
        return new PresetVO(key, name, description, config);
    }

    private ThemeConfig loadDefault() {
        try (InputStream in = new ClassPathResource(DEFAULT_THEME_JSON).getInputStream()) {
            String json = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
            return objectMapper.readValue(json, ThemeConfig.class);
        } catch (Exception e) {
            log.warn("读取默认主题配置失败，使用内置兜底配置", e);
            return new ThemeConfig();
        }
    }

    public ThemeConfig clone(ThemeConfig source) {
        try {
            return objectMapper.readValue(objectMapper.writeValueAsString(source), ThemeConfig.class);
        } catch (Exception e) {
            throw new IllegalStateException("主题配置复制失败", e);
        }
    }

    /**
     * 预设主题视图对象。
     */
    @Data
    public static class PresetVO {
        private final String key;
        private final String name;
        private final String description;
        private final ThemeConfig config;

        public PresetVO(String key, String name, String description, ThemeConfig config) {
            this.key = key;
            this.name = name;
            this.description = description;
            this.config = config;
        }
    }
}
