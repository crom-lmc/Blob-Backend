package com.blog.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * 文件存储抽象：本地磁盘起步，后续可无缝替换为 OSS / MinIO。
 */
public interface StorageService {

    /**
     * 保存上传文件到指定目录。
     */
    StoredFile store(MultipartFile file, String folder) throws IOException;

    /**
     * 保存输入流（用于分片合并等场景）。
     */
    StoredFile store(InputStream inputStream, String originalName, String contentType, long size,
                     String folder) throws IOException;

    /**
     * 删除文件（参数为对外访问 URL 或相对路径）。
     */
    void delete(String urlOrPath);

    /**
     * 分片上传：初始化，返回临时目录标识。
     */
    String initChunk(String uploadId, String originalName);

    /**
     * 分片上传：写入单个分片。
     */
    void writeChunk(String uploadId, int index, java.io.InputStream inputStream) throws IOException;

    /**
     * 分片上传：合并所有分片并返回存储结果。
     */
    StoredFile mergeChunks(String uploadId, String originalName, String contentType, String folder) throws IOException;

    /**
     * 允许的扩展名白名单。
     */
    List<String> allowedExtensions();
}
