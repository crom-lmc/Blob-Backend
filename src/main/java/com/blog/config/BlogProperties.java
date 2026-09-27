package com.blog.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 博客自定义配置项，对应 application.yml 中的 blog.* 。
 */
@Data
@ConfigurationProperties(prefix = "blog")
public class BlogProperties {

    private Jwt jwt = new Jwt();

    private Upload upload = new Upload();

    private Security security = new Security();

    private RateLimit rateLimit = new RateLimit();

    private Comment comment = new Comment();

    private Site site = new Site();

    private Search search = new Search();

    @Data
    public static class Jwt {
        /** HS256 密钥，长度需 >= 32 字节 */
        private String secret = "blog-system-default-secret-please-change-me-1234567890";
        /** 令牌有效期（分钟） */
        private long expireMinutes = 720;
    }

    @Data
    public static class Upload {
        /** 本地磁盘存储根目录 */
        private String root = "./uploads";
        /** 对外访问 URL 前缀 */
        private String urlPrefix = "/uploads";
        /** 单文件大小上限（MB） */
        private long maxSizeMb = 5;
        /** 扩展名白名单 */
        // svg 可能携带脚本，默认不开放
        private List<String> allowedExtensions = new ArrayList<>(List.of(
                "jpg", "jpeg", "png", "gif", "webp", "bmp", "ico", "pdf", "mp4"));
    }

    @Data
    public static class Security {
        /** 登录是否需要图形验证码 */
        private boolean captchaEnabled = true;
        /** 登录失败锁定阈值 */
        private int maxLoginFail = 5;
        /** 锁定时长（分钟） */
        private int lockMinutes = 10;
    }

    @Data
    public static class RateLimit {
        private boolean enabled = true;
        /** 公开接口每 IP 每分钟请求数 */
        private int publicQpm = 300;
        /** 评论提交每 IP 每分钟条数 */
        private int commentQpm = 3;
        /** 登录每 IP 每分钟次数 */
        private int loginQpm = 10;
    }

    @Data
    public static class Comment {
        /** 评论默认是否需要审核 */
        private boolean reviewOn = true;
        /** 敏感词 */
        private List<String> sensitiveWords = new ArrayList<>();
    }

    @Data
    public static class Site {
        /** 站点对外地址，用于 sitemap / RSS */
        private String baseUrl = "http://localhost:8080";
    }

    @Data
    public static class Search {
        /** 检索模式：fulltext（MySQL ngram 全文索引，默认） / like（原 LIKE 逻辑，灰度回退用） */
        private String mode = "fulltext";
    }
}
