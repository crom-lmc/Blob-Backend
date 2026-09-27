package com.blog.module.article.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 文章列表视图对象。
 */
@Data
public class ArticleListVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String title;
    private String slug;
    private String summary;
    private String cover;
    private String type;
    private String status;

    private Long categoryId;
    private String categoryName;
    private String categorySlug;

    private List<TagVO> tags = new ArrayList<>();

    private Integer isTop;
    private Integer allowComment;
    private Integer viewCount;
    private Integer likeCount;
    private Integer commentCount;
    private Integer wordCount;
    private Integer readingTime;

    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 搜索结果命中的摘要片段（纯文本，前端按关键词高亮） */
    private String highlight;
}
