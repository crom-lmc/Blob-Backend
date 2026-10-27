package com.blog.common.ratelimit;

import com.blog.common.redis.RedisService;
import com.blog.config.BlogProperties;
import com.blog.util.IpUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 Redis 的固定窗口限流器（IP 维度）。
 * Redis 不可用时降级为本地内存计数，保证限流不失效。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimiter {

    private final RedisService redisService;
    private final BlogProperties blogProperties;

    /** 本地降级计数器：key -> [窗口开始时间, 计数] */
    private final Map<String, long[]> localCounter = new ConcurrentHashMap<>();

    public boolean tryAcquire(String bizKey, String ip, int limit, Duration window) {
        String key = "rl:" + bizKey + ":" + ip + ":" + (System.currentTimeMillis() / window.toMillis());
        if (redisService.available()) {
            Long count = redisService.increment(key, window);
            return count == null || count <= limit;
        }
        return localAcquire(key, limit, window.toMillis());
    }

    /**
     * 公开接口默认限流。
     */
    public boolean tryAcquirePublic(String ip) {
        if (!blogProperties.getRateLimit().isEnabled()) {
            return true;
        }
        return tryAcquire("public", ip, blogProperties.getRateLimit().getPublicQpm(), Duration.ofMinutes(1));
    }

    public boolean tryAcquireComment(String ip) {
        if (!blogProperties.getRateLimit().isEnabled()) {
            return true;
        }
        return tryAcquire("comment", ip, blogProperties.getRateLimit().getCommentQpm(), Duration.ofMinutes(1));
    }

    public boolean tryAcquireLogin(String ip) {
        if (!blogProperties.getRateLimit().isEnabled()) {
            return true;
        }
        return tryAcquire("login", ip, blogProperties.getRateLimit().getLoginQpm(), Duration.ofMinutes(1));
    }

    public boolean tryAcquireLogin(String username, String ip) {
        if (!blogProperties.getRateLimit().isEnabled()) {
            return true;
        }
        return tryAcquire("login:" + username, ip, blogProperties.getRateLimit().getLoginQpm(), Duration.ofMinutes(1));
    }

    public static String currentIp() {
        return IpUtils.getIp();
    }

    private boolean localAcquire(String key, int limit, long windowMillis) {
        long now = System.currentTimeMillis();
        long[] counter = localCounter.compute(key, (k, old) -> {
            if (old == null || now - old[0] > windowMillis) {
                return new long[]{now, 1L};
            }
            old[1] = old[1] + 1;
            return old;
        });
        return counter[1] <= limit;
    }
}
