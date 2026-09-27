package com.blog.module.comment.service;

import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.common.BusinessException;
import com.blog.common.ErrorCode;
import com.blog.common.PageResult;
import com.blog.common.ratelimit.RateLimiter;
import com.blog.module.article.entity.Article;
import com.blog.module.article.mapper.ArticleMapper;
import com.blog.module.article.service.ArticleService;
import com.blog.module.comment.dto.CommentSubmitRequest;
import com.blog.module.comment.dto.CommentVO;
import com.blog.module.comment.entity.Comment;
import com.blog.module.comment.mapper.CommentMapper;
import com.blog.module.setting.service.SettingService;
import com.blog.security.SecurityUtils;
import com.blog.util.IpUtils;
import com.blog.util.SensitiveWordUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 评论服务：前台提交、文章评论树、后台审核与回复。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentMapper commentMapper;
    private final ArticleMapper articleMapper;
    private final ArticleService articleService;
    private final SettingService settingService;
    private final RateLimiter rateLimiter;

    /**
     * 前台：文章评论（顶层 + 一层回复）。
     */
    public PageResult<CommentVO> articleComments(Long articleId, long page, long size) {
        long total = commentMapper.selectCount(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getArticleId, articleId)
                .eq(Comment::getStatus, "approved")
                .eq(Comment::getParentId, 0));
        if (total == 0) {
            return PageResult.empty(page, size);
        }
        // 顶层分页
        LambdaQueryWrapper<Comment> topWrapper = new LambdaQueryWrapper<Comment>()
                .eq(Comment::getArticleId, articleId)
                .eq(Comment::getStatus, "approved")
                .eq(Comment::getParentId, 0)
                .orderByDesc(Comment::getCreatedAt);
        long offset = (Math.max(1, page) - 1) * size;
        List<Comment> tops = commentMapper.selectList(topWrapper.last("LIMIT " + offset + ", " + size));
        if (tops.isEmpty()) {
            return PageResult.empty(page, size);
        }
        List<Long> topIds = tops.stream().map(Comment::getId).toList();
        List<Comment> replies = commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getStatus, "approved")
                .in(Comment::getParentId, topIds)
                .orderByAsc(Comment::getCreatedAt));
        Map<Long, List<CommentVO>> replyMap = replies.stream()
                .map(this::toVO)
                .collect(Collectors.groupingBy(CommentVO::getParentId));
        List<CommentVO> result = tops.stream().map(comment -> {
            CommentVO vo = toVO(comment);
            vo.setReplies(replyMap.getOrDefault(comment.getId(), new ArrayList<>()));
            return vo;
        }).toList();
        return new PageResult<>(result, total, page, size);
    }

    /**
     * 前台提交评论：频率限制 + 敏感词过滤 + 默认待审核。
     */
    @Transactional(rollbackFor = Exception.class)
    public CommentVO submit(CommentSubmitRequest request, String ip, String userAgent) {
        // 1. 频率限制（IP 维度）
        if (!rateLimiter.tryAcquireComment(ip)) {
            throw new BusinessException(ErrorCode.COMMENT_TOO_FREQUENT);
        }
        Article article = articleService.resolve(request.getArticleIdOrSlug());
        if (!"published".equals(article.getStatus())) {
            throw new BusinessException(ErrorCode.ARTICLE_NOT_PUBLISHED);
        }
        if (article.getAllowComment() == null || article.getAllowComment() != 1) {
            throw new BusinessException(ErrorCode.COMMENT_CLOSED);
        }

        // 2. 敏感词过滤
        String content = request.getContent().trim();
        List<String> hits = SensitiveWordUtils.find(content);
        if (!hits.isEmpty()) {
            throw new BusinessException(ErrorCode.COMMENT_CONTAINS_SENSITIVE,
                    "评论包含敏感词：" + String.join("、", hits));
        }

        // 3. 父评论校验（仅支持一层回复）
        Long parentId = 0L;
        if (request.getParentId() != null && request.getParentId() > 0) {
            Comment parent = commentMapper.selectById(request.getParentId());
            if (parent == null || !parent.getArticleId().equals(article.getId())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "被回复的评论不存在");
            }
            // 回复回复时挂到顶层，保证只有一层
            parentId = (parent.getParentId() == null || parent.getParentId() == 0)
                    ? parent.getId() : parent.getParentId();
        }

        Comment comment = new Comment();
        comment.setArticleId(article.getId());
        comment.setParentId(parentId);
        comment.setAuthorName(request.getAuthorName().trim());
        comment.setAuthorEmail(request.getAuthorEmail());
        comment.setAuthorSite(request.getAuthorSite());
        comment.setAuthorAvatar(avatar(request.getAuthorEmail()));
        comment.setContent(content);
        comment.setUserAgent(StringUtils.hasText(userAgent) ? userAgent : IpUtils.getUserAgent());
        comment.setIp(ip);
        comment.setIsAdmin(SecurityUtils.isAdmin() ? 1 : 0);

        // 管理员免审核；其余按站点设置决定是否待审核
        boolean reviewOn = settingService.getBool("comment_review_on", true);
        comment.setStatus(comment.getIsAdmin() == 1 || !reviewOn ? "approved" : "pending");
        commentMapper.insert(comment);

        if ("approved".equals(comment.getStatus())) {
            articleService.refreshCommentCount(article.getId());
        }
        return toVO(comment);
    }

    // ---------------- 后台管理 ----------------

    public PageResult<CommentVO> page(long page, long size, String status, String keyword, Long articleId) {
        LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<Comment>()
                .eq(StringUtils.hasText(status), Comment::getStatus, status)
                .eq(articleId != null, Comment::getArticleId, articleId)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(Comment::getContent, keyword)
                        .or().like(Comment::getAuthorName, keyword)
                        .or().like(Comment::getAuthorEmail, keyword))
                .orderByDesc(Comment::getCreatedAt);
        var p = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<Comment>(page, size);
        var result = commentMapper.selectPage(p, wrapper);
        List<CommentVO> vos = result.getRecords().stream().map(this::toVO).toList();
        fillArticleTitle(vos);
        return new PageResult<>(vos, result.getTotal(), page, size);
    }

    /**
     * 审核通过。
     */
    @Transactional(rollbackFor = Exception.class)
    public void approve(List<Long> ids) {
        for (Long id : ids) {
            Comment comment = commentMapper.selectById(id);
            if (comment == null) {
                continue;
            }
            Comment update = new Comment();
            update.setId(id);
            update.setStatus("approved");
            commentMapper.updateById(update);
            articleService.refreshCommentCount(comment.getArticleId());
        }
    }

    /**
     * 拒绝（标记为垃圾评论，不删除，便于审计）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void reject(List<Long> ids) {
        for (Long id : ids) {
            Comment comment = commentMapper.selectById(id);
            if (comment == null) {
                continue;
            }
            Comment update = new Comment();
            update.setId(id);
            update.setStatus("spam");
            commentMapper.updateById(update);
            articleService.refreshCommentCount(comment.getArticleId());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(List<Long> ids) {
        int count = 0;
        for (Long id : ids) {
            Comment comment = commentMapper.selectById(id);
            if (comment == null) {
                continue;
            }
            commentMapper.deleteById(id);
            articleService.refreshCommentCount(comment.getArticleId());
            count++;
        }
        return count;
    }

    /**
     * 管理员回复评论。
     */
    @Transactional(rollbackFor = Exception.class)
    public CommentVO reply(Long articleId, Long parentId, String content) {
        Article article = articleMapper.selectById(articleId);
        if (article == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "文章不存在");
        }
        Comment parent = parentId == null || parentId == 0 ? null : commentMapper.selectById(parentId);
        Long topId = 0L;
        if (parent != null) {
            topId = (parent.getParentId() == null || parent.getParentId() == 0)
                    ? parent.getId() : parent.getParentId();
        }
        com.blog.security.SecurityUser user = SecurityUtils.currentUser().orElse(null);
        Comment comment = new Comment();
        comment.setArticleId(articleId);
        comment.setParentId(topId);
        comment.setAuthorName(user == null ? "管理员" :
                (StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername()));
        comment.setAuthorEmail(user == null ? null : user.getEmail());
        comment.setAuthorAvatar(user == null ? null : user.getAvatar());
        comment.setContent(content.trim());
        comment.setStatus("approved");
        comment.setIsAdmin(1);
        comment.setIp(IpUtils.getIp());
        comment.setUserAgent(IpUtils.getUserAgent());
        commentMapper.insert(comment);
        articleService.refreshCommentCount(articleId);
        return toVO(comment);
    }

    /**
     * 仪表盘：最近待审评论。
     */
    public List<CommentVO> recentPending(int limit) {
        List<Comment> list = commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getStatus, "pending")
                .orderByDesc(Comment::getCreatedAt)
                .last("LIMIT " + Math.max(1, limit)));
        List<CommentVO> vos = list.stream().map(this::toVO).toList();
        fillArticleTitle(vos);
        return vos;
    }

    public long countPending() {
        Long count = commentMapper.selectCount(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getStatus, "pending"));
        return count == null ? 0 : count;
    }

    // ---------------- 私有方法 ----------------

    private CommentVO toVO(Comment comment) {
        CommentVO vo = new CommentVO();
        vo.setId(comment.getId());
        vo.setArticleId(comment.getArticleId());
        vo.setParentId(comment.getParentId());
        vo.setAuthorName(comment.getAuthorName());
        vo.setAuthorEmail(comment.getAuthorEmail());
        vo.setAuthorSite(comment.getAuthorSite());
        vo.setAuthorAvatar(comment.getAuthorAvatar());
        vo.setContent(comment.getContent());
        vo.setStatus(comment.getStatus());
        vo.setIsAdmin(comment.getIsAdmin());
        vo.setIp(comment.getIp());
        vo.setCreatedAt(comment.getCreatedAt());
        return vo;
    }

    private void fillArticleTitle(List<CommentVO> vos) {
        Set<Long> ids = vos.stream().map(CommentVO::getArticleId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, Article> map = articleMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Article::getId, a -> a));
        for (CommentVO vo : vos) {
            Article article = map.get(vo.getArticleId());
            if (article != null) {
                vo.setArticleTitle(article.getTitle());
                vo.setArticleSlug(article.getSlug());
            }
        }
    }

    /**
     * 根据邮箱生成 Gravatar 头像。
     */
    private String avatar(String email) {
        if (!StringUtils.hasText(email)) {
            return null;
        }
        return "https://www.gravatar.com/avatar/" + DigestUtil.md5Hex(email.trim().toLowerCase())
                + "?d=identicon&s=80";
    }
}
