package com.blog.security;

import com.blog.common.ErrorCode;
import com.blog.common.R;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 已登录但权限不足时返回统一 JSON。
 */
@Slf4j
@Component
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        try {
            MAPPER.writeValue(response.getWriter(), R.fail(ErrorCode.FORBIDDEN));
        } catch (Exception e) {
            log.warn("写入拒绝访问响应失败", e);
        }
    }
}
