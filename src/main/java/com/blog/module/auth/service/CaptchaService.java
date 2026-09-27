package com.blog.module.auth.service;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import com.blog.common.BusinessException;
import com.blog.common.ErrorCode;
import com.blog.common.redis.RedisService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录图形验证码：优先存 Redis，Redis 不可用时降级到本地内存。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaptchaService {

    private static final String PREFIX = "captcha:";
    private static final Duration TTL = Duration.ofMinutes(5);

    private final RedisService redisService;

    private final Map<String, String> localStore = new ConcurrentHashMap<>();
    private final Map<String, Long> localExpire = new ConcurrentHashMap<>();

    /**
     * 生成验证码，返回 key 与 base64 图片（data URL 形式）。
     */
    public CaptchaVO generate() {
        LineCaptcha captcha = CaptchaUtil.createLineCaptcha(120, 40, 4, 30);
        String key = UUID.randomUUID().toString().replace("-", "");
        String code = captcha.getCode();
        redisService.set(PREFIX + key, code, TTL);
        if (!redisService.available()) {
            localStore.put(key, code);
            localExpire.put(key, System.currentTimeMillis() + TTL.toMillis());
        }
        CaptchaVO vo = new CaptchaVO();
        vo.setCaptchaKey(key);
        vo.setCaptchaImage(captcha.getImageBase64Data());
        vo.setExpiresIn((int) TTL.getSeconds());
        return vo;
    }

    /**
     * 校验并消费验证码（一次性）。
     */
    public boolean verify(String key, String code) {
        if (key == null || code == null) {
            return false;
        }
        String actual;
        if (redisService.available()) {
            actual = redisService.get(PREFIX + key);
            redisService.delete(PREFIX + key);
        } else {
            Long expire = localExpire.get(key);
            actual = localStore.remove(key);
            localExpire.remove(key);
            if (expire == null || expire < System.currentTimeMillis()) {
                actual = null;
            }
        }
        return actual != null && actual.equalsIgnoreCase(code.trim());
    }

    public void require(String key, String code) {
        if (!verify(key, code)) {
            throw new BusinessException(ErrorCode.CAPTCHA_ERROR);
        }
    }

    @Data
    public static class CaptchaVO {
        private String captchaKey;
        private String captchaImage;
        private int expiresIn;
    }
}
