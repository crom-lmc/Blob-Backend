package com.blog.module.media.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.BusinessException;
import com.blog.common.ErrorCode;
import com.blog.common.PageResult;
import com.blog.module.media.entity.Media;
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
    private final StorageService storageService;

    /**
     * 普通上传。
     */
    public Media upload(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请选择要上传的文件");
        }
        try {
            StoredFile stored = storageService.store(file, folder);
            return saveRecord(stored, folder);
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
    public Media mergeChunk(String uploadId, String originalName, String contentType, String folder) {
        try {
            StoredFile stored = storageService.mergeChunks(uploadId, originalName, contentType, folder);
            return saveRecord(stored, folder);
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.UPLOAD_FAILED, "分片合并失败");
        }
    }

    public PageResult<Media> page(long page, long size, String folder, String keyword) {
        LambdaQueryWrapper<Media> wrapper = new LambdaQueryWrapper<Media>()
                .eq(StringUtils.hasText(folder), Media::getFolder, folder == null ? "" : folder)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(Media::getOriginalName, keyword)
                        .or().like(Media::getFileName, keyword))
                .orderByDesc(Media::getCreatedAt);
        return PageResult.of(mediaMapper.selectPage(new Page<>(page, size), wrapper));
    }

    public List<String> folders() {
        return mediaMapper.selectFolders();
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

    private Media saveRecord(StoredFile stored, String folder) {
        Media media = new Media();
        media.setFileName(stored.getFileName());
        media.setOriginalName(stored.getOriginalName());
        media.setUrl(stored.getUrl());
        media.setMimeType(stored.getMimeType());
        media.setSize(stored.getSize());
        media.setWidth(stored.getWidth());
        media.setHeight(stored.getHeight());
        media.setFolder(folder == null ? "" : folder);
        media.setUploaderId(SecurityUtils.currentUserId());
        mediaMapper.insert(media);
        return media;
    }
}
