package com.blog.module.article.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 标签保存请求。
 */
@Data
public class TagSaveRequest {

    /** 为空表示新增 */
    private Long id;

    @NotBlank(message = "标签名称不能为空")
    private String name;

    /** 为空时由名称自动生成 */
    private String slug;

    private String color;
}
