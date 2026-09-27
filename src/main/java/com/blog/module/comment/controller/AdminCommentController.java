package com.blog.module.comment.controller;

import com.blog.common.PageResult;
import com.blog.common.R;
import com.blog.common.log.OpLog;
import com.blog.module.comment.dto.CommentVO;
import com.blog.module.comment.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 后台评论管理（author 角色可访问）。
 */
@RestController
@RequestMapping("/api/admin/comments")
@RequiredArgsConstructor
@Tag(name = "后台-评论", description = "审核、拒绝、回复、批量删除")
public class AdminCommentController {

    private final CommentService commentService;

    @GetMapping
    @Operation(summary = "评论分页列表", description = "status: pending/approved/spam/deleted")
    public R<PageResult<CommentVO>> page(@RequestParam(defaultValue = "1") long page,
                                         @RequestParam(defaultValue = "20") long size,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) Long articleId) {
        return R.ok(commentService.page(page, size, status, keyword, articleId));
    }

    @PutMapping("/{id}/approve")
    @OpLog(module = "comment", action = "approve")
    @Operation(summary = "审核通过")
    public R<Void> approve(@PathVariable Long id) {
        commentService.approve(List.of(id));
        return R.ok();
    }

    @PutMapping("/{id}/reject")
    @OpLog(module = "comment", action = "reject")
    @Operation(summary = "拒绝（标记为垃圾评论）")
    public R<Void> reject(@PathVariable Long id) {
        commentService.reject(List.of(id));
        return R.ok();
    }

    @PostMapping("/batch/approve")
    @OpLog(module = "comment", action = "batch-approve")
    @Operation(summary = "批量审核通过")
    public R<Void> batchApprove(@RequestBody BatchRequest request) {
        commentService.approve(request.getIds());
        return R.ok();
    }

    @PostMapping("/batch/delete")
    @OpLog(module = "comment", action = "batch-delete")
    @Operation(summary = "批量删除")
    public R<Integer> batchDelete(@RequestBody BatchRequest request) {
        return R.ok(commentService.delete(request.getIds()));
    }

    @DeleteMapping("/{id}")
    @OpLog(module = "comment", action = "delete")
    @Operation(summary = "删除评论")
    public R<Void> delete(@PathVariable Long id) {
        commentService.delete(List.of(id));
        return R.ok();
    }

    @PostMapping("/reply")
    @OpLog(module = "comment", action = "reply")
    @Operation(summary = "回复评论（管理员身份，直接通过审核）")
    public R<CommentVO> reply(@RequestBody ReplyRequest request) {
        return R.ok(commentService.reply(request.getArticleId(), request.getParentId(), request.getContent()));
    }

    @Data
    public static class BatchRequest {
        private List<Long> ids;
    }

    @Data
    public static class ReplyRequest {
        private Long articleId;
        private Long parentId;
        private String content;
    }
}
