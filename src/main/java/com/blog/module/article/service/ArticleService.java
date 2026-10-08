package com.blog.module.article.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.common.BusinessException;
import com.blog.common.ErrorCode;
import com.blog.common.PageResult;
import com.blog.common.redis.RedisService;
import com.blog.module.article.dto.*;
import com.blog.module.article.entity.Article;
import com.blog.module.article.entity.ArticleTag;
import com.blog.module.article.entity.Category;
import com.blog.module.article.mapper.ArticleMapper;
import com.blog.module.article.mapper.ArticleTagMapper;
import com.blog.module.article.mapper.CategoryMapper;
import com.blog.module.comment.entity.Comment;
import com.blog.module.comment.mapper.CommentMapper;
import com.blog.util.MarkdownUtils;
import com.blog.util.SearchSupport;
import com.blog.util.SlugUtils;
import com.blog.config.BlogProperties;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.util.concurrent.ConcurrentHashMap;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文章服务：查询、详情、保存、发布、置顶、导入导出、阅读数统计。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleService {

    /** 阅读数在 Redis 中累计的 key 前缀，定时任务批量落库 */
    private static final String VIEW_KEY_PREFIX = "article:view:";
    /** 热词搜索结果缓存 */
    private static final String SEARCH_CACHE_PREFIX = "search:hot:";

    private static final Pattern FRONT_MATTER = Pattern.compile("^---\\s*\\R([\\s\\S]*?)\\R---\\s*\\R?(.*)$",
            Pattern.MULTILINE);

    private final ArticleMapper articleMapper;
    private final ArticleTagMapper articleTagMapper;
    private final CategoryMapper categoryMapper;
    private final CommentMapper commentMapper;
    private final TagService tagService;
    private final RedisService redisService;
    private final BlogProperties blogProperties;

    /** 进程内搜索结果缓存（替代 Redis 热词缓存，零中间件依赖） */
    private final SearchCache searchCache = new SearchCache();

    // ---------------- 查询 ----------------

    /**
     * 前台文章分页列表（仅已发布）。
     */
    public PageResult<ArticleListVO> queryPublic(ArticleQuery query) {
        query.setStatus("published");
        if (query.getType() == null || query.getType().isBlank()) {
            query.setType("article");
        }
        expandCategory(query);
        return query(query);
    }

    /**
     * 分类为两级结构：按父分类查询时纳入其全部子分类的文章。
     */
    private void expandCategory(ArticleQuery query) {
        Long categoryId = query.getCategoryId();
        if (categoryId == null) {
            return;
        }
        List<Long> ids = new ArrayList<>();
        ids.add(categoryId);
        List<Category> children = categoryMapper.selectList(
                new LambdaQueryWrapper<Category>().eq(Category::getParentId, categoryId));
        for (Category child : children) {
            ids.add(child.getId());
        }
        query.setCategoryId(null);
        query.setCategoryIds(ids);
    }

    /**
     * 通用分页查询（先 id 分页再回表，缓解深分页）。
     */
    public PageResult<ArticleListVO> query(ArticleQuery query) {
        query.applySort();
        long offset = query.offset();
        long size = query.limit();
        long total = articleMapper.countByCondition(query);
        if (total == 0) {
            return PageResult.empty(query.getPage(), size);
        }
        List<Article> rows = articleMapper.selectPageByCondition(query, offset, size);
        List<ArticleListVO> voList = toListVOs(rows);
        attachHighlight(voList, rows, query.getKeyword());
        return new PageResult<>(voList, total, query.getPage(), size);
    }

    /**
     * 后台文章分页列表（全部状态）。
     * 与前台一致：按父分类筛选时纳入其全部子分类的文章。
     */
    public PageResult<ArticleListVO> queryAdmin(ArticleQuery query) {
        expandCategory(query);
        return query(query);
    }

    /**
     * 前台入口的文章详情（支持 id 或 slug）：仅允许访问「已发布」文章。
     * 草稿 / 私有文章不允许通过前台链接直接查看。
     */
    public ArticleDetailVO detail(String idOrSlug) {
        Article article = resolvePublished(idOrSlug);
        return detail(article, true);
    }

    public ArticleDetailVO detail(Article article, boolean withRelated) {
        ArticleDetailVO vo = new ArticleDetailVO();
        fillListVO(vo, article, categoryOf(article), tagsOf(Collections.singletonList(article.getId())).get(article.getId()));
        vo.setContentHtml(article.getContentHtml());
        vo.setContentMd(article.getContentMd());
        // 目录直接基于已落库的 content_html 抽取，避免详情页重复渲染 Markdown
        vo.setToc(MarkdownUtils.buildTocFromHtml(article.getContentHtml()));
        vo.setViewCount(article.getViewCount() + (int) pendingViewCount(article.getId()));
        if (withRelated && "published".equals(article.getStatus())) {
            vo.setPrev(toSimpleVO(articleMapper.selectPrev(article.getPublishedAt())));
            vo.setNext(toSimpleVO(articleMapper.selectNext(article.getPublishedAt())));
            vo.setRelated(related(article, 5));
        }
        return vo;
    }

    public Article getById(Long id) {
        Article article = articleMapper.selectById(id);
        if (article == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "文章不存在");
        }
        return article;
    }

    /**
     * 支持 id 或 slug 定位文章。
     */
    public Article resolve(String idOrSlug) {
        if (!StringUtils.hasText(idOrSlug)) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "文章不存在");
        }
        Article article;
        if (idOrSlug.matches("\\d+")) {
            article = articleMapper.selectById(Long.parseLong(idOrSlug));
        } else {
            article = articleMapper.selectOne(new LambdaQueryWrapper<Article>()
                    .eq(Article::getSlug, idOrSlug).last("LIMIT 1"));
        }
        if (article == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "文章不存在");
        }
        return article;
    }

    /**
     * 前台入口专用：按 id 或 slug 定位文章，且必须是「已发布」状态。
     *
     * <p>用于文章详情、自定义页面、点赞、评论列表等所有公开读取路径，
     * 避免草稿 / 私有文章被直接拼链接访问。后台操作请继续使用 {@link #resolve(String)} 或
     * {@link #getById(Long)}，它们不做状态限制。
     *
     * @throws BusinessException 文章不存在，或状态不是 published
     */
    public Article resolvePublished(String idOrSlug) {
        Article article = resolve(idOrSlug);
        if (!"published".equals(article.getStatus())) {
            throw new BusinessException(ErrorCode.ARTICLE_NOT_PUBLISHED);
        }
        return article;
    }

    /**
     * 归档：按年月分组。
     */
    public List<ArchiveGroupVO> archives() {
        List<ArchiveGroupVO> groups = articleMapper.selectArchiveGroups();
        for (ArchiveGroupVO group : groups) {
            group.setYear(Integer.parseInt(group.getMonth().substring(0, 4)));
            group.setItems(articleMapper.selectArchiveItems(group.getMonth()));
        }
        return groups;
    }

    /**
     * 全文检索：
     * - fulltext 模式（默认）：MySQL InnoDB ngram 全文索引 + 进程内分词改写，按相关度排序，命中高亮；
     * - like 模式：原 LIKE 逻辑，作为灰度回退。
     * 全程不依赖任何外部中间件（无 ES / 无 Redis 检索缓存）。
     */
    public PageResult<ArticleListVO> search(String keyword, long page, long size) {
        if (!StringUtils.hasText(keyword)) {
            return PageResult.empty(page, size);
        }
        String key = keyword.trim();

        if (!"fulltext".equalsIgnoreCase(blogProperties.getSearch().getMode())) {
            return searchByLike(key, page, size);
        }

        String booleanQuery = SearchSupport.toBooleanQuery(key);
        if (booleanQuery.isEmpty()) {
            return PageResult.empty(page, size);
        }

        // 进程内缓存：同一布尔查询 10 分钟内复用
        String cacheKey = "ft:" + booleanQuery;
        PageResult<ArticleListVO> cached = searchCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        long offset = (Math.max(1, page) - 1) * size;
        List<Article> rows = articleMapper.searchByFulltext(booleanQuery, offset, size);
        long total = articleMapper.countByFulltext(booleanQuery);

        // 兜底：ngram 因词太短（如单汉字）整体归零时，回退 LIKE，保证至少能命中标题/摘要
        if (total == 0) {
            return searchByLike(key, page, size);
        }

        List<ArticleListVO> vos = toListVOs(rows);
        attachHighlight(vos, rows, key);

        PageResult<ArticleListVO> result = new PageResult<>(vos, total, page, size);
        searchCache.put(cacheKey, result);
        return result;
    }

    /** 原 LIKE 检索（灰度回退用） */
    private PageResult<ArticleListVO> searchByLike(String key, long page, long size) {
        ArticleQuery query = new ArticleQuery();
        query.setStatus("published");
        query.setKeyword(key);
        query.setPage(page);
        query.setSize(size);
        query.setSort("latest");
        return query(query);
    }

    /**
     * 站点地图使用的已发布文章（文章 + 自定义页面）。
     */
    public List<Article> listPublishedForSitemap() {
        return articleMapper.selectList(new LambdaQueryWrapper<Article>()
                .eq(Article::getStatus, "published")
                .orderByDesc(Article::getPublishedAt));
    }

    // ---------------- 写操作 ----------------

    /**
     * 新增或更新文章：Markdown 在后端渲染并落库。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long save(ArticleSaveRequest request) {
        Article article = request.getId() == null ? new Article() : getById(request.getId());
        boolean insert = request.getId() == null;

        article.setTitle(request.getTitle().trim());
        article.setSlug(uniqueSlug(request.getSlug(), article.getTitle(), request.getId()));
        article.setCover(request.getCover());
        article.setContentMd(request.getContentMd() == null ? "" : request.getContentMd());
        article.setCategoryId(request.getCategoryId());
        article.setType(request.getType() == null || request.getType().isBlank() ? "article" : request.getType());
        article.setIsTop(request.getIsTop() == null ? 0 : request.getIsTop());
        article.setAllowComment(request.getAllowComment() == null ? 1 : request.getAllowComment());
        article.setStatus(request.getStatus() == null || request.getStatus().isBlank() ? "draft" : request.getStatus());

        // Markdown 渲染（后端完成，前台不再重复渲染）
        MarkdownUtils.RenderResult rendered = MarkdownUtils.render(article.getContentMd());
        article.setContentHtml(rendered.getHtml());
        // 抽取正文纯文本，用于全文检索与高亮
        article.setSearchText(MarkdownUtils.plainText(rendered.getHtml(), 0));

        // 摘要：未填写时截取正文纯文本
        String summary = request.getSummary();
        if (!StringUtils.hasText(summary)) {
            summary = MarkdownUtils.plainText(rendered.getHtml(), 120);
        }
        article.setSummary(summary.length() > 500 ? summary.substring(0, 500) : summary);

        // 字数与阅读时长
        int wordCount = MarkdownUtils.countWords(article.getContentMd());
        article.setWordCount(wordCount);
        article.setReadingTime(MarkdownUtils.readingMinutes(wordCount));

        // 发布时间
        if ("published".equals(article.getStatus())) {
            if (request.getPublishedAt() != null) {
                article.setPublishedAt(request.getPublishedAt());
            } else if (article.getPublishedAt() == null) {
                article.setPublishedAt(LocalDateTime.now());
            }
        } else if (request.getPublishedAt() != null) {
            article.setPublishedAt(request.getPublishedAt());
        }

        if (insert) {
            article.setViewCount(0);
            article.setLikeCount(0);
            article.setCommentCount(0);
            articleMapper.insert(article);
        } else {
            articleMapper.updateById(article);
        }

        // 标签：名称 → ID（不存在自动创建），再全量替换关联
        List<Long> tagIds = new ArrayList<>();
        if (request.getTagIds() != null) {
            tagIds.addAll(request.getTagIds());
        }
        tagIds.addAll(tagService.resolveTagIds(request.getTagNames()));
        replaceTags(article.getId(), tagIds);

        // 维护分类冗余计数
        if (article.getCategoryId() != null) {
            categoryMapper.recount(article.getCategoryId());
        }
        redisService.deleteByPattern(SEARCH_CACHE_PREFIX + "*");
        return article.getId();
    }

    /**
     * 发布 / 下线。
     */
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id, boolean publish) {
        Article article = getById(id);
        Article update = new Article();
        update.setId(id);
        if (publish) {
            update.setStatus("published");
            if (article.getPublishedAt() == null) {
                update.setPublishedAt(LocalDateTime.now());
            }
        } else {
            update.setStatus("draft");
        }
        articleMapper.updateById(update);
        if (article.getCategoryId() != null) {
            categoryMapper.recount(article.getCategoryId());
        }
    }

    /**
     * 置顶 / 取消置顶。
     */
    public void top(Long id, boolean top) {
        getById(id);
        Article update = new Article();
        update.setId(id);
        update.setIsTop(top ? 1 : 0);
        articleMapper.updateById(update);
    }

    /**
     * 批量删除（同时删除标签关联与评论）。
     *
     * <p>已发布的文章<b>不允许删除</b>——单条删除与批量删除走同一条路径，规则一致：
     * 需要先「下线」转为草稿，再执行删除，避免线上正在被访问的文章被直接抹掉。
     *
     * @throws BusinessException 待删除的文章中存在已发布状态的（{@link ErrorCode#CANNOT_DELETE_PUBLISHED_ARTICLE}）
     */
    @Transactional(rollbackFor = Exception.class)
    public int delete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }

        // 已发布校验：只要命中一篇就整批拒绝，避免「删一半留一半」
        Long published = articleMapper.selectCount(new LambdaQueryWrapper<Article>()
                .in(Article::getId, ids)
                .eq(Article::getStatus, "published"));
        if (published != null && published > 0) {
            throw new BusinessException(ErrorCode.CANNOT_DELETE_PUBLISHED_ARTICLE);
        }

        int count = 0;
        for (Long id : ids) {
            Article article = articleMapper.selectById(id);
            if (article == null) {
                continue;
            }
            articleTagMapper.delete(new LambdaQueryWrapper<ArticleTag>().eq(ArticleTag::getArticleId, id));
            commentMapper.delete(new LambdaQueryWrapper<Comment>().eq(Comment::getArticleId, id));
            articleMapper.deleteById(id);
            if (article.getCategoryId() != null) {
                categoryMapper.recount(article.getCategoryId());
            }
            count++;
        }
        return count;
    }

    /**
     * 导出 Markdown（含 YAML Front Matter）。
     */
    public String exportMarkdown(Long id) {
        Article article = getById(id);
        StringBuilder sb = new StringBuilder();
        sb.append("---\n");
        sb.append("title: ").append(article.getTitle()).append('\n');
        sb.append("slug: ").append(article.getSlug()).append('\n');
        sb.append("status: ").append(article.getStatus()).append('\n');
        sb.append("type: ").append(article.getType()).append('\n');
        if (article.getCategoryId() != null) {
            Category category = categoryMapper.selectById(article.getCategoryId());
            if (category != null) {
                sb.append("category: ").append(category.getName()).append('\n');
            }
        }
        List<TagRelation> relations = articleTagMapper.selectTagsByArticleIds(Collections.singletonList(id));
        if (!relations.isEmpty()) {
            sb.append("tags: [");
            for (int i = 0; i < relations.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(relations.get(i).getName());
            }
            sb.append("]\n");
        }
        sb.append("date: ").append(article.getPublishedAt() == null ? "" : article.getPublishedAt()).append('\n');
        sb.append("---\n\n");
        sb.append(article.getContentMd() == null ? "" : article.getContentMd());
        return sb.toString();
    }

    /**
     * 导入 Markdown（自动识别 Front Matter）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long importMarkdown(String content, String defaultStatus) {
        if (!StringUtils.hasText(content)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文件内容为空");
        }
        ArticleSaveRequest request = new ArticleSaveRequest();
        String body = content;
        Matcher matcher = FRONT_MATTER.matcher(content);
        if (matcher.find()) {
            String meta = matcher.group(1);
            body = matcher.group(2);
            for (String line : meta.split("\\R")) {
                int index = line.indexOf(':');
                if (index <= 0) {
                    continue;
                }
                String key = line.substring(0, index).trim().toLowerCase();
                String value = line.substring(index + 1).trim();
                switch (key) {
                    case "title" -> request.setTitle(value);
                    case "slug" -> request.setSlug(value);
                    case "status" -> request.setStatus(value);
                    case "type" -> request.setType(value);
                    case "category" -> {
                        if (StringUtils.hasText(value)) {
                            Category category = categoryMapper.selectOne(new LambdaQueryWrapper<Category>()
                                    .eq(Category::getName, value).last("LIMIT 1"));
                            if (category == null) {
                                Category created = new Category();
                                created.setName(value);
                                created.setSlug(SlugUtils.slugify(value));
                                created.setParentId(0L);
                                created.setSort(0);
                                created.setArticleCount(0);
                                categoryMapper.insert(created);
                                category = created;
                            }
                            request.setCategoryId(category.getId());
                        }
                    }
                    case "tags" -> {
                        String trimmed = value.replaceAll("^\\[|]$", "");
                        for (String name : trimmed.split("[,，]")) {
                            if (StringUtils.hasText(name)) {
                                request.getTagNames().add(name.trim());
                            }
                        }
                    }
                    case "summary", "description" -> request.setSummary(value);
                    case "cover" -> request.setCover(value);
                    default -> {
                        // 其它字段忽略
                    }
                }
            }
        }
        if (!StringUtils.hasText(request.getTitle())) {
            // 无标题时取正文首个标题行
            for (String line : body.split("\\R")) {
                if (line.startsWith("# ")) {
                    request.setTitle(line.substring(2).trim());
                    break;
                }
            }
        }
        if (!StringUtils.hasText(request.getTitle())) {
            request.setTitle("导入文章 " + LocalDateTime.now().toString().substring(0, 19));
        }
        request.setContentMd(body);
        if (!StringUtils.hasText(request.getStatus())) {
            request.setStatus(StringUtils.hasText(defaultStatus) ? defaultStatus : "draft");
        }
        return save(request);
    }

    // ---------------- 阅读数 ----------------

    /**
     * 阅读数 +1：写入 Redis，定时任务批量落库；Redis 不可用时直接落库。
     */
    public void incrementView(Long id) {
        Long value = redisService.increment(VIEW_KEY_PREFIX + id, Duration.ofDays(2));
        if (value == null) {
            articleMapper.updateViewCount(id, 1);
        }
    }

    public long pendingViewCount(Long id) {
        String value = redisService.get(VIEW_KEY_PREFIX + id);
        if (value == null) {
            return 0;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 将 Redis 中的阅读数增量批量落库（每 5 分钟执行一次）。
     */
    public int flushViewCounts() {
        Set<String> keys = redisService.scan(VIEW_KEY_PREFIX + "*");
        int count = 0;
        for (String key : keys) {
            String value = redisService.get(key);
            redisService.delete(key);
            if (value == null) {
                continue;
            }
            long delta;
            try {
                delta = Long.parseLong(value);
            } catch (NumberFormatException e) {
                continue;
            }
            if (delta <= 0) {
                continue;
            }
            try {
                long id = Long.parseLong(key.substring(VIEW_KEY_PREFIX.length()));
                articleMapper.updateViewCount(id, delta);
                count++;
            } catch (NumberFormatException e) {
                log.warn("阅读数缓存 key 异常：{}", key);
            }
        }
        if (count > 0) {
            log.info("阅读数落库完成，共 {} 篇", count);
        }
        return count;
    }

    /**
     * 仪表盘：阅读量 TOP N。
     */
    public List<ArticleListVO> topArticles(int limit) {
        List<Article> rows = articleMapper.selectList(new LambdaQueryWrapper<Article>()
                .eq(Article::getStatus, "published")
                .orderByDesc(Article::getViewCount)
                .last("LIMIT " + Math.max(1, limit)));
        return rows.isEmpty() ? Collections.emptyList() : toListVOs(rows);
    }

    /**
     * 点赞：直接落库（低频写操作，无需走 Redis）。
     */
    public int like(Long id) {
        Article article = getById(id);
        Article update = new Article();
        update.setId(id);
        int likeCount = nullSafe(article.getLikeCount()) + 1;
        update.setLikeCount(likeCount);
        articleMapper.updateById(update);
        return likeCount;
    }

    /**
     * 评论数变更（评论审核、删除时调用）。
     */
    public void refreshCommentCount(Long articleId) {
        Long count = commentMapper.selectCount(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getArticleId, articleId)
                .eq(Comment::getStatus, "approved"));
        Article update = new Article();
        update.setId(articleId);
        update.setCommentCount(count == null ? 0 : count.intValue());
        articleMapper.updateById(update);
    }

    /**
     * 重建检索纯文本：遍历所有文章，从已渲染 HTML 抽取纯文本写入 search_text。
     * 用于存量数据初始化或索引重建（建 ngram 全文索引后调用一次即可）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void rebuildSearchText() {
        List<Article> all = articleMapper.selectList(null);
        for (Article a : all) {
            if (a.getContentHtml() == null) {
                continue;
            }
            String text = MarkdownUtils.plainText(a.getContentHtml(), 0);
            articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                    .eq(Article::getId, a.getId())
                    .set(Article::getSearchText, text));
        }
    }

    // ---------------- 私有方法 ----------------

    private void replaceTags(Long articleId, List<Long> tagIds) {
        articleTagMapper.delete(new LambdaQueryWrapper<ArticleTag>().eq(ArticleTag::getArticleId, articleId));
        if (tagIds == null) {
            return;
        }
        for (Long tagId : tagIds) {
            if (tagId != null) {
                articleTagMapper.insertIgnore(articleId, tagId);
            }
        }
    }

    /**
     * 相关阅读：同分类或存在共同标签，按发布时间倒序。
     */
    private List<ArticleListVO> related(Article article, int limit) {
        String tagSql = "SELECT article_id FROM t_article_tag WHERE tag_id IN "
                + "(SELECT tag_id FROM t_article_tag WHERE article_id = " + article.getId() + ")";
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<Article>()
                .eq(Article::getStatus, "published")
                .ne(Article::getId, article.getId())
                .and(w -> {
                    if (article.getCategoryId() != null) {
                        w.eq(Article::getCategoryId, article.getCategoryId()).or();
                    }
                    w.inSql(Article::getId, tagSql);
                })
                .last("LIMIT " + limit);
        List<Article> rows = articleMapper.selectList(wrapper);
        return rows.isEmpty() ? Collections.emptyList() : toListVOs(rows);
    }

    private List<ArticleListVO> toListVOs(List<Article> articles) {
        if (articles.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> ids = articles.stream().map(Article::getId).toList();
        Map<Long, List<TagVO>> tagMap = tagsOf(ids);
        List<ArticleListVO> result = new ArrayList<>(articles.size());
        for (Article article : articles) {
            ArticleListVO vo = new ArticleListVO();
            fillListVO(vo, article, categoryOf(article), tagMap.get(article.getId()));
            result.add(vo);
        }
        return result;
    }

    private ArticleListVO toSimpleVO(Article article) {
        if (article == null) {
            return null;
        }
        ArticleListVO vo = new ArticleListVO();
        vo.setId(article.getId());
        vo.setTitle(article.getTitle());
        vo.setSlug(article.getSlug());
        vo.setCover(article.getCover());
        vo.setSummary(article.getSummary());
        vo.setPublishedAt(article.getPublishedAt());
        return vo;
    }

    private void fillListVO(ArticleListVO vo, Article article, Category category, List<TagVO> tags) {
        vo.setId(article.getId());
        vo.setTitle(article.getTitle());
        vo.setSlug(article.getSlug());
        vo.setSummary(article.getSummary());
        vo.setCover(article.getCover());
        vo.setType(article.getType());
        vo.setStatus(article.getStatus());
        vo.setCategoryId(article.getCategoryId());
        if (category != null) {
            vo.setCategoryName(category.getName());
            vo.setCategorySlug(category.getSlug());
        }
        vo.setTags(tags == null ? new ArrayList<>() : tags);
        vo.setIsTop(article.getIsTop());
        vo.setAllowComment(article.getAllowComment());
        vo.setViewCount(nullSafe(article.getViewCount()));
        vo.setLikeCount(nullSafe(article.getLikeCount()));
        vo.setCommentCount(nullSafe(article.getCommentCount()));
        vo.setWordCount(nullSafe(article.getWordCount()));
        vo.setReadingTime(nullSafe(article.getReadingTime()));
        vo.setPublishedAt(article.getPublishedAt());
        vo.setCreatedAt(article.getCreatedAt());
        vo.setUpdatedAt(article.getUpdatedAt());
    }

    /**
     * 搜索结果附加命中片段与高亮：优先摘要、其次标题、再否则从正文 search_text 截取片段，
     * 命中词统一包裹 &lt;mark&gt;。全量检索与 LIKE 回退分支共用，保证前后端表现一致。
     * 返回的 highlight 仅含 &lt;mark&gt; 与纯文本，可直接 v-html 渲染。
     */
    private void attachHighlight(List<ArticleListVO> voList, List<Article> rows, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return;
        }
        for (int i = 0; i < voList.size(); i++) {
            Article article = rows.get(i);
            voList.get(i).setHighlight(SearchSupport.highlightBest(
                    article.getSummary(), article.getTitle(), article.getSearchText(), keyword));
        }
    }

    private Category categoryOf(Article article) {
        if (article.getCategoryId() == null) {
            return null;
        }
        return categoryMapper.selectById(article.getCategoryId());
    }

    private Map<Long, List<TagVO>> tagsOf(List<Long> articleIds) {
        Map<Long, List<TagVO>> map = new HashMap<>();
        if (articleIds == null || articleIds.isEmpty()) {
            return map;
        }
        List<TagRelation> relations = articleTagMapper.selectTagsByArticleIds(articleIds);
        for (TagRelation relation : relations) {
            TagVO vo = new TagVO();
            vo.setId(relation.getId());
            vo.setName(relation.getName());
            vo.setSlug(relation.getSlug());
            vo.setColor(relation.getColor());
            map.computeIfAbsent(relation.getArticleId(), k -> new ArrayList<>()).add(vo);
        }
        return map;
    }

    /**
     * 生成唯一 slug：重复时追加 -2、-3 ...
     */
    private String uniqueSlug(String input, String title, Long selfId) {
        String base = StringUtils.hasText(input) ? SlugUtils.slugify(input) : SlugUtils.slugify(title);
        String slug = base;
        int suffix = 1;
        while (true) {
            Article exists = articleMapper.selectOne(new LambdaQueryWrapper<Article>()
                    .eq(Article::getSlug, slug).last("LIMIT 1"));
            if (exists == null || (selfId != null && exists.getId().equals(selfId))) {
                return slug;
            }
            suffix++;
            slug = base + "-" + suffix;
        }
    }

    private int nullSafe(Integer value) {
        return value == null ? 0 : value;
    }

    /** 进程内搜索结果缓存（10 分钟 TTL），替代原 Redis 热词缓存，零中间件依赖 */
    private static final class SearchCache {
        private final ConcurrentHashMap<String, CacheValue> store = new ConcurrentHashMap<>();
        private static final long TTL_MS = 10 * 60 * 1000L;

        PageResult<ArticleListVO> get(String key) {
            CacheValue v = store.get(key);
            if (v == null) {
                return null;
            }
            if (System.currentTimeMillis() - v.ts > TTL_MS) {
                store.remove(key);
                return null;
            }
            return v.result;
        }

        void put(String key, PageResult<ArticleListVO> result) {
            store.put(key, new CacheValue(result, System.currentTimeMillis()));
        }

        private static final class CacheValue {
            final PageResult<ArticleListVO> result;
            final long ts;

            CacheValue(PageResult<ArticleListVO> result, long ts) {
                this.result = result;
                this.ts = ts;
            }
        }
    }
}
