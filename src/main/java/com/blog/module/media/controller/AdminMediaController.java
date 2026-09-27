package com.blog.module.media.controller;

import com.blog.common.PageResult;
import com.blog.common.R;
import com.blog.common.log.OpLog;
import com.blog.module.media.entity.Media;
import com.blog.module.media.service.MediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 后台媒体库（author 角色可访问）。
 */
@RestController
@RequestMapping("/api/admin/media")
@RequiredArgsConstructor
@Tag(name = "后台-媒体库", description = "上传（普通 / 分片）、列表、目录、删除")
public class AdminMediaController {

    private final MediaService mediaService;

    @GetMapping
    @Operation(summary = "媒体列表")
    public R<PageResult<Media>> page(@RequestParam(defaultValue = "1") long page,
                                     @RequestParam(defaultValue = "24") long size,
                                     @RequestParam(required = false) String folder,
                                     @RequestParam(required = false) String keyword) {
        return R.ok(mediaService.page(page, size, folder, keyword));
    }

    @GetMapping("/folders")
    @Operation(summary = "目录列表")
    public R<List<String>> folders() {
        return R.ok(mediaService.folders());
    }

    @PostMapping("/upload")
    @OpLog(module = "media", action = "upload")
    @Operation(summary = "上传文件", description = "类型白名单 + 大小限制（默认 5MB），自动读取图片宽高")
    public R<Media> upload(@RequestPart("file") MultipartFile file,
                           @RequestParam(required = false, defaultValue = "") String folder) {
        return R.ok(mediaService.upload(file, folder));
    }

    @PostMapping("/upload/chunk/init")
    @Operation(summary = "分片上传 - 初始化", description = "返回 uploadId，客户端按序号依次上传分片")
    public R<String> initChunk(@RequestBody ChunkInitRequest request) {
        return R.ok(mediaService.initChunk(request.getUploadId(), request.getOriginalName()));
    }

    @PostMapping("/upload/chunk")
    @Operation(summary = "分片上传 - 写入分片")
    public R<Void> writeChunk(@RequestPart("file") MultipartFile file,
                              @RequestParam String uploadId,
                              @RequestParam int index) {
        mediaService.writeChunk(uploadId, index, file);
        return R.ok();
    }

    @PostMapping("/upload/chunk/merge")
    @OpLog(module = "media", action = "upload-chunk")
    @Operation(summary = "分片上传 - 合并")
    public R<Media> mergeChunk(@RequestBody ChunkMergeRequest request) {
        return R.ok(mediaService.mergeChunk(request.getUploadId(), request.getOriginalName(),
                request.getContentType(), request.getFolder()));
    }

    @DeleteMapping("/{id}")
    @OpLog(module = "media", action = "delete")
    @Operation(summary = "删除媒体文件（同时删除磁盘文件）")
    public R<Void> delete(@PathVariable Long id) {
        mediaService.delete(id);
        return R.ok();
    }

    @PostMapping("/batch/delete")
    @OpLog(module = "media", action = "batch-delete")
    @Operation(summary = "批量删除媒体文件")
    public R<Integer> batchDelete(@RequestBody BatchRequest request) {
        return R.ok(mediaService.delete(request.getIds()));
    }

    @Data
    public static class ChunkInitRequest {
        private String uploadId;
        private String originalName;
    }

    @Data
    public static class ChunkMergeRequest {
        private String uploadId;
        private String originalName;
        private String contentType;
        private String folder;
    }

    @Data
    public static class BatchRequest {
        private List<Long> ids;
    }
}
