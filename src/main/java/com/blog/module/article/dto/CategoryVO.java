package com.blog.module.article.dto;

import com.blog.module.article.entity.Category;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 分类视图对象（支持树形结构）。
 */
@Data
public class CategoryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String slug;
    private String description;
    private Long parentId;
    private Integer sort;
    private Integer articleCount;
    private List<CategoryVO> children = new ArrayList<>();

    public static CategoryVO from(Category category) {
        CategoryVO vo = new CategoryVO();
        vo.setId(category.getId());
        vo.setName(category.getName());
        vo.setSlug(category.getSlug());
        vo.setDescription(category.getDescription());
        vo.setParentId(category.getParentId());
        vo.setSort(category.getSort());
        vo.setArticleCount(category.getArticleCount());
        return vo;
    }
}
