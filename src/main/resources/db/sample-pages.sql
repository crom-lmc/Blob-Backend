-- =============================================================
--  示例自定义页面（type = page）：可重复执行（INSERT IGNORE）
--  用法：mysql -uroot -p blog < sample-pages.sql
--  说明：
--   1. 自定义页面与文章共用 `t_article` 表，用 `type = 'page'` 区分；
--   2. 前台导航「关于」硬编码指向 /page/about（见 SiteHeader.vue 的 navItems），
--      因此 slug 必须是 about，否则点击会提示「页面不存在或已下线」；
--   3. content_html 留空，前台 PageView 会用 content_md 兜底渲染 Markdown。
-- =============================================================
USE `blog`;

INSERT IGNORE INTO `t_article`
(`title`, `slug`, `summary`, `content_md`, `status`, `type`, `is_top`, `allow_comment`, `word_count`, `reading_time`, `published_at`)
VALUES
('关于', 'about', '关于本站与作者',
'## 关于我

你好，我是一名 Java 后端工程师，这里记录我的技术笔记与生活片段。

### 我在写什么

- **后端开发**：Spring 全家桶、JVM 调优、并发编程
- **数据存储**：MySQL 索引与事务、Redis 缓存与分布式锁
- **架构设计**：微服务、消息队列、分布式一致性

### 联系我

- GitHub：https://github.com/your-name
- 邮箱：hello@example.com

> 本站基于 Spring Boot + Vue 3 构建，支持可视化主题编辑与实时预览。',
'published', 'page', 0, 1, 200, 1, NOW());
