package com.blog.module.stat.controller;

import com.blog.common.R;
import com.blog.module.stat.dto.DashboardStatsVO;
import com.blog.module.stat.service.StatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台仪表盘统计。
 */
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@Tag(name = "后台-仪表盘", description = "统计卡片、近 30 天趋势、热门文章、待审评论")
public class StatController {

    private final StatService statService;

    @GetMapping("/stats")
    @Operation(summary = "仪表盘统计数据")
    public R<DashboardStatsVO> stats() {
        return R.ok(statService.dashboard());
    }
}
