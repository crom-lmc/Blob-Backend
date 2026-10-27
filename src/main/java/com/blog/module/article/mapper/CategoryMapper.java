package com.blog.module.article.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.module.article.entity.Category;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 分类 Mapper。
 */
@Mapper
public interface CategoryMapper extends BaseMapper<Category> {

    /**
     * 重算分类下的已发布文章数（冗余字段维护）。
     */
    @Update("UPDATE t_category SET article_count = "
            + "(SELECT COUNT(*) FROM t_article WHERE category_id = #{id} AND status = 'published') "
            + "WHERE id = #{id}")
    int recount(@Param("id") Long id);
}
