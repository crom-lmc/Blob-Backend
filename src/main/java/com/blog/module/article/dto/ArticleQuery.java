package com.blog.module.article.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章查询条件（前台与后台共用）。
 */
@Data
public class ArticleQuery {

    private long page = 1;

    private long size = 10;

    /** 关键词：标题 / 摘要 / 正文 */
    private String keyword;

    private Long categoryId;

    /** 分类 ID 集合（含子分类），按父分类聚合查询时使用 */
    private List<Long> categoryIds;

    private Long tagId;

    /** latest / hot / views / oldest / created */
    private String sort = "latest";

    /** draft / published / private */
    private String status;

    /** article / page / note */
    private String type;

    private Boolean isTop;

    private LocalDateTime start;

    private LocalDateTime end;

    /** 排序 SQL（由 sort 白名单转换，禁止外部直接传入） */
    private String orderBy = "a.is_top DESC, a.published_at DESC, a.id DESC";

    public long offset() {
        long current = Math.max(1, page);
        long pageSize = Math.min(Math.max(1, size), 100);
        return (current - 1) * pageSize;
    }

    public long limit() {
        return Math.min(Math.max(1, size), 100);
    }

    /**
     * 根据 sort 参数解析出安全的排序 SQL（白名单，避免注入）。
     */
    public void applySort() {
        if (sort == null || sort.isBlank()) {
            this.orderBy = "a.is_top DESC, a.published_at DESC, a.id DESC";
            return;
        }
        this.orderBy = switch (sort.trim().toLowerCase()) {
            case "hot" -> "a.comment_count DESC, a.view_count DESC, a.id DESC";
            case "views" -> "a.view_count DESC, a.id DESC";
            case "oldest" -> "a.published_at ASC, a.id ASC";
            case "created" -> "a.created_at DESC, a.id DESC";
            case "title" -> "a.title ASC, a.id DESC";
            default -> "a.is_top DESC, a.published_at DESC, a.id DESC";
        };
    }
}
