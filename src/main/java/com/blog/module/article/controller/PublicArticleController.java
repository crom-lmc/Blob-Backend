package com.blog.module.article.controller;

import com.blog.common.PageResult;
import com.blog.common.R;
import com.blog.config.BlogProperties;
import com.blog.module.article.dto.*;
import com.blog.module.article.service.ArticleService;
import com.blog.module.article.service.CategoryService;
import com.blog.module.article.service.TagService;
import com.blog.module.theme.dto.ThemeActiveVO;
import com.blog.module.theme.service.ThemeService;
import com.blog.module.setting.dto.SiteSettingsVO;
import com.blog.module.setting.service.SettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 前台文章 / 分类 / 标签 / 归档 / 搜索 / 站点地图。
 */
@Slf4j
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
@Tag(name = "前台-内容", description = "文章列表、详情、分类、标签、归档、搜索、站点地图")
public class PublicArticleController {

    private final ArticleService articleService;
    private final CategoryService categoryService;
    private final TagService tagService;
    private final SettingService settingService;
    private final ThemeService themeService;
    private final BlogProperties blogProperties;

    @GetMapping("/articles")
    @Operation(summary = "文章分页列表", description = "支持 keyword / categoryId / tagId / page / size / sort")
    public R<PageResult<ArticleListVO>> articles(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long tagId,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(required = false) Long size) {
        ArticleQuery query = new ArticleQuery();
        query.setKeyword(keyword);
        query.setCategoryId(categoryId);
        query.setTagId(tagId);
        query.setSort(sort);
        query.setType(type);
        query.setPage(page);
        query.setSize(resolvePageSize(size));
        return R.ok(articleService.queryPublic(query));
    }

    @GetMapping("/articles/{idOrSlug}")
    @Operation(summary = "文章详情", description = "含上下篇、相关阅读，并异步累加阅读数")
    public R<ArticleDetailVO> detail(@PathVariable String idOrSlug) {
        ArticleDetailVO vo = articleService.detail(idOrSlug);
        // 阅读数 +1（Redis 累加，定时任务落库）
        try {
            articleService.incrementView(vo.getId());
        } catch (Exception e) {
            log.warn("阅读数累加失败 articleId={}", vo.getId(), e);
        }
        return R.ok(vo);
    }

    @PostMapping("/articles/{idOrSlug}/like")
    @Operation(summary = "文章点赞", description = "点赞数直接落库，返回最新点赞数")
    public R<Integer> like(@PathVariable String idOrSlug) {
        return R.ok(articleService.like(articleService.resolve(idOrSlug).getId()));
    }

    @GetMapping("/categories")
    @Operation(summary = "分类树 + 文章数")
    public R<List<CategoryVO>> categories() {
        return R.ok(categoryService.tree());
    }

    @GetMapping("/categories/{slug}/articles")
    @Operation(summary = "分类下的文章")
    public R<PageResult<ArticleListVO>> categoryArticles(@PathVariable String slug,
                                                         @RequestParam(defaultValue = "1") long page,
                                                         @RequestParam(required = false) Long size) {
        ArticleQuery query = new ArticleQuery();
        query.setCategoryId(categoryService.getBySlug(slug).getId());
        query.setPage(page);
        query.setSize(resolvePageSize(size));
        return R.ok(articleService.queryPublic(query));
    }

    @GetMapping("/tags")
    @Operation(summary = "标签云 + 文章数")
    public R<List<TagVO>> tags() {
        return R.ok(tagService.listWithCount());
    }

    @GetMapping("/tags/{slug}/articles")
    @Operation(summary = "标签下的文章")
    public R<PageResult<ArticleListVO>> tagArticles(@PathVariable String slug,
                                                    @RequestParam(defaultValue = "1") long page,
                                                    @RequestParam(required = false) Long size) {
        ArticleQuery query = new ArticleQuery();
        query.setTagId(tagService.getBySlug(slug).getId());
        query.setPage(page);
        query.setSize(resolvePageSize(size));
        return R.ok(articleService.queryPublic(query));
    }

    @GetMapping("/archives")
    @Operation(summary = "按年月归档聚合")
    public R<List<ArchiveGroupVO>> archives() {
        return R.ok(articleService.archives());
    }

    @GetMapping("/pages/{slug}")
    @Operation(summary = "自定义页面", description = "关于我、友链等 type=page 的内容")
    public R<ArticleDetailVO> page(@PathVariable String slug) {
        return R.ok(articleService.detail(slug));
    }

    @GetMapping("/search")
    @Operation(summary = "全文检索", description = "LIKE + 应用层分词，标题/摘要/正文，热词结果 Redis 缓存")
    public R<PageResult<ArticleListVO>> search(@RequestParam String q,
                                               @RequestParam(defaultValue = "1") long page,
                                               @RequestParam(required = false) Long size) {
        return R.ok(articleService.search(q, page, resolvePageSize(size)));
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    @Operation(summary = "站点地图 sitemap.xml")
    public String sitemap() {
        String baseUrl = blogProperties.getSite().getBaseUrl();
        if (!baseUrl.endsWith("/")) {
            baseUrl = baseUrl + "/";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        sb.append("  <url><loc>").append(baseUrl).append("</loc><priority>1.0</priority></url>\n");
        for (var article : articleService.listPublishedForSitemap()) {
            String loc;
            if ("page".equals(article.getType())) {
                loc = baseUrl + "page/" + article.getSlug();
            } else {
                loc = baseUrl + "posts/" + article.getSlug();
            }
            sb.append("  <url><loc>").append(loc).append("</loc>");
            if (article.getUpdatedAt() != null) {
                sb.append("<lastmod>").append(article.getUpdatedAt().toLocalDate()).append("</lastmod>");
            }
            sb.append("<changefreq>weekly</changefreq><priority>0.8</priority></url>\n");
        }
        sb.append("</urlset>");
        return sb.toString();
    }

    /**
     * 首屏聚合：主题 + 站点设置（前台可一次请求拿到，减少闪烁）。
     */
    @GetMapping("/bootstrap")
    @Operation(summary = "首屏聚合数据", description = "主题 + 站点设置，供前台首屏一次拉取")
    public R<Map<String, Object>> bootstrap() {
        ThemeActiveVO theme = themeService.getActive();
        SiteSettingsVO settings = settingService.aggregate();
        return R.ok(Map.of(
                "theme", theme,
                "settings", settings,
                "serverTime", LocalDateTime.now().toString()));
    }

    /** 解析每页条数：传入且合法则优先使用，否则回退到站点设置 page_size（默认 10） */
    private long resolvePageSize(Long size) {
        return size != null && size > 0 ? size : settingService.getInt("page_size", 10);
    }
}
