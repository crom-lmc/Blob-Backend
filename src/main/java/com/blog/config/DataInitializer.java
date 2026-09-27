package com.blog.config;

import com.blog.module.article.dto.ArticleSaveRequest;
import com.blog.module.article.mapper.ArticleMapper;
import com.blog.module.article.mapper.CategoryMapper;
import com.blog.module.article.service.ArticleService;
import com.blog.module.article.entity.Article;
import com.blog.module.article.entity.Category;
import com.blog.module.theme.entity.Theme;
import com.blog.module.theme.mapper.ThemeMapper;
import com.blog.module.theme.service.ThemeService;
import com.blog.module.user.entity.User;
import com.blog.module.user.mapper.UserMapper;
import com.blog.module.user.service.UserService;
import com.blog.util.SensitiveWordUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 首次启动数据初始化：管理员账号、默认主题、默认分类、示例文章。
 * 每一步都幂等且互不阻塞，任意一步失败不影响应用启动。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final UserMapper userMapper;
    private final UserService userService;
    private final ThemeMapper themeMapper;
    private final ThemeService themeService;
    private final CategoryMapper categoryMapper;
    private final ArticleMapper articleMapper;
    private final ArticleService articleService;
    private final BlogProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        SensitiveWordUtils.init(properties.getComment().getSensitiveWords());
        initAdmin();
        initTheme();
        initCategory();
        initWelcomeArticle();
    }

    /**
     * 初始化管理员：admin / admin123（首次部署后请立即修改密码）。
     */
    private void initAdmin() {
        try {
            Long count = userMapper.selectCount(null);
            if (count != null && count > 0) {
                return;
            }
            User user = new User();
            user.setUsername("admin");
            user.setNickname("管理员");
            user.setRole("admin");
            user.setStatus(1);
            user.setEmail("admin@example.com");
            userService.create(user, "admin123");
            log.info("已初始化管理员账号：admin / admin123，请登录后立即修改密码");
        } catch (Exception e) {
            log.warn("初始化管理员失败（可忽略）：{}", e.getMessage());
        }
    }

    /**
     * 初始化默认主题（内置 default-theme.json）。
     */
    private void initTheme() {
        try {
            Long count = themeMapper.selectCount(null);
            if (count != null && count > 0) {
                return;
            }
            Theme theme = new Theme();
            theme.setName("默认主题");
            theme.setDescription("系统内置默认主题（靛蓝主色，支持深色模式）");
            theme.setConfigJson(themeService.writeConfig(themeService.defaultConfig()));
            theme.setIsActive(1);
            theme.setIsBuiltin(1);
            theme.setVersion(1);
            themeMapper.insert(theme);
            log.info("已初始化默认主题（内置，不可删除）");
        } catch (Exception e) {
            log.warn("初始化默认主题失败（可忽略）：{}", e.getMessage());
        }
    }

    private void initCategory() {
        try {
            Long count = categoryMapper.selectCount(null);
            if (count != null && count > 0) {
                return;
            }
            Category category = new Category();
            category.setName("未分类");
            category.setSlug("uncategorized");
            category.setDescription("默认分类");
            category.setParentId(0L);
            category.setSort(0);
            category.setArticleCount(0);
            categoryMapper.insert(category);
        } catch (Exception e) {
            log.warn("初始化默认分类失败（可忽略）：{}", e.getMessage());
        }
    }

    /**
     * 初始化一篇示例文章，覆盖代码、表格、公式等渲染场景。
     */
    private void initWelcomeArticle() {
        try {
            Long count = articleMapper.selectCount(null);
            if (count != null && count > 0) {
                return;
            }
            Category category = categoryMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Category>()
                            .last("LIMIT 1"));
            String markdown = """
                    # 欢迎使用博客系统

                    这是一篇**示例文章**，用于验证 Markdown 渲染效果。

                    ## 代码高亮

                    ```java
                    public class Hello {
                        public static void main(String[] args) {
                            System.out.println("Hello Blog!");
                        }
                    }
                    ```

                    ## 表格

                    | 能力 | 说明 |
                    | --- | --- |
                    | 动态主题 | 后台可视化编辑，实时生效 |
                    | Markdown | 后端渲染并落库，统一 XSS 过滤 |
                    | 搜索 | LIKE + 应用层分词，热词缓存 |

                    ## 数学公式

                    行内公式 \\(E = mc^2\\)，块级公式：

                    $$\\int_0^1 x^2 dx = \\frac{1}{3}$$

                    ## 任务列表

                    - [x] 搭建后端骨架
                    - [ ] 接入对象存储

                    > 在后台「主题编辑器」中修改主色并发布，刷新前台即可看到变化。
                    """;
            ArticleSaveRequest request = new ArticleSaveRequest();
            request.setTitle("欢迎使用博客系统");
            request.setSlug("welcome");
            request.setSummary("一篇用于验证 Markdown 渲染、代码高亮与数学公式的示例文章。");
            request.setContentMd(markdown);
            request.setStatus("published");
            request.setType("article");
            request.setCategoryId(category == null ? null : category.getId());
            request.setTagNames(List.of("示例", "指南"));
            Long id = articleService.save(request);
            log.info("已初始化示例文章，id={}", id);
        } catch (Exception e) {
            log.warn("初始化示例文章失败（可忽略）：{}", e.getMessage());
        }
    }
}
