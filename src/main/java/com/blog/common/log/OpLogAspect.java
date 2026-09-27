package com.blog.common.log;

import com.blog.module.log.entity.OperationLog;
import com.blog.module.log.service.OperationLogService;
import com.blog.security.SecurityUtils;
import com.blog.util.IpUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 操作日志切面：记录后台写操作（执行成功或失败都会记录）。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OpLogAspect {

    private final OperationLogService operationLogService;

    @AfterReturning(value = "@annotation(opLog)", returning = "result")
    public void afterReturning(JoinPoint joinPoint, OpLog opLog, Object result) {
        record(joinPoint, opLog, null);
    }

    @AfterThrowing(value = "@annotation(opLog)", throwing = "ex")
    public void afterThrowing(JoinPoint joinPoint, OpLog opLog, Exception ex) {
        record(joinPoint, opLog, ex == null ? null : ex.getMessage());
    }

    private void record(JoinPoint joinPoint, OpLog opLog, String error) {
        try {
            OperationLog entity = new OperationLog();
            entity.setUserId(SecurityUtils.currentUserId());
            entity.setModule(opLog.module());
            entity.setAction(opLog.action());
            entity.setIp(IpUtils.getIp());
            String detail = joinPoint.getSignature().getDeclaringType().getSimpleName()
                    + "." + joinPoint.getSignature().getName()
                    + "(" + Arrays.stream(joinPoint.getArgs())
                    .map(arg -> arg == null ? "null" : shorten(arg.toString()))
                    .collect(Collectors.joining(", ")) + ")";
            if (error != null) {
                detail = detail + " => 失败：" + error;
            }
            if (detail.length() > 1000) {
                detail = detail.substring(0, 1000);
            }
            entity.setDetail(detail);
            operationLogService.save(entity);
        } catch (Exception e) {
            log.warn("记录操作日志失败", e);
        }
    }

    private String shorten(String text) {
        String single = text.replaceAll("\\s+", " ");
        return single.length() > 200 ? single.substring(0, 200) + "…" : single;
    }
}
