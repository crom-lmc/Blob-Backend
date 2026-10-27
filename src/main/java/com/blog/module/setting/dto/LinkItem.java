package com.blog.module.setting.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 友情链接 / 社交链接项。
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LinkItem implements Serializable {

    private static final long serialVersionUID = 1L;

    private String name;

    private String url;

    private String desc;

    private String icon;

    public LinkItem(String name, String url) {
        this.name = name;
        this.url = url;
    }
}
