package com.blog.module.setting.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.common.redis.RedisService;
import com.blog.module.setting.dto.LinkItem;
import com.blog.module.setting.dto.SiteSettingsVO;
import com.blog.module.setting.entity.Setting;
import com.blog.module.setting.mapper.SettingMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 站点设置服务（KV + Redis 缓存）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettingService {

    private static final String CACHE_KEY = "settings:all";
    private static final Duration CACHE_TTL = Duration.ofHours(2);

    /** 预置 key 列表，缺省值兜底 */
    private static final Map<String, String> DEFAULTS = new HashMap<>();

    static {
        DEFAULTS.put("site_title", "我的博客");
        DEFAULTS.put("site_subtitle", "记录生活与技术");
        DEFAULTS.put("site_logo", "");
        DEFAULTS.put("site_favicon", "");
        DEFAULTS.put("site_footer", "© My Blog");
        DEFAULTS.put("icp_no", "");
        DEFAULTS.put("seo_keywords", "");
        DEFAULTS.put("seo_desc", "");
        DEFAULTS.put("comment_review_on", "true");
        DEFAULTS.put("page_size", "10");
        DEFAULTS.put("friend_links", "[]");
        DEFAULTS.put("social_links", "[]");
    }

    private final SettingMapper settingMapper;
    private final RedisService redisService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 全部设置（带缓存）。
     */
    public Map<String, String> all() {
        Map<String, String> cached = cached();
        if (cached != null) {
            return cached;
        }
        Map<String, String> result = new HashMap<>(DEFAULTS);
        List<Setting> list = settingMapper.selectList(new LambdaQueryWrapper<Setting>());
        for (Setting setting : list) {
            result.put(setting.getSettingKey(), setting.getSettingValue());
        }
        try {
            redisService.setJson(CACHE_KEY, result, CACHE_TTL);
        } catch (Exception e) {
            log.warn("站点设置写入缓存失败", e);
        }
        return result;
    }

    public String get(String key) {
        return all().get(key);
    }

    public String get(String key, String defaultValue) {
        String value = get(key);
        return value == null ? defaultValue : value;
    }

    public boolean getBool(String key, boolean defaultValue) {
        String value = get(key);
        return value == null ? defaultValue : Boolean.parseBoolean(value);
    }

    public int getInt(String key, int defaultValue) {
        String value = get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 批量保存（后端管理「保存站点设置」）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void save(Map<String, String> values) {
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue() == null ? "" : entry.getValue();
            Setting exists = settingMapper.selectOne(new LambdaQueryWrapper<Setting>()
                    .eq(Setting::getSettingKey, key));
            if (exists == null) {
                Setting setting = new Setting();
                setting.setSettingKey(key);
                setting.setSettingValue(value);
                settingMapper.insert(setting);
            } else {
                exists.setSettingValue(value);
                settingMapper.updateById(exists);
            }
        }
        refresh();
    }

    public void refresh() {
        redisService.delete(CACHE_KEY);
    }

    /**
     * 前台聚合结果。
     */
    public SiteSettingsVO aggregate() {
        Map<String, String> all = all();
        SiteSettingsVO vo = new SiteSettingsVO();
        vo.setSettings(all);
        vo.setFriendLinks(parseLinks(all.get("friend_links")));
        vo.setSocialLinks(parseLinks(all.get("social_links")));
        vo.setCommentReviewOn(getBool("comment_review_on", true));
        vo.setPageSize(getInt("page_size", 10));
        return vo;
    }

    private List<LinkItem> parseLinks(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<LinkItem>>() {
            });
        } catch (Exception e) {
            log.warn("链接配置解析失败：{}", json, e);
            return new ArrayList<>();
        }
    }

    private Map<String, String> cached() {
        try {
            return redisService.getJson(CACHE_KEY, new TypeReference<Map<String, String>>() {
            });
        } catch (Exception e) {
            return null;
        }
    }
}
