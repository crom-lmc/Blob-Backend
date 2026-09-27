package com.blog.common.ratelimit;

import com.blog.common.ErrorCode;
import com.blog.common.R;
import com.blog.util.IpUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 公开接口限流拦截器（IP 维度）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RateLimiter rateLimiter;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String ip = IpUtils.getIp(request);
        if (!rateLimiter.tryAcquirePublic(ip)) {
            log.warn("触发限流 ip={} uri={}", ip, request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_OK);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            try {
                MAPPER.writeValue(response.getWriter(), R.fail(ErrorCode.TOO_MANY_REQUESTS));
            } catch (Exception ignored) {
                // 忽略写失败
            }
            return false;
        }
        return true;
    }
}
