package com.blog.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 获取客户端真实 IP（支持反向代理）。
 */
public final class IpUtils {

    private static final String[] HEADERS = {
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_CLIENT_IP",
            "HTTP_X_FORWARDED_FOR"
    };

    private IpUtils() {
    }

    public static String getIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        for (String header : HEADERS) {
            String value = request.getHeader(header);
            if (value != null && !value.isEmpty() && !"unknown".equalsIgnoreCase(value)) {
                // X-Forwarded-For 可能是 "client, proxy1, proxy2"
                return value.split(",")[0].trim();
            }
        }
        String ip = request.getRemoteAddr();
        return ip == null ? "unknown" : ip;
    }

    /**
     * 在非 Web 线程中也能安全调用的重载。
     */
    public static String getIp() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                return getIp(attributes.getRequest());
            }
        } catch (Exception ignored) {
            // 忽略：异步线程中无请求上下文
        }
        return "unknown";
    }

    public static String getUserAgent() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                String ua = attributes.getRequest().getHeader("User-Agent");
                if (ua != null) {
                    return ua.length() > 500 ? ua.substring(0, 500) : ua;
                }
            }
        } catch (Exception ignored) {
            // 忽略
        }
        return "";
    }
}
