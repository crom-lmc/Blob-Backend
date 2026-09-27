package com.blog.module.setting.controller;

import com.blog.common.R;
import com.blog.module.setting.dto.SiteSettingsVO;
import com.blog.module.setting.service.SettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 前台站点设置接口（无需鉴权）。
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
@Tag(name = "前台-站点设置", description = "站点标题、备案号、SEO、友链等聚合配置")
public class PublicSettingController {

    private final SettingService settingService;

    @GetMapping("/settings")
    @Operation(summary = "站点设置聚合")
    public R<SiteSettingsVO> settings() {
        return R.ok(settingService.aggregate());
    }
}
