package com.blog.module.auth.service;

import com.blog.common.BusinessException;
import com.blog.common.ErrorCode;
import com.blog.common.ratelimit.RateLimiter;
import com.blog.common.redis.RedisService;
import com.blog.config.BlogProperties;
import com.blog.module.auth.dto.LoginRequest;
import com.blog.module.auth.dto.LoginResponse;
import com.blog.module.user.dto.UserVO;
import com.blog.module.user.entity.User;
import com.blog.module.user.service.UserService;
import com.blog.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/**
 * 认证服务：登录、登出、当前用户信息。
 * 安全策略：IP 维度限流 + 连续失败锁定 + 图形验证码 + 账号停用校验。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String FAIL_PREFIX = "login:fail:";

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final RedisService redisService;
    private final RateLimiter rateLimiter;
    private final BlogProperties properties;
    private final CaptchaService captchaService;

    /**
     * 登录。
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse login(LoginRequest request, String ip) {
        // 1. IP 限流
        if (!rateLimiter.tryAcquireLogin(ip)) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "登录尝试过于频繁，请稍后再试");
        }
        String username = request.getUsername().trim();

        // 2. 锁定校验
        Integer failCount = getFailCount(username);
        int maxFail = properties.getSecurity().getMaxLoginFail();
        if (failCount != null && failCount >= maxFail) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }

        // 3. 验证码校验
        if (properties.getSecurity().isCaptchaEnabled()) {
            captchaService.require(request.getCaptchaKey(), request.getCaptchaCode());
        }

        // 4. 用户校验
        User user = userService.getByUsername(username);
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            int current = incrementFail(username);
            int remain = Math.max(0, maxFail - current);
            throw new BusinessException(ErrorCode.PASSWORD_ERROR,
                    remain > 0 ? "用户名或密码错误，还可尝试 " + remain + " 次" : "登录失败次数过多，账号已锁定，请稍后再试");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }

        // 5. 成功：清空失败计数并更新登录时间
        redisService.delete(FAIL_PREFIX + username);
        userService.updateLastLogin(user.getId());

        LoginResponse response = new LoginResponse();
        // 记住我：令牌有效期翻倍（上限 30 天）
        long expiresIn = jwtUtil.getExpireMillis() / 1000;
        if (Boolean.TRUE.equals(request.getRememberMe())) {
            expiresIn = Math.min(expiresIn * 2, 30L * 24 * 3600);
        }
        response.setToken(jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole()));
        response.setExpiresIn(expiresIn);
        response.setTokenType("Bearer");
        response.setUser(UserVO.from(user));
        return response;
    }

    /**
     * 登出（JWT 无状态，服务端只需记录日志；前端删除本地令牌即可）。
     */
    public void logout(String username) {
        log.info("用户退出登录：{}", username);
    }

    public UserVO profile(Long userId) {
        return UserVO.from(userService.getById(userId));
    }

    private Integer getFailCount(String username) {
        String value = redisService.get(FAIL_PREFIX + username);
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int incrementFail(String username) {
        Long count = redisService.increment(FAIL_PREFIX + username,
                Duration.ofMinutes(properties.getSecurity().getLockMinutes()));
        return count == null ? 0 : count.intValue();
    }
}
