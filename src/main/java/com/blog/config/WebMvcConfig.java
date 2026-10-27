package com.blog.config;

import com.blog.common.ratelimit.RateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * Web MVC 配置：CORS、静态资源（上传目录）与限流拦截器。
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final BlogProperties blogProperties;
    private final RateLimitInterceptor rateLimitInterceptor;

    @Value("${blog.upload.root:./uploads}")
    private String uploadRoot;

    @Value("${blog.upload.url-prefix:/uploads}")
    private String urlPrefix;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Authorization")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * 本地磁盘上传目录映射为静态资源。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = "file:" + Paths.get(uploadRoot).toAbsolutePath().normalize() + "/";
        registry.addResourceHandler(urlPrefix.endsWith("/") ? urlPrefix + "**" : urlPrefix + "/**")
                .addResourceLocations(location)
                .setCachePeriod(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/public/**")
                .excludePathPatterns("/api/public/theme/**");
    }
}
