package com.blog.module.comment.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 前台评论提交请求。
 */
@Data
public class CommentSubmitRequest {

    /** 文章 ID 或 slug */
    @NotBlank(message = "文章不能为空")
    private String articleIdOrSlug;

    /** 回复的评论 ID */
    private Long parentId;

    @NotBlank(message = "昵称不能为空")
    @Size(max = 64, message = "昵称过长")
    private String authorName;

    @Email(message = "邮箱格式不正确")
    private String authorEmail;

    private String authorSite;

    @NotBlank(message = "评论内容不能为空")
    @Size(max = 2000, message = "评论内容过长")
    private String content;
}
