package com.blog.module.setting.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 前台站点设置聚合结果。
 */
@Data
public class SiteSettingsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 原始 KV 配置 */
    private Map<String, String> settings;

    /** 友情链接 */
    private List<LinkItem> friendLinks;

    /** 社交链接 */
    private List<LinkItem> socialLinks;

    /** 评论是否需要审核 */
    private boolean commentReviewOn;

    /** 前台默认每页条数 */
    private int pageSize;
}
