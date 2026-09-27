package com.blog.module.article.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 文章目录项。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TocItem implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 锚点 id */
    private String id;

    /** 标题层级 2~4 */
    private int level;

    /** 标题文本 */
    private String text;
}
