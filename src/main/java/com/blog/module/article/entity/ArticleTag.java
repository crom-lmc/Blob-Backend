package com.blog.module.article.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 文章标签关联（t_article_tag），复合主键 (article_id, tag_id)。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_article_tag")
public class ArticleTag implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long articleId;

    private Long tagId;
}
