package com.blog.module.article.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 归档分组（按年月）。
 */
@Data
public class ArchiveGroupVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** yyyy-MM */
    private String month;

    private Integer year;

    private long count;

    private List<ArchiveItemVO> items = new ArrayList<>();
}
