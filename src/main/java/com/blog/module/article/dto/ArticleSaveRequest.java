package com.blog.module.article.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 文章保存请求（新增 / 编辑）。
 */
@Data
public class ArticleSaveRequest {

    /** 为空表示新增 */
    private Long id;

    @NotBlank(message = "标题不能为空")
    private String title;

    /** 为空时由标题自动生成 */
    private String slug;

    private String summary;

    private String cover;

    private String contentMd;

    /** draft / published / private */
    private String status = "draft";

    private Long categoryId;

    /** article / page / note */
    private String type = "article";

    private Integer isTop = 0;

    private Integer allowComment = 1;

    private LocalDateTime publishedAt;

    /** 标签 ID 列表 */
    private List<Long> tagIds = new ArrayList<>();

    /** 标签名称列表（不存在时自动创建） */
    private List<String> tagNames = new ArrayList<>();
}
