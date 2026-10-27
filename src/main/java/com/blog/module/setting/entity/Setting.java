package com.blog.module.setting.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 站点设置（t_setting，KV 形式）。
 */
@Data
@TableName("t_setting")
public class Setting implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String settingKey;

    /** JSON 字符串或纯文本（LONGTEXT，兼容 MySQL 5.7） */
    private String settingValue;

    private String remark;
}
