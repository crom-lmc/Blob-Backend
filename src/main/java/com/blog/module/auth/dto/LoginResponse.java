package com.blog.module.auth.dto;

import com.blog.module.user.dto.UserVO;
import lombok.Data;

/**
 * 登录响应。
 */
@Data
public class LoginResponse {

    private String token;

    private String tokenType = "Bearer";

    /** 有效期（秒） */
    private long expiresIn;

    private UserVO user;
}
