package com.blog.module.article.dto;

import com.blog.module.article.entity.Tag;
import lombok.Data;

import java.io.Serializable;

/**
 * 标签视图对象。
 */
@Data
public class TagVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String slug;
    private String color;
    /** 关联文章数，用于标签云加权 */
    private Integer articleCount;

    public static TagVO from(Tag tag) {
        TagVO vo = new TagVO();
        vo.setId(tag.getId());
        vo.setName(tag.getName());
        vo.setSlug(tag.getSlug());
        vo.setColor(tag.getColor());
        return vo;
    }

    public static TagVO from(Tag tag, Integer articleCount) {
        TagVO vo = from(tag);
        vo.setArticleCount(articleCount);
        return vo;
    }
}
