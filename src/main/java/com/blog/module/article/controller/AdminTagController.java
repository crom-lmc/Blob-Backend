package com.blog.module.article.controller;

import com.blog.common.R;
import com.blog.common.log.OpLog;
import com.blog.module.article.dto.TagSaveRequest;
import com.blog.module.article.dto.TagVO;
import com.blog.module.article.service.TagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 后台标签管理。
 */
@RestController
@RequestMapping("/api/admin/tags")
@RequiredArgsConstructor
@Tag(name = "后台-标签", description = "标签 CRUD 与合并")
public class AdminTagController {

    private final TagService tagService;

    @GetMapping
    @Operation(summary = "标签列表（含文章数）")
    public R<List<TagVO>> list() {
        return R.ok(tagService.listWithCount());
    }

    @PostMapping
    @OpLog(module = "tag", action = "create")
    @Operation(summary = "新增标签")
    public R<Long> create(@RequestBody TagSaveRequest request) {
        request.setId(null);
        return R.ok(tagService.save(request));
    }

    @PutMapping("/{id}")
    @OpLog(module = "tag", action = "update")
    @Operation(summary = "修改标签")
    public R<Long> update(@PathVariable Long id, @RequestBody TagSaveRequest request) {
        request.setId(id);
        return R.ok(tagService.save(request));
    }

    @DeleteMapping("/{id}")
    @OpLog(module = "tag", action = "delete")
    @Operation(summary = "删除标签")
    public R<Void> delete(@PathVariable Long id) {
        tagService.delete(id);
        return R.ok();
    }

    @PostMapping("/merge")
    @OpLog(module = "tag", action = "merge")
    @Operation(summary = "合并标签", description = "sourceId 下的文章全部迁移到 targetId，随后删除 sourceId")
    public R<Void> merge(@RequestBody MergeRequest request) {
        tagService.merge(request.getSourceId(), request.getTargetId());
        return R.ok();
    }

    @Data
    public static class MergeRequest {
        private Long sourceId;
        private Long targetId;
    }
}
