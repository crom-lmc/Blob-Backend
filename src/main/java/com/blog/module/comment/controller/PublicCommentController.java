package com.blog.module.comment.controller;

import com.blog.common.PageResult;
import com.blog.common.R;
import com.blog.module.article.service.ArticleService;
import com.blog.module.comment.dto.CommentSubmitRequest;
import com.blog.module.comment.dto.CommentVO;
import com.blog.module.comment.service.CommentService;
import com.blog.module.setting.service.SettingService;
import com.blog.util.IpUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 前台评论接口（无需鉴权）。
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
@Tag(name = "前台-评论", description = "文章评论列表与提交")
public class PublicCommentController {

    private final CommentService commentService;
    private final ArticleService articleService;
    private final SettingService settingService;

    @GetMapping("/articles/{idOrSlug}/comments")
    @Operation(summary = "文章评论列表", description = "仅返回已审核评论，含一层回复")
    public R<PageResult<CommentVO>> comments(@PathVariable String idOrSlug,
                                             @RequestParam(defaultValue = "1") long page,
                                             @RequestParam(required = false) Long size) {
        Long articleId = articleService.resolve(idOrSlug).getId();
        long pageSize = size != null && size > 0 ? size : settingService.getInt("page_size", 10);
        return R.ok(commentService.articleComments(articleId, page, pageSize));
    }

    @PostMapping("/comments")
    @Operation(summary = "提交评论", description = "频率限制 + 敏感词过滤，默认进入待审核")
    public R<CommentVO> submit(@Valid @RequestBody CommentSubmitRequest request,
                               HttpServletRequest httpRequest) {
        return R.ok(commentService.submit(request, IpUtils.getIp(httpRequest),
                httpRequest.getHeader("User-Agent")));
    }
}
