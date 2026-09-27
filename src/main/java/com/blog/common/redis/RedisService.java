package com.blog.common.redis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis 访问门面。
 * 说明：Redis 仅用于缓存/计数/限流等可降级场景，一旦不可用则记录一次警告并降级为空操作，
 * 避免单点故障导致整个站点不可用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisService {


    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 缓存对象可能包含 LocalDateTime（如主题 VO、文章列表），
     * 必须注册 JavaTimeModule 并以 ISO-8601 字符串输出，否则 Jackson 默认无法序列化 Java 8 时间类型。
     */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static volatile boolean unavailableLogged = false;

    /** 自增并设置过期时间的 Lua 脚本，保证原子性 */
    private static final String INCR_WITH_TTL =
            "local c = redis.call('incr', KEYS[1]); " +
            "if c == 1 then redis.call('expire', KEYS[1], ARGV[1]) end; " +
            "return c";

    public boolean available() {
        try {
            stringRedisTemplate.hasKey("blog:ping");
            return true;
        } catch (Exception e) {
            markUnavailable(e);
            return false;
        }
    }

    public void set(String key, String value) {
        execute(() -> stringRedisTemplate.opsForValue().set(key, value));
    }

    public void set(String key, String value, Duration ttl) {
        execute(() -> stringRedisTemplate.opsForValue().set(key, value, ttl));
    }

    public boolean setIfAbsent(String key, String value, Duration ttl) {
        Boolean r = executeWithResult(() -> stringRedisTemplate.opsForValue()
                .setIfAbsent(key, value, ttl));
        return Boolean.TRUE.equals(r);
    }

    public String get(String key) {
        return executeWithResult(() -> stringRedisTemplate.opsForValue().get(key));
    }

    public Long increment(String key) {
        return executeWithResult(() -> stringRedisTemplate.opsForValue().increment(key));
    }

    /**
     * 自增并设置过期时间（首次自增时设置）。
     */
    public Long increment(String key, Duration ttl) {
        return executeWithResult(() -> stringRedisTemplate.execute(
                new DefaultRedisScript<>(INCR_WITH_TTL, Long.class),
                Collections.singletonList(key),
                String.valueOf(ttl.getSeconds())));
    }

    public Long incrementBy(String key, long delta) {
        return executeWithResult(() -> stringRedisTemplate.opsForValue().increment(key, delta));
    }

    public void delete(String... keys) {
        if (keys == null || keys.length == 0) {
            return;
        }
        execute(() -> stringRedisTemplate.delete(java.util.Arrays.asList(keys)));
    }

    public void delete(Set<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return;
        }
        execute(() -> stringRedisTemplate.delete(keys));
    }

    public void expire(String key, Duration ttl) {
        execute(() -> stringRedisTemplate.expire(key, ttl));
    }

    public Boolean hasKey(String key) {
        return executeWithResult(() -> stringRedisTemplate.hasKey(key));
    }

    /**
     * 使用 SCAN 扫描匹配 key，避免 KEYS 命令阻塞。
     */
    public Set<String> scan(String pattern) {
        Set<String> result = new HashSet<>();
        Set<String> keys = executeWithResult(() -> {
            Set<String> set = new HashSet<>();
            stringRedisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Set<String>>) connection -> {
                try (var cursor = connection.keyCommands()
                        .scan(org.springframework.data.redis.core.ScanOptions.scanOptions()
                                .match(pattern).count(500).build())) {
                    while (cursor.hasNext()) {
                        set.add(new String(cursor.next(), StandardCharsets.UTF_8));
                    }
                }
                return set;
            });
            return set;
        });
        if (keys != null) {
            result.addAll(keys);
        }
        return result;
    }

    /**
     * 删除匹配 pattern 的所有 key。
     */
    public void deleteByPattern(String pattern) {
        Set<String> keys = scan(pattern);
        if (!keys.isEmpty()) {
            delete(keys);
        }
    }

    public void setJson(String key, Object value, Duration ttl) {
        try {
            String json = MAPPER.writeValueAsString(value);
            set(key, json, ttl);
        } catch (Exception e) {
            log.warn("序列化写入 Redis 失败 key={}", key, e);
        }
    }

    public <T> T getJson(String key, Class<T> type) {
        String json = get(key);
        if (json == null) {
            return null;
        }
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            log.warn("反序列化 Redis 数据失败 key={}", key, e);
            return null;
        }
    }

    public <T> T getJson(String key, TypeReference<T> typeRef) {
        String json = get(key);
        if (json == null) {
            return null;
        }
        try {
            return MAPPER.readValue(json, typeRef);
        } catch (Exception e) {
            log.warn("反序列化 Redis 数据失败 key={}", key, e);
            return null;
        }
    }

    public List<String> multiGet(Set<String> keys) {
        List<String> values = executeWithResult(() -> stringRedisTemplate.opsForValue().multiGet(keys));
        return values == null ? new ArrayList<>() : values;
    }

    public void hashSet(String key, String field, String value) {
        execute(() -> stringRedisTemplate.opsForHash().put(key, field, value));
    }

    public Map<Object, Object> hashGetAll(String key) {
        return executeWithResult(() -> stringRedisTemplate.opsForHash().entries(key));
    }

    private void execute(Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            markUnavailable(e);
        }
    }

    private <T> T executeWithResult(java.util.function.Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            markUnavailable(e);
            return null;
        }
    }

    private void markUnavailable(Exception e) {
        if (!unavailableLogged) {
            unavailableLogged = true;
            log.warn("Redis 不可用，已降级为本地处理：{}", e.getMessage());
        }
    }
}
