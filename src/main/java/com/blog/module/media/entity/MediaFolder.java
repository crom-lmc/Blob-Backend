package com.blog.module.media.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 媒体目录（t_media_folder，树形结构）。
 */
@Data
@TableName("t_media_folder")
public class MediaFolder implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** 父目录 ID，0 为顶级 */
    private Long parentId;

    private Integer sort;

    private LocalDateTime createdAt;
}
