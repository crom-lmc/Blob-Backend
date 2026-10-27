package com.blog.module.auth.controller;

import com.blog.common.R;
import com.blog.module.auth.dto.LoginRequest;
import com.blog.module.auth.dto.LoginResponse;
import com.blog.module.auth.service.AuthService;
import com.blog.module.auth.service.CaptchaService;
import com.blog.module.user.dto.UserVO;
import com.blog.security.SecurityUtils;
import com.blog.security.SecurityUser;
import com.blog.util.IpUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 后台认证接口。
 */
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
@Tag(name = "后台-认证", description = "登录、登出、验证码、当前用户")
public class AuthController {

    private final AuthService authService;
    private final CaptchaService captchaService;

    @GetMapping("/captcha")
    @Operation(summary = "获取登录图形验证码")
    public R<CaptchaService.CaptchaVO> captcha() {
        return R.ok(captchaService.generate());
    }

    @PostMapping("/login")
    @Operation(summary = "登录", description = "账号密码 + 图形验证码；连续失败 5 次锁定 10 分钟")
    public R<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return R.ok(authService.login(request, IpUtils.getIp(httpRequest)));
    }

    @PostMapping("/logout")
    @Operation(summary = "登出")
    public R<Void> logout() {
        authService.logout(SecurityUtils.currentUsername());
        return R.ok();
    }

    @GetMapping("/profile")
    @Operation(summary = "当前登录用户信息")
    public R<UserVO> profile() {
        SecurityUser user = SecurityUtils.currentUser()
                .orElseThrow(() -> new com.blog.common.BusinessException(
                        com.blog.common.ErrorCode.UNAUTHORIZED));
        return R.ok(authService.profile(user.getUserId()));
    }
}
