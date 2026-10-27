package com.blog.module.article.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.module.article.dto.TagCountDTO;
import com.blog.module.article.entity.Tag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 标签 Mapper。
 */
@Mapper
public interface TagMapper extends BaseMapper<Tag> {

    /**
     * 标签列表 + 文章数（t.id 为主键，5.7 的 ONLY_FULL_GROUP_BY 允许按主键分组选择其它列）。
     */
    @Select("SELECT t.id, t.name, t.slug, t.color, COUNT(at.article_id) AS article_count "
            + "FROM t_tag t LEFT JOIN t_article_tag at ON at.tag_id = t.id "
            + "GROUP BY t.id ORDER BY article_count DESC, t.id ASC")
    List<TagCountDTO> selectWithCount();
}
