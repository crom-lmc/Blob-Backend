package com.blog.module.stat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.module.article.entity.Article;
import com.blog.module.article.mapper.ArticleMapper;
import com.blog.module.article.mapper.CategoryMapper;
import com.blog.module.article.mapper.TagMapper;
import com.blog.module.article.service.ArticleService;
import com.blog.module.comment.entity.Comment;
import com.blog.module.comment.mapper.CommentMapper;
import com.blog.module.comment.service.CommentService;
import com.blog.module.media.mapper.MediaMapper;
import com.blog.module.stat.dto.DailyCountVO;
import com.blog.module.stat.dto.DashboardStatsVO;
import com.blog.module.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 仪表盘统计服务。
 * 说明：MySQL 5.7 不使用递归 CTE 生成日期序列，改为在应用层补齐缺失日期。
 */
@Service
@RequiredArgsConstructor
public class StatService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int TREND_DAYS = 30;

    private final ArticleMapper articleMapper;
    private final CommentMapper commentMapper;
    private final CategoryMapper categoryMapper;
    private final TagMapper tagMapper;
    private final UserMapper userMapper;
    private final MediaMapper mediaMapper;
    private final ArticleService articleService;
    private final CommentService commentService;

    public DashboardStatsVO dashboard() {
        DashboardStatsVO vo = new DashboardStatsVO();
        vo.setArticleCount(count(articleMapper.selectCount(null)));
        vo.setPublishedCount(count(articleMapper.selectCount(
                new LambdaQueryWrapper<Article>().eq(Article::getStatus, "published"))));
        vo.setDraftCount(count(articleMapper.selectCount(
                new LambdaQueryWrapper<Article>().eq(Article::getStatus, "draft"))));
        vo.setViewCount(articleMapper.sumViewCount());
        vo.setCommentCount(count(commentMapper.selectCount(null)));
        vo.setPendingCommentCount(commentService.countPending());
        vo.setCategoryCount(count(categoryMapper.selectCount(null)));
        vo.setTagCount(count(tagMapper.selectCount(null)));
        vo.setUserCount(count(userMapper.selectCount(null)));
        vo.setMediaCount(count(mediaMapper.selectCount(null)));

        LocalDateTime start = LocalDate.now().minusDays(TREND_DAYS - 1).atStartOfDay();
        vo.setArticleTrend(fillTrend(articleMapper.selectDailyArticleCount(start), TREND_DAYS));
        vo.setCommentTrend(fillTrend(commentMapper.selectDailyCommentCount(start), TREND_DAYS));
        vo.setTopArticles(articleService.topArticles(10));
        vo.setPendingComments(commentService.recentPending(5));
        return vo;
    }

    /**
     * 补齐日期序列，缺失日期补 0，保证前端折线图连续。
     */
    private List<DailyCountVO> fillTrend(List<DailyCountVO> source, int days) {
        Map<String, Long> map = new LinkedHashMap<>();
        if (source != null) {
            for (DailyCountVO item : source) {
                map.put(item.getDate(), item.getCount());
            }
        }
        List<DailyCountVO> result = new ArrayList<>(days);
        LocalDate today = LocalDate.now();
        for (int i = days - 1; i >= 0; i--) {
            String date = today.minusDays(i).format(FORMATTER);
            DailyCountVO item = new DailyCountVO();
            item.setDate(date);
            item.setCount(map.getOrDefault(date, 0L));
            result.add(item);
        }
        return result;
    }

    private long count(Long value) {
        return value == null ? 0 : value;
    }
}
