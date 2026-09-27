package com.blog.module.log.controller;

import com.blog.common.PageResult;
import com.blog.common.R;
import com.blog.module.log.entity.OperationLog;
import com.blog.module.log.service.OperationLogService;
import com.blog.module.setting.service.SettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 后台操作日志（仅管理员）。
 */
@RestController
@RequestMapping("/api/admin/logs")
@RequiredArgsConstructor
@Tag(name = "后台-操作日志", description = "分页查询与清理")
public class AdminLogController {

    private final OperationLogService operationLogService;
    private final SettingService settingService;

    @GetMapping
    @Operation(summary = "操作日志分页列表")
    public R<PageResult<OperationLog>> page(@RequestParam(defaultValue = "1") long page,
                                            @RequestParam(required = false) Long size,
                                            @RequestParam(required = false) String module,
                                            @RequestParam(required = false) String action,
                                            @RequestParam(required = false) String keyword) {
        long pageSize = size != null && size > 0 ? size : settingService.getInt("page_size", 10);
        return R.ok(operationLogService.page(page, pageSize, module, action, keyword));
    }

    @DeleteMapping("/clean")
    @Operation(summary = "清理历史日志", description = "删除 days 天之前的记录，days 默认 90")
    public R<Integer> clean(@RequestParam(defaultValue = "90") int days) {
        return R.ok(operationLogService.cleanBefore(days));
    }
}
