package com.blog.module.theme.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 主题（t_theme）。
 */
@Data
@TableName("t_theme")
public class Theme implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    /** 主题配置 JSON 字符串（LONGTEXT，兼容 MySQL 5.7） */
    private String configJson;

    /** 是否启用 */
    private Integer isActive;

    /** 是否内置/默认主题：1=内置（不可删除、不可改名留空） */
    private Integer isBuiltin;

    /** 版本号，每次发布 +1，用于前台缓存失效 */
    private Integer version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
