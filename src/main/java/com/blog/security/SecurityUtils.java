package com.blog.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * 当前登录用户工具类。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<SecurityUser> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof SecurityUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    /**
     * 获取当前用户 ID，未登录返回 null。
     */
    public static Long currentUserId() {
        return currentUser().map(SecurityUser::getUserId).orElse(null);
    }

    /**
     * 获取当前用户名，未登录返回 null。
     */
    public static String currentUsername() {
        return currentUser().map(SecurityUser::getUsername).orElse(null);
    }

    public static boolean isAdmin() {
        return currentUser().map(SecurityUser::isAdmin).orElse(false);
    }
}
