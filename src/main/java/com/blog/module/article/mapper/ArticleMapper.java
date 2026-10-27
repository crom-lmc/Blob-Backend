package com.blog.module.article.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.module.article.dto.ArchiveGroupVO;
import com.blog.module.article.dto.ArchiveItemVO;
import com.blog.module.article.entity.Article;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文章 Mapper。
 * 复杂列表查询走 XML（mapper/ArticleMapper.xml）：先查 id 再回表，缓解 5.7 深分页性能问题。
 */
@Mapper
public interface ArticleMapper extends BaseMapper<Article> {

    /**
     * 条件统计。
     */
    long countByCondition(@Param("query") com.blog.module.article.dto.ArticleQuery query);

    /**
     * 分页查询（先 id 分页再回表）。
     */
    List<Article> selectPageByCondition(@Param("query") com.blog.module.article.dto.ArticleQuery query,
                                        @Param("offset") long offset,
                                        @Param("size") long size);

    /**
     * 归档分组：按年月统计（MySQL 5.7 使用 DATE_FORMAT，不使用窗口函数）。
     */
    List<ArchiveGroupVO> selectArchiveGroups();

    /**
     * 指定年月的文章条目。
     */
    List<ArchiveItemVO> selectArchiveItems(@Param("month") String month);

    /**
     * 仪表盘：指定时间范围内按天统计文章发布数。
     */
    List<com.blog.module.stat.dto.DailyCountVO> selectDailyArticleCount(@Param("start") LocalDateTime start);

    /**
     * 阅读数批量落库（定时任务）。
     */
    int updateViewCount(@Param("id") Long id, @Param("delta") long delta);

    /**
     * 按分类统计已发布文章数（分类树实时计数）。
     */
    List<com.blog.module.article.dto.CategoryCountDTO> selectPublishedCountByCategory();

    /**
     * 全站阅读量合计。
     */
    @org.apache.ibatis.annotations.Select("SELECT IFNULL(SUM(view_count), 0) FROM t_article")
    long sumViewCount();

    /**
     * 上下篇查询：发布时间早于指定时间的最新一篇。
     */
    Article selectPrev(@Param("publishedAt") LocalDateTime publishedAt);

    /**
     * 上下篇查询：发布时间晚于指定时间的最早一篇。
     */
    Article selectNext(@Param("publishedAt") LocalDateTime publishedAt);

    /**
     * ngram 全文检索（已发布文章，按相关度降序）。
     * 仅选取列表所需字段，避免回表 content_html / content_md。
     */
    @Select("""
            SELECT a.id, a.title, a.slug, a.summary, a.cover, a.type, a.status,
                   a.category_id, a.is_top, a.allow_comment, a.view_count, a.like_count,
                   a.comment_count, a.word_count, a.reading_time, a.published_at,
                   a.created_at, a.updated_at, a.search_text,
                   MATCH(a.title, a.summary, a.search_text) AGAINST(#{q} IN BOOLEAN MODE) AS relevance
            FROM t_article a
            WHERE a.status = 'published'
              AND MATCH(a.title, a.summary, a.search_text) AGAINST(#{q} IN BOOLEAN MODE)
            ORDER BY relevance DESC, a.published_at DESC
            LIMIT #{offset}, #{size}
            """)
    List<Article> searchByFulltext(@Param("q") String booleanQuery,
                                   @Param("offset") long offset,
                                   @Param("size") long size);

    /**
     * ngram 全文检索计数。
     */
    @Select("""
            SELECT COUNT(*) FROM t_article a
            WHERE a.status = 'published'
              AND MATCH(a.title, a.summary, a.search_text) AGAINST(#{q} IN BOOLEAN MODE)
            """)
    Long countByFulltext(@Param("q") String booleanQuery);
}
