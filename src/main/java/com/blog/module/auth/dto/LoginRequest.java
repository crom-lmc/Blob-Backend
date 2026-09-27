package com.blog.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求。
 */
@Data
public class LoginRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    /** 验证码 key（开启验证码时必填） */
    private String captchaKey;

    /** 验证码内容 */
    private String captchaCode;

    /** 记住我：延长令牌有效期 */
    private Boolean rememberMe;
}
