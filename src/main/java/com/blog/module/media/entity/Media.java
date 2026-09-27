package com.blog.module.media.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 媒体文件（t_media）。
 */
@Data
@TableName("t_media")
public class Media implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String fileName;

    private String originalName;

    private String url;

    private String mimeType;

    private Long size;

    private Integer width;

    private Integer height;

    private String folder;

    private Long uploaderId;

    private LocalDateTime createdAt;
}
