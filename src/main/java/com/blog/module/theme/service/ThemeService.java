package com.blog.module.theme.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.common.BusinessException;
import com.blog.common.ErrorCode;
import com.blog.common.redis.RedisService;
import com.blog.module.theme.ThemePresets;
import com.blog.module.theme.dto.ThemeActiveVO;
import com.blog.module.theme.dto.ThemeConfig;
import com.blog.module.theme.entity.Theme;
import com.blog.module.theme.mapper.ThemeMapper;
import com.blog.module.theme.util.ThemeCssRenderer;
import com.blog.util.CssUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 主题服务：主题 CRUD、配置保存、启用发布、预览令牌、缓存刷新。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThemeService {

    /** 生效主题缓存 key */
    private static final String CACHE_KEY = "theme:active";
    private static final String PREVIEW_PREFIX = "theme:preview:";
    private static final Duration PREVIEW_TTL = Duration.ofMinutes(15);

    private final ThemeMapper themeMapper;
    private final RedisService redisService;
    private final ThemePresets themePresets;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Redis 不可用时的预览令牌兜底（token -> 过期时间） */
    private final Map<String, ThemeActiveVO> localPreview = new ConcurrentHashMap<>();
    private final Map<String, Long> localPreviewExpire = new ConcurrentHashMap<>();

    // ---------------- 查询 ----------------

    public List<Theme> list() {
        return themeMapper.selectList(new LambdaQueryWrapper<Theme>()
                .orderByDesc(Theme::getIsActive)
                .orderByAsc(Theme::getId));
    }

    public Theme getById(Long id) {
        Theme theme = themeMapper.selectById(id);
        if (theme == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "主题不存在");
        }
        return theme;
    }

    /**
     * 获取生效主题视图（带 Redis 缓存）。
     */
    public ThemeActiveVO getActive() {
        ThemeActiveVO cached = redisService.getJson(CACHE_KEY, ThemeActiveVO.class);
        if (cached != null) {
            return cached;
        }
        Theme theme = themeMapper.selectOne(new LambdaQueryWrapper<Theme>()
                .eq(Theme::getIsActive, 1)
                .last("LIMIT 1"));
        if (theme == null) {
            // 兜底：没有启用主题时返回内置默认主题，保证前台始终有样式
            ThemeConfig config = themePresets.defaultConfig();
            ThemeActiveVO fallback = buildVO(null, "默认主题", 1, config, null);
            return fallback;
        }
        ThemeActiveVO vo = buildVO(theme.getId(), theme.getName(), version(theme),
                parseConfig(theme.getConfigJson()), theme.getUpdatedAt());
        redisService.setJson(CACHE_KEY, vo, Duration.ofHours(2));
        return vo;
    }

    /**
     * 只返回 CSS 文本（供 &lt;link rel="stylesheet"&gt; 直接引用）。
     */
    public String getActiveCss() {
        ThemeActiveVO active = getActive();
        return active.getCss() == null ? "" : active.getCss();
    }

    public void refreshCache() {
        redisService.delete(CACHE_KEY);
        log.info("主题缓存已刷新");
    }

    // ---------------- 管理操作 ----------------

    /**
     * 新建主题（基于默认或指定主题复制配置）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(String name, String description, Long copyFromId) {
        Theme theme = new Theme();
        theme.setName(name == null || name.isBlank() ? "新主题" : name.trim());
        theme.setDescription(description);
        theme.setIsActive(0);
        theme.setIsBuiltin(0);
        theme.setVersion(1);
        ThemeConfig config;
        if (copyFromId != null) {
            config = parseConfig(getById(copyFromId).getConfigJson());
        } else {
            config = themePresets.defaultConfig();
        }
        if (config.getMeta() != null) {
            config.getMeta().setName(theme.getName());
        }
        theme.setConfigJson(writeConfig(config));
        themeMapper.insert(theme);
        return theme.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, String name, String description) {
        Theme theme = getById(id);
        if (name != null && !name.isBlank()) {
            theme.setName(name.trim());
        }
        if (description != null) {
            theme.setDescription(description);
        }
        themeMapper.updateById(theme);
        if (isActive(theme)) {
            refreshCache();
        }
    }

    /**
     * 复制主题。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long copy(Long id) {
        Theme source = getById(id);
        Theme target = new Theme();
        target.setName(source.getName() + " - 副本");
        target.setDescription(source.getDescription());
        target.setConfigJson(source.getConfigJson());
        target.setIsActive(0);
        target.setIsBuiltin(0);
        target.setVersion(1);
        themeMapper.insert(target);
        return target.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Theme theme = getById(id);
        // 内置/默认主题强保护：任何情况下不可删除
        if (theme.getIsBuiltin() != null && theme.getIsBuiltin() == 1) {
            throw new BusinessException(ErrorCode.CANNOT_DELETE_DEFAULT_THEME);
        }
        if (isActive(theme)) {
            throw new BusinessException(ErrorCode.CANNOT_DELETE_ACTIVE_THEME);
        }
        themeMapper.deleteById(id);
    }

    /**
     * 保存主题配置（草稿态，不刷新前台缓存，需要发布后才生效）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveConfig(Long id, ThemeConfig config) {
        validate(config);
        Theme theme = getById(id);
        theme.setConfigJson(writeConfig(config));
        themeMapper.updateById(theme);
    }

    /**
     * 启用主题：其它主题置为未启用，version +1，并刷新 Redis 缓存。
     */
    @Transactional(rollbackFor = Exception.class)
    public ThemeActiveVO activate(Long id) {
        Theme theme = getById(id);
        themeMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Theme>()
                .set(Theme::getIsActive, 0)
                .ne(Theme::getId, id));
        theme.setIsActive(1);
        theme.setVersion(version(theme) + 1);
        themeMapper.updateById(theme);
        ThemeActiveVO vo = buildVO(theme.getId(), theme.getName(), theme.getVersion(),
                parseConfig(theme.getConfigJson()), theme.getUpdatedAt());
        redisService.setJson(CACHE_KEY, vo, Duration.ofHours(2));
        return vo;
    }

    /**
     * 发布上线：保存配置 + 启用 + version +1（编辑器「发布上线」按钮）。
     */
    @Transactional(rollbackFor = Exception.class)
    public ThemeActiveVO publish(Long id, ThemeConfig config) {
        validate(config);
        Theme theme = getById(id);
        theme.setConfigJson(writeConfig(config));
        theme.setIsActive(1);
        theme.setVersion(version(theme) + 1);
        themeMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Theme>()
                .set(Theme::getIsActive, 0)
                .ne(Theme::getId, id));
        themeMapper.updateById(theme);
        ThemeActiveVO vo = buildVO(theme.getId(), theme.getName(), theme.getVersion(),
                parseConfig(theme.getConfigJson()), theme.getUpdatedAt());
        redisService.setJson(CACHE_KEY, vo, Duration.ofHours(2));
        return vo;
    }

    /**
     * 生成一次性预览令牌（15 分钟有效），供主题编辑器 iframe 使用。
     */
    public String createPreviewToken(Long id, ThemeConfig config) {
        Theme theme = getById(id);
        ThemeConfig target = config == null ? parseConfig(theme.getConfigJson()) : config;
        validate(target);
        String token = UUID.randomUUID().toString().replace("-", "");
        ThemeActiveVO vo = buildVO(theme.getId(), theme.getName(), version(theme) + 1, target, LocalDateTime.now());
        redisService.setJson(PREVIEW_PREFIX + token, vo, PREVIEW_TTL);
        if (!redisService.available()) {
            localPreview.put(token, vo);
            localPreviewExpire.put(token, System.currentTimeMillis() + PREVIEW_TTL.toMillis());
        }
        return token;
    }

    /**
     * 读取预览令牌对应的主题（过期返回 null）。
     */
    public ThemeActiveVO getPreview(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        ThemeActiveVO vo = redisService.getJson(PREVIEW_PREFIX + token, ThemeActiveVO.class);
        if (vo != null) {
            return vo;
        }
        Long expire = localPreviewExpire.get(token);
        if (expire != null && expire > System.currentTimeMillis()) {
            return localPreview.get(token);
        }
        localPreview.remove(token);
        localPreviewExpire.remove(token);
        return null;
    }

    /**
     * 内置预设主题。
     */
    public List<ThemePresets.PresetVO> presets() {
        return themePresets.presets();
    }

    public ThemeConfig defaultConfig() {
        return themePresets.defaultConfig();
    }

    // ---------------- 工具方法 ----------------

    public ThemeConfig parseConfig(String json) {
        if (json == null || json.isBlank()) {
            return themePresets.defaultConfig();
        }
        try {
            return objectMapper.readValue(json, ThemeConfig.class);
        } catch (Exception e) {
            log.warn("主题配置解析失败，回退默认配置", e);
            return themePresets.defaultConfig();
        }
    }

    public String writeConfig(ThemeConfig config) {
        try {
            return objectMapper.writeValueAsString(config);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.THEME_CONFIG_INVALID, "主题配置序列化失败");
        }
    }

    /**
     * 保存前校验：颜色格式、自定义 CSS 安全。
     */
    public void validate(ThemeConfig config) {
        if (config == null) {
            throw new BusinessException(ErrorCode.THEME_CONFIG_INVALID, "主题配置不能为空");
        }
        CssUtils.checkCustomCss(config.getCustomCss());
        CssUtils.checkCustomHtml(config.getCustomHeadHtml());
        ThemeConfig.Color color = config.getColor();
        if (color != null) {
            checkColor("color.primary", color.getPrimary());
            checkColor("color.bg", color.getBg());
            checkColor("color.text", color.getText());
        }
        if (config.getDark() != null && config.getDark().getTokens() != null) {
            checkColor("dark.tokens.bg", config.getDark().getTokens().getBg());
            checkColor("dark.tokens.text", config.getDark().getTokens().getText());
        }
    }

    private void checkColor(String field, String value) {
        if (value != null && !value.isBlank() && !CssUtils.isHexColor(value)) {
            throw new BusinessException(ErrorCode.THEME_CONFIG_INVALID, field + " 不是合法的颜色值：" + value);
        }
    }

    private ThemeActiveVO buildVO(Long id, String name, int version, ThemeConfig config, LocalDateTime updatedAt) {
        ThemeActiveVO vo = new ThemeActiveVO();
        vo.setId(id);
        vo.setName(name);
        vo.setVersion(version);
        vo.setTokens(config);
        vo.setCss(ThemeCssRenderer.render(config));
        vo.setLayout(config.getLayout());
        vo.setCustomCss(config.getCustomCss());
        vo.setCustomHeadHtml(config.getCustomHeadHtml());
        vo.setDarkEnabled(config.getDark() != null && config.getDark().isEnabled());
        vo.setDefaultMode(config.getDark() == null ? "system" : config.getDark().getDefaultMode());
        vo.setUpdatedAt(updatedAt == null ? LocalDateTime.now() : updatedAt);
        return vo;
    }

    private int version(Theme theme) {
        return theme.getVersion() == null ? 1 : theme.getVersion();
    }

    private boolean isActive(Theme theme) {
        return theme.getIsActive() != null && theme.getIsActive() == 1;
    }
}
