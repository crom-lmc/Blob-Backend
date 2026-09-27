package com.blog.module.article.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * 文章详情视图对象。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ArticleDetailVO extends ArticleListVO {

    private static final long serialVersionUID = 1L;

    /** 渲染后的正文 HTML（后端渲染、已过 XSS 过滤） */
    private String contentHtml;

    /** Markdown 原文（后台编辑回显用） */
    private String contentMd;

    /** 文章目录 */
    private List<TocItem> toc = new ArrayList<>();

    /** 上一篇 */
    private ArticleListVO prev;

    /** 下一篇 */
    private ArticleListVO next;

    /** 相关阅读 */
    private List<ArticleListVO> related = new ArrayList<>();
}
