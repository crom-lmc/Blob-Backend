package com.blog.module.media.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.BusinessException;
import com.blog.common.ErrorCode;
import com.blog.common.PageResult;
import com.blog.module.media.entity.Media;
import com.blog.module.media.entity.MediaFolder;
import com.blog.module.media.mapper.MediaFolderMapper;
import com.blog.module.media.mapper.MediaMapper;
import com.blog.security.SecurityUtils;
import com.blog.storage.StorageService;
import com.blog.storage.StoredFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 媒体库服务：上传（普通 / 分片）、列表、目录、删除。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaService {

    private final MediaMapper mediaMapper;
    private final MediaFolderMapper folderMapper;
    private final StorageService storageService;

    /**
     * 普通上传（folderId 为逻辑目录，NULL/0 表示未分组）。
     */
    public Media upload(MultipartFile file, String folder, Long folderId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请选择要上传的文件");
        }
        try {
            StoredFile stored = storageService.store(file, folder);
            return saveRecord(stored, folder, folderId);
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            log.error("文件上传失败", e);
            throw new BusinessException(ErrorCode.UPLOAD_FAILED);
        }
    }

    /**
     * 分片上传 - 初始化。
     */
    public String initChunk(String uploadId, String originalName) {
        return storageService.initChunk(uploadId, originalName);
    }

    /**
     * 分片上传 - 写入分片。
     */
    public void writeChunk(String uploadId, int index, MultipartFile file) {
        try {
            storageService.writeChunk(uploadId, index, file.getInputStream());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.UPLOAD_FAILED, "分片写入失败");
        }
    }

    /**
     * 分片上传 - 合并。
     */
    public Media mergeChunk(String uploadId, String originalName, String contentType, String folder, Long folderId) {
        try {
            StoredFile stored = storageService.mergeChunks(uploadId, originalName, contentType, folder);
            return saveRecord(stored, folder, folderId);
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.UPLOAD_FAILED, "分片合并失败");
        }
    }

    /**
     * 分页列表。folderId 语义：null=全部，0=未分组，>0=指定目录。
     */
    public PageResult<Media> page(long page, long size, String folder, Long folderId, String keyword) {
        LambdaQueryWrapper<Media> wrapper = new LambdaQueryWrapper<Media>()
                .eq(StringUtils.hasText(folder), Media::getFolder, folder == null ? "" : folder)
                .eq(folderId != null && folderId > 0, Media::getFolderId, folderId)
                .isNull(folderId != null && folderId == 0, Media::getFolderId)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(Media::getOriginalName, keyword)
                        .or().like(Media::getFileName, keyword))
                .orderByDesc(Media::getCreatedAt);
        return PageResult.of(mediaMapper.selectPage(new Page<>(page, size), wrapper));
    }

    public List<String> folders() {
        return mediaMapper.selectFolders();
    }

    /**
     * 修改媒体文件：重命名（originalName）与移动目录（folderId）。
     * 两个字段都可选，只更新需要改的那一个。
     * folderId 语义：>0=移到指定目录，0 或负数=移到未分组。
     * 注意：只改逻辑归属，磁盘上的物理文件位置不变。
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, String name, Long folderId) {
        Media media = mediaMapper.selectById(id);
        if (media == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "媒体文件不存在");
        }
        if (name != null && !name.isBlank()) {
            String trimmed = name.trim();
            if (trimmed.length() > 200) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "文件名称过长（最多 200 字）");
            }
            // 不允许修改扩展名，避免展示名与实际文件类型不符
            String oldExt = extOf(media.getOriginalName());
            if (!oldExt.isEmpty() && !oldExt.equalsIgnoreCase(extOf(trimmed))) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "不允许修改文件后缀");
            }
            media.setOriginalName(trimmed);
        }
        if (folderId != null) {
            if (folderId > 0) {
                MediaFolder folder = folderMapper.selectById(folderId);
                if (folder == null) {
                    throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "目标目录不存在");
                }
                media.setFolderId(folderId);
            } else {
                media.setFolderId(null);
            }
        }
        mediaMapper.updateById(media);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Media media = mediaMapper.selectById(id);
        if (media == null) {
            return;
        }
        storageService.delete(media.getUrl());
        mediaMapper.deleteById(id);
    }

    /**
     * 批量删除。
     */
    @Transactional(rollbackFor = Exception.class)
    public int delete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Long id : ids) {
            delete(id);
            count++;
        }
        return count;
    }

    public long count() {
        Long count = mediaMapper.selectCount(null);
        return count == null ? 0 : count;
    }

    /** 取扩展名（含点）；无扩展名返回空串 */
    private String extOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot);
    }

    private Media saveRecord(StoredFile stored, String folder, Long folderId) {
        Media media = new Media();
        media.setFileName(stored.getFileName());
        media.setOriginalName(stored.getOriginalName());
        media.setUrl(stored.getUrl());
        media.setMimeType(stored.getMimeType());
        media.setSize(stored.getSize());
        media.setWidth(stored.getWidth());
        media.setHeight(stored.getHeight());
        media.setFolder(folder == null ? "" : folder);
        media.setFolderId(folderId == null || folderId == 0 ? null : folderId);
        media.setUploaderId(SecurityUtils.currentUserId());
        mediaMapper.insert(media);
        return media;
    }
}
