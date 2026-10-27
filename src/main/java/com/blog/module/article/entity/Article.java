package com.blog.module.article.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 文章（t_article）。
 */
@Data
@TableName("t_article")
public class Article implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    /** URL 别名，唯一 */
    private String slug;

    private String summary;

    private String cover;

    private String contentMd;

    /** 后端渲染并落库的 HTML，前台不重复渲染 */
    private String contentHtml;

    /** 正文纯文本（从 content_html 抽取），用于全文检索与高亮 */
    @TableField("search_text")
    private String searchText;

    /** draft / published / private */
    private String status;

    private Long categoryId;

    /** article / page / note */
    private String type;

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
}
