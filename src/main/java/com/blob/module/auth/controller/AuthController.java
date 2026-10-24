package com.blob.module.auth.controller;

import com.blob.common.R;
import com.blob.module.auth.dto.LoginRequest;
import com.blob.module.auth.dto.LoginResponse;
import com.blob.module.auth.service.AuthService;
import com.blob.module.auth.service.CaptchaService;
import com.blob.module.user.dto.UserVO;
import com.blob.security.SecurityUtils;
import com.blob.security.SecurityUser;
import com.blob.util.IpUtils;
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
                .orElseThrow(() -> new com.blob.common.BusinessException(
                        com.blob.common.ErrorCode.UNAUTHORIZED));
        return R.ok(authService.profile(user.getUserId()));
    }
}
