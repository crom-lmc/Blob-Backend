package com.blog.module.article.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 文章-标签关联查询结果。
 */
@Data
public class TagRelation implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long articleId;
    private Long id;
    private String name;
    private String slug;
    private String color;
}
