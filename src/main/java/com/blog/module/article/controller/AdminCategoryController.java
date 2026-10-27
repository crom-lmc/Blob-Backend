package com.blog.module.article.controller;

import com.blog.common.R;
import com.blog.common.log.OpLog;
import com.blog.module.article.dto.CategorySaveRequest;
import com.blog.module.article.dto.CategoryVO;
import com.blog.module.article.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 后台分类管理。
 */
@RestController
@RequestMapping("/api/admin/categories")
@RequiredArgsConstructor
@Tag(name = "后台-分类", description = "树形分类 CRUD 与排序")
public class AdminCategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @Operation(summary = "分类树")
    public R<List<CategoryVO>> tree() {
        return R.ok(categoryService.tree());
    }

    @PostMapping
    @OpLog(module = "category", action = "create")
    @Operation(summary = "新增分类")
    public R<Long> create(@RequestBody CategorySaveRequest request) {
        request.setId(null);
        return R.ok(categoryService.save(request));
    }

    @PutMapping("/{id}")
    @OpLog(module = "category", action = "update")
    @Operation(summary = "修改分类")
    public R<Long> update(@PathVariable Long id, @RequestBody CategorySaveRequest request) {
        request.setId(id);
        return R.ok(categoryService.save(request));
    }

    @PutMapping("/sort")
    @OpLog(module = "category", action = "sort")
    @Operation(summary = "拖拽排序", description = "按传入顺序重新计算 sort 值")
    public R<Void> sort(@RequestBody List<Long> ids) {
        categoryService.sort(ids);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @OpLog(module = "category", action = "delete")
    @Operation(summary = "删除分类", description = "子分类上移到父级，文章解除关联")
    public R<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return R.ok();
    }

    @PostMapping("/recount")
    @Operation(summary = "重算分类文章数")
    public R<Void> recount() {
        categoryService.tree();
        return R.ok();
    }
}
