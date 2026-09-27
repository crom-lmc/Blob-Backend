package com.blog.module.article.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 分类文章数统计。
 */
@Data
public class CategoryCountDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long categoryId;

    private Long total;
}
