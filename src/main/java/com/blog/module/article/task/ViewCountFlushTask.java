package com.blog.module.article.task;

import com.blog.module.article.service.ArticleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 阅读数落库任务：每 5 分钟把 Redis 中累计的阅读数批量写入数据库，避免高频写库。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ViewCountFlushTask {

    private final ArticleService articleService;

    /**
     * 固定间隔 5 分钟执行（上次结束后 5 分钟再次执行）。
     */
    @Scheduled(fixedDelay = 5 * 60 * 1000L, initialDelay = 60 * 1000L)
    public void flush() {
        try {
            articleService.flushViewCounts();
        } catch (Exception e) {
            log.error("阅读数落库任务执行失败", e);
        }
    }
}
