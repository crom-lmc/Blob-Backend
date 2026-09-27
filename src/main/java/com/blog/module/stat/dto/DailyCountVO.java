package com.blog.module.stat.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 按天统计结果（仪表盘趋势图）。
 */
@Data
public class DailyCountVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** yyyy-MM-dd */
    private String date;

    private long count;
}
