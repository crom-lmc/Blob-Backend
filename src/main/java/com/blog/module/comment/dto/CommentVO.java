package com.blog.module.comment.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 评论视图对象（含一层回复）。
 */
@Data
public class CommentVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long articleId;
    private Long parentId;
    private String authorName;
    private String authorEmail;
    private String authorSite;
    private String authorAvatar;
    private String content;
    private String status;
    private Integer isAdmin;
    private String ip;
    private LocalDateTime createdAt;

    /** 文章标题（后台列表展示） */
    private String articleTitle;
    private String articleSlug;

    /** 回复列表（仅一层） */
    private List<CommentVO> replies = new ArrayList<>();
}
