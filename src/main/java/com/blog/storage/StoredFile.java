package com.blog.storage;

import lombok.Data;

/**
 * 文件存储结果。
 */
@Data
public class StoredFile {

    /** 存储后的文件名（重命名后的唯一名） */
    private String fileName;

    /** 原始文件名 */
    private String originalName;

    /** 对外访问 URL */
    private String url;

    /** MIME 类型 */
    private String mimeType;

    /** 字节大小 */
    private long size;

    /** 图片宽度（非图片为 null） */
    private Integer width;

    /** 图片高度（非图片为 null） */
    private Integer height;

    /** 存储相对路径 */
    private String path;
}
