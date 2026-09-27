package com.blog.module.article.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 标签 + 文章数（标签云加权用）。
 */
@Data
public class TagCountDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String slug;
    private String color;
    private Long articleCount;
}
