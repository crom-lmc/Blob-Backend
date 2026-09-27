package com.blog.module.log.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.PageResult;
import com.blog.module.log.entity.OperationLog;
import com.blog.module.log.mapper.OperationLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 操作日志服务。
 */
@Service
@RequiredArgsConstructor
public class OperationLogService {

    private final OperationLogMapper operationLogMapper;

    public void save(OperationLog log) {
        if (log.getCreatedAt() == null) {
            log.setCreatedAt(java.time.LocalDateTime.now());
        }
        operationLogMapper.insert(log);
    }

    /**
     * 分页查询操作日志。
     */
    public PageResult<OperationLog> page(long page, long size, String module, String action, String keyword) {
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(module), OperationLog::getModule, module)
                .eq(StringUtils.hasText(action), OperationLog::getAction, action)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(OperationLog::getDetail, keyword)
                        .or().like(OperationLog::getIp, keyword))
                .orderByDesc(OperationLog::getCreatedAt);
        Page<OperationLog> p = new Page<>(page, size);
        return PageResult.of(operationLogMapper.selectPage(p, wrapper));
    }

    /**
     * 清理指定天数之前的日志。
     */
    public int cleanBefore(int days) {
        java.time.LocalDateTime deadline = java.time.LocalDateTime.now().minusDays(days);
        return operationLogMapper.delete(new LambdaQueryWrapper<OperationLog>()
                .lt(OperationLog::getCreatedAt, deadline));
    }
}
