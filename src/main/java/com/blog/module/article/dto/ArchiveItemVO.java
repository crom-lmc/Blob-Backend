package com.blog.module.article.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 归档条目。
 */
@Data
public class ArchiveItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String slug;

    private String title;

    /** yyyy-MM-dd */
    private String date;

    private LocalDateTime publishedAt;
}
