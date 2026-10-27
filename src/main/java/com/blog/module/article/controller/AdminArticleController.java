package com.blog.module.article.controller;

import com.blog.common.PageResult;
import com.blog.common.R;
import com.blog.common.log.OpLog;
import com.blog.module.article.dto.*;
import com.blog.module.article.service.ArticleService;
import com.blog.module.setting.service.SettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 后台文章管理（author 角色可访问）。
 */
@RestController
@RequestMapping("/api/admin/articles")
@RequiredArgsConstructor
@Tag(name = "后台-文章", description = "文章 CRUD、发布、置顶、批量删除、Markdown 导入导出")
public class AdminArticleController {

    private final ArticleService articleService;
    private final SettingService settingService;

    @GetMapping
    @Operation(summary = "文章分页列表", description = "支持状态/分类/标签/时间/关键词筛选")
    public R<PageResult<ArticleListVO>> page(@RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(required = false) Long categoryId,
                                             @RequestParam(required = false) Long tagId,
                                             @RequestParam(required = false) String type,
                                             @RequestParam(required = false) String sort,
                                             @RequestParam(required = false)
                                             @org.springframework.format.annotation.DateTimeFormat(
                                                     iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME)
                                             java.time.LocalDateTime start,
                                             @RequestParam(required = false)
                                             @org.springframework.format.annotation.DateTimeFormat(
                                                     iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME)
                                             java.time.LocalDateTime end,
                                             @RequestParam(defaultValue = "1") long page,
                                             @RequestParam(required = false) Long size) {
        ArticleQuery query = new ArticleQuery();
        query.setKeyword(keyword);
        query.setStatus(status);
        query.setCategoryId(categoryId);
        query.setTagId(tagId);
        query.setType(type);
        query.setSort(sort == null ? "created" : sort);
        query.setStart(start);
        query.setEnd(end);
        query.setPage(page);
        query.setSize(resolvePageSize(size));
        return R.ok(articleService.queryAdmin(query));
    }

    @GetMapping("/{id}")
    @Operation(summary = "文章详情（含 Markdown 原文，用于编辑回显）")
    public R<ArticleDetailVO> detail(@PathVariable Long id) {
        return R.ok(articleService.detail(articleService.getById(id), false));
    }

    @PostMapping
    @OpLog(module = "article", action = "create")
    @Operation(summary = "新增文章")
    public R<Map<String, Object>> create(@Valid @RequestBody ArticleSaveRequest request) {
        request.setId(null);
        return R.ok(Map.of("id", articleService.save(request)));
    }

    @PutMapping("/{id}")
    @OpLog(module = "article", action = "update")
    @Operation(summary = "修改文章")
    public R<Map<String, Object>> update(@PathVariable Long id, @Valid @RequestBody ArticleSaveRequest request) {
        request.setId(id);
        return R.ok(Map.of("id", articleService.save(request)));
    }

    @PostMapping("/{id}/publish")
    @OpLog(module = "article", action = "publish")
    @Operation(summary = "发布 / 下线")
    public R<Void> publish(@PathVariable Long id, @RequestParam(defaultValue = "true") boolean publish) {
        articleService.publish(id, publish);
        return R.ok();
    }

    @PostMapping("/{id}/top")
    @OpLog(module = "article", action = "top")
    @Operation(summary = "置顶 / 取消置顶")
    public R<Void> top(@PathVariable Long id, @RequestParam(defaultValue = "true") boolean top) {
        articleService.top(id, top);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @OpLog(module = "article", action = "delete")
    @Operation(summary = "删除文章")
    public R<Void> delete(@PathVariable Long id) {
        articleService.delete(List.of(id));
        return R.ok();
    }

    @PostMapping("/batch/delete")
    @OpLog(module = "article", action = "batch-delete")
    @Operation(summary = "批量删除文章")
    public R<Integer> batchDelete(@RequestBody BatchRequest request) {
        return R.ok(articleService.delete(request.getIds()));
    }

    @GetMapping("/{id}/export")
    @Operation(summary = "导出 Markdown（含 Front Matter）")
    public R<Map<String, String>> export(@PathVariable Long id) {
        return R.ok(Map.of("markdown", articleService.exportMarkdown(id)));
    }

    @PostMapping("/import")
    @OpLog(module = "article", action = "import")
    @Operation(summary = "导入 Markdown 文件", description = "自动识别 Front Matter，缺失分类/标签自动创建")
    public R<Map<String, Object>> importMarkdown(@RequestPart("file") MultipartFile file,
                                                 @RequestParam(defaultValue = "draft") String status) {
        String content;
        try {
            content = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new com.blog.common.BusinessException(com.blog.common.ErrorCode.UPLOAD_FAILED, "文件读取失败");
        }
        Long id = articleService.importMarkdown(content, status);
        return R.ok(Map.of("id", id));
    }

    @PostMapping("/rebuild-search")
    @OpLog(module = "article", action = "rebuild-search")
    @Operation(summary = "重建检索文本", description = "从已渲染 HTML 抽取纯文本回填 search_text，用于存量数据初始化或索引重建")
    public R<Void> rebuildSearch() {
        articleService.rebuildSearchText();
        return R.ok();
    }

    /** 解析每页条数：传入且合法则优先，否则回退站点设置 page_size（默认 10） */
    private long resolvePageSize(Long size) {
        return size != null && size > 0 ? size : settingService.getInt("page_size", 10);
    }

    @Data
    public static class BatchRequest {
        private List<Long> ids;
    }
}
