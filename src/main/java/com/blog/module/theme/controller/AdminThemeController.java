package com.blog.module.theme.controller;

import com.blog.common.R;
import com.blog.common.log.OpLog;
import com.blog.module.theme.ThemePresets;
import com.blog.module.theme.dto.ThemeActiveVO;
import com.blog.module.theme.dto.ThemeConfig;
import com.blog.module.theme.entity.Theme;
import com.blog.module.theme.service.ThemeService;
import com.blog.module.theme.util.ThemeCssRenderer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台主题管理（仅管理员）。
 */
@RestController
@RequestMapping("/api/admin/themes")
@RequiredArgsConstructor
@Tag(name = "后台-主题", description = "主题 CRUD、配置保存、启用发布、预览与预设")
public class AdminThemeController {

    private final ThemeService themeService;

    @GetMapping
    @Operation(summary = "主题列表")
    public R<List<ThemeVO>> list() {
        List<ThemeVO> list = themeService.list().stream().map(t -> {
            ThemeVO vo = new ThemeVO();
            vo.setId(t.getId());
            vo.setName(t.getName());
            vo.setDescription(t.getDescription());
            vo.setIsActive(t.getIsActive());
            vo.setIsBuiltin(t.getIsBuiltin());
            vo.setVersion(t.getVersion());
            vo.setUpdatedAt(t.getUpdatedAt());
            return vo;
        }).toList();
        return R.ok(list);
    }

    @GetMapping("/{id}")
    @Operation(summary = "主题详情（含完整配置）")
    public R<ThemeDetailVO> detail(@PathVariable Long id) {
        Theme theme = themeService.getById(id);
        ThemeDetailVO vo = new ThemeDetailVO();
        vo.setId(theme.getId());
        vo.setName(theme.getName());
        vo.setDescription(theme.getDescription());
        vo.setIsActive(theme.getIsActive());
        vo.setIsBuiltin(theme.getIsBuiltin());
        vo.setVersion(theme.getVersion());
        ThemeConfig config = themeService.parseConfig(theme.getConfigJson());
        vo.setConfig(config);
        vo.setCss(ThemeCssRenderer.render(config));
        return R.ok(vo);
    }

    @PostMapping
    @OpLog(module = "theme", action = "create")
    @Operation(summary = "新建主题", description = "copyFromId 为空时使用内置默认配置")
    public R<Map<String, Object>> create(@RequestBody CreateThemeRequest request) {
        Long id = themeService.create(request.getName(), request.getDescription(), request.getCopyFromId());
        return R.ok(Map.of("id", id));
    }

    @PutMapping("/{id}")
    @OpLog(module = "theme", action = "update")
    @Operation(summary = "修改主题名称与描述")
    public R<Void> update(@PathVariable Long id, @RequestBody UpdateThemeRequest request) {
        themeService.update(id, request.getName(), request.getDescription());
        return R.ok();
    }

    @PostMapping("/{id}/copy")
    @OpLog(module = "theme", action = "copy")
    @Operation(summary = "复制主题")
    public R<Map<String, Object>> copy(@PathVariable Long id) {
        return R.ok(Map.of("id", themeService.copy(id)));
    }

    @DeleteMapping("/{id}")
    @OpLog(module = "theme", action = "delete")
    @Operation(summary = "删除主题", description = "正在使用的主题不允许删除")
    public R<Void> delete(@PathVariable Long id) {
        themeService.delete(id);
        return R.ok();
    }

    @PutMapping("/{id}/config")
    @OpLog(module = "theme", action = "save-config")
    @Operation(summary = "保存主题配置（草稿态）", description = "只落库不刷新前台缓存，需发布后才生效")
    public R<Void> saveConfig(@PathVariable Long id, @RequestBody ThemeConfig config) {
        themeService.saveConfig(id, config);
        return R.ok();
    }

    @PostMapping("/{id}/activate")
    @OpLog(module = "theme", action = "activate")
    @Operation(summary = "启用主题", description = "刷新 Redis 缓存，version +1")
    public R<ThemeActiveVO> activate(@PathVariable Long id) {
        return R.ok(themeService.activate(id));
    }

    @PostMapping("/{id}/publish")
    @OpLog(module = "theme", action = "publish")
    @Operation(summary = "发布上线", description = "保存配置并启用，覆盖线上样式，version +1")
    public R<ThemeActiveVO> publish(@PathVariable Long id, @RequestBody(required = false) ThemeConfig config) {
        // 未传配置时直接启用当前已保存的配置
        if (config == null) {
            return R.ok(themeService.activate(id));
        }
        return R.ok(themeService.publish(id, config));
    }

    @PostMapping("/{id}/preview")
    @Operation(summary = "生成预览令牌", description = "15 分钟内有效，供可视化编辑器 iframe 加载真实前台页面")
    public R<Map<String, Object>> preview(@PathVariable Long id, @RequestBody(required = false) ThemeConfig config) {
        String token = themeService.createPreviewToken(id, config);
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("url", "/preview?token=" + token);
        data.put("expiresIn", 900);
        return R.ok(data);
    }

    @GetMapping("/presets")
    @Operation(summary = "内置预设主题", description = "默认蓝、暗夜、极简黑白、暖橙、森绿")
    public R<List<ThemePresets.PresetVO>> presets() {
        return R.ok(themeService.presets());
    }

    @GetMapping("/default")
    @Operation(summary = "内置默认主题配置", description = "编辑器「重置为默认」使用")
    public R<ThemeConfig> defaultConfig() {
        return R.ok(themeService.defaultConfig());
    }

    @GetMapping("/{id}/export")
    @Operation(summary = "导出主题配置 JSON", description = "用于备份与迁移到其它站点")
    public R<ThemeConfig> export(@PathVariable Long id) {
        return R.ok(themeService.parseConfig(themeService.getById(id).getConfigJson()));
    }

    @PostMapping("/import")
    @OpLog(module = "theme", action = "import")
    @Operation(summary = "导入主题配置 JSON", description = "创建一个新主题承载导入的配置")
    public R<Map<String, Object>> importConfig(@RequestBody ImportThemeRequest request) {
        Long id = themeService.create(request.getName(), request.getDescription(), null);
        themeService.saveConfig(id, request.getConfig());
        return R.ok(Map.of("id", id));
    }

    // ---------------- DTO ----------------

    @Data
    public static class ThemeVO {
        private Long id;
        private String name;
        private String description;
        private Integer isActive;
        private Integer isBuiltin;
        private Integer version;
        private java.time.LocalDateTime updatedAt;
    }

    @Data
    public static class ThemeDetailVO {
        private Long id;
        private String name;
        private String description;
        private Integer isActive;
        private Integer isBuiltin;
        private Integer version;
        private ThemeConfig config;
        private String css;
    }

    @Data
    public static class CreateThemeRequest {
        private String name;
        private String description;
        private Long copyFromId;
    }

    @Data
    public static class UpdateThemeRequest {
        private String name;
        private String description;
    }

    @Data
    public static class ImportThemeRequest {
        private String name;
        private String description;
        private ThemeConfig config;
    }
}
