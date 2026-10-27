package com.blog.module.article.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.module.article.dto.TagRelation;
import com.blog.module.article.entity.ArticleTag;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 文章标签关联 Mapper（复合主键，插入走自定义 SQL）。
 */
@Mapper
public interface ArticleTagMapper extends BaseMapper<ArticleTag> {

    @Insert("INSERT IGNORE INTO t_article_tag(article_id, tag_id) VALUES(#{articleId}, #{tagId})")
    int insertIgnore(@Param("articleId") Long articleId, @Param("tagId") Long tagId);

    /**
     * 批量查询多个文章的标签，避免 N+1。
     */
    @Select("<script>"
            + "SELECT at.article_id AS articleId, t.id, t.name, t.slug, t.color "
            + "FROM t_article_tag at JOIN t_tag t ON t.id = at.tag_id "
            + "WHERE at.article_id IN "
            + "<foreach item='id' collection='ids' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    List<TagRelation> selectTagsByArticleIds(@Param("ids") List<Long> ids);
}
