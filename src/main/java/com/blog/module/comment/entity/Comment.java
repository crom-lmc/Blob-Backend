package com.blog.module.comment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 评论（t_comment），支持一层回复。
 */
@Data
@TableName("t_comment")
public class Comment implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long articleId;

    /** 父评论 ID，0 表示顶层 */
    private Long parentId;

    private String authorName;

    private String authorEmail;

    private String authorSite;

    private String authorAvatar;

    private String content;

    /** pending / approved / spam / deleted */
    private String status;

    private String userAgent;

    private String ip;

    private Integer isAdmin;

    private LocalDateTime createdAt;
}
