package com.blog.module.theme.controller;

import com.blog.common.R;
import com.blog.module.theme.dto.ThemeActiveVO;
import com.blog.module.theme.service.ThemeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 前台主题接口（无需鉴权）。
 */
@RestController
@RequestMapping("/api/public/theme")
@RequiredArgsConstructor
@Tag(name = "前台-主题", description = "生效主题与 CSS 变量下发")
public class PublicThemeController {

    private final ThemeService themeService;

    @GetMapping("/active")
    @Operation(summary = "当前生效主题", description = "返回 { version, tokens, css, layout }，前台据此注入 CSS 变量")
    public R<ThemeActiveVO> active() {
        return R.ok(themeService.getActive());
    }

    @GetMapping(value = "/active.css", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "当前生效主题的 CSS 文本", description = "可直接以 <link rel=stylesheet> 引入")
    public String activeCss() {
        return themeService.getActiveCss();
    }

    @GetMapping("/preview")
    @Operation(summary = "主题预览", description = "根据一次性 token 返回预览主题，供后台可视化编辑器 iframe 使用")
    public R<ThemeActiveVO> preview(@RequestParam String token) {
        ThemeActiveVO vo = themeService.getPreview(token);
        if (vo == null) {
            return R.fail(404, "预览令牌无效或已过期");
        }
        return R.ok(vo);
    }
}
