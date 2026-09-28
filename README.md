# 博客系统后端（blog-server）

Java 17 + Spring Boot 3.2 + Spring Security(JWT) + MyBatis-Plus + MySQL 5.7 + Redis 实现的博客后端，
包含**前台公开接口**与**后台管理接口**，核心能力是**可视化动态主题**：所有颜色 / 字体 / 圆角 / 间距 / 布局 /
深色模式 / 自定义 CSS 存于数据库，后台可视化编辑 → 一键发布 → 前台运行时拉取并注入 CSS 变量生效，**不重新打包、不改代码**。

## 一、快速启动

### 1. 准备数据库

```bash
mysql -uroot -p < src/main/resources/db/schema.sql
```

脚本会创建 `blog` 库（utf8mb4_general_ci）与全部 `t_*` 表，并预置站点设置，可重复执行。

> 注意：本项目 `spring.sql.init.mode=never`，`schema.sql` **不会**随应用自动执行，需手动导入。已有表结构后，
> 如需启用搜索升级 / 主题内置保护等新特性，请再执行 `scripts/` 下两个**幂等**迁移脚本（可重复运行）：
> - `scripts/tmp_search_ddl.sql`：为 `t_article` 增加 `search_text` 列与 ngram 全文索引，并回填存量数据。
> - `scripts/tmp_theme_builtin.sql`：为 `t_theme` 增加 `is_builtin` 列，并将「默认主题」标记为内置（不可删除）。
>
> `schema.sql` 末尾已内置媒体目录初始化（幂等，重跑安全）：创建「博客logo」一级目录，并将库中
> `folder_id IS NULL` 的已有媒体归入该目录（含 `folder` 文本字段同步）。已有媒体库文件会自动出现在该目录下。

### 2. 修改配置

环境相关配置（数据库、Redis、JWT 密钥、存储目录、站点域名）**不写在代码里**，统一放在
Nacos 配置中心，详见「七、Nacos 配置中心」。首次使用请先在 Nacos 的 `blog-local` /
`blog-prod` 命名空间创建 `blog-server.yaml`：

```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    # 说明：mysql-connector-j 8.x 不接受 characterEncoding=utf8mb4（Java 无此编码名），
    #      使用 UTF-8 时驱动会自动映射为 MySQL 的 utf8mb4 字符集，效果等价
    url: jdbc:mysql://127.0.0.1:3306/blog?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8&allowPublicKeyRetrieval=true
    username: root
    password: 123456
  data:
    redis:
      host: 127.0.0.1
      port: 6379
blog:
  jwt:
    secret: 至少 32 字节的随机字符串
  upload:
    root: ./uploads
  site:
    base-url: http://localhost:8080
```

`src/main/resources/application.yml` 只保留各环境通用的配置（端口、Jackson、MyBatis、
限流、评论策略、检索模式等），默认已激活 `local` profile。

### 3. 启动

```bash
mvn spring-boot:run
# 或
mvn clean package && java -jar target/blog-server.jar
```

### 4. 首次登录

应用启动后自动初始化（幂等）：

- 管理员账号：`admin` / `admin123`（**请登录后立即修改密码**）
- 默认主题「默认主题」（内置 `theme/default-theme.json`）
- 默认分类「未分类」与一篇示例文章

接口文档：<http://localhost:8080/swagger-ui.html>

## 二、工程结构

```
com.blog
├── common/       统一响应 R、PageResult、ErrorCode、BusinessException、GlobalExceptionHandler
│   ├── log/      @OpLog 注解 + 切面（后台操作审计）
│   ├── ratelimit RateLimiter + 拦截器（IP 维度限流）
│   └── redis/    RedisService（不可用时自动降级为空操作）
├── config/       SecurityConfig、MybatisPlusConfig、RedisConfig、WebMvcConfig、OpenApiConfig、
│                 BlogProperties、DataInitializer
├── security/     JwtUtil、JwtAuthenticationFilter、UserDetailsServiceImpl、SecurityUser
├── storage/      StorageService 接口 + LocalStorageService（预留 OSS / MinIO 实现）
├── util/         MarkdownUtils、CssUtils、SlugUtils、SensitiveWordUtils、IpUtils
└── module/
    ├── auth/     登录 / 登出 / 验证码 / 当前用户
    ├── user/     用户管理（admin 专用）
    ├── article/  文章、分类、标签、归档、搜索、站点地图
    ├── comment/  评论提交与审核
    ├── media/    媒体库（普通上传 + 分片上传）
    ├── theme/    【核心】主题配置与 CSS 变量生成
    ├── setting/  站点设置（KV）
    ├── stat/     仪表盘统计
    └── log/      操作日志
```

## 三、接口一览

### 公开接口 `/api/public/**`（无需鉴权，Redis 缓存 + 限流）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/theme/active` | 当前生效主题 `{ version, tokens, css, layout }` |
| GET | `/theme/active.css` | 主题 CSS 文本，可直接 `<link>` 引入 |
| GET | `/theme/preview?token=` | 预览主题（后台编辑器 iframe 用，15 分钟有效） |
| GET | `/settings` | 站点设置聚合 |
| GET | `/bootstrap` | 首屏聚合：主题 + 站点设置 |
| GET | `/articles` | 分页列表（keyword / categoryId / tagId / sort / page / size） |
| GET | `/articles/{idOrSlug}` | 详情（上下篇、相关阅读、TOC），阅读数 +1 |
| POST | `/articles/{idOrSlug}/like` | 点赞 |
| GET | `/articles/{idOrSlug}/comments` | 文章评论（含一层回复） |
| POST | `/comments` | 提交评论（频率限制 + 敏感词 + 默认待审核） |
| GET | `/categories` | 分类树 + 文章数 |
| GET | `/categories/{slug}/articles` | 分类下文章 |
| GET | `/tags` | 标签云 + 文章数 |
| GET | `/tags/{slug}/articles` | 标签下文章 |
| GET | `/archives` | 年月归档聚合 |
| GET | `/pages/{slug}` | 自定义页面 |
| GET | `/search?q=` | 全文检索（ngram 全文索引，`blog.search.mode=fulltext` 默认开启；结果含 `highlight` 高亮字段） |
| GET | `/sitemap.xml` | 站点地图 |

### 管理接口 `/api/admin/**`（JWT：admin 全权限，author 仅文章 / 媒体 / 评论）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/auth/captcha` | 图形验证码 |
| POST | `/auth/login` | 登录（连续失败 5 次锁定 10 分钟） |
| POST | `/auth/logout` | 登出 |
| GET | `/auth/profile` | 当前用户 |
| GET | `/dashboard/stats` | 仪表盘（统计卡片 + 近 30 天趋势 + TOP10 + 待审评论） |
| CRUD | `/articles` | 文章管理，另含 `/{id}/publish`、`/{id}/top`、`/batch/delete`、`/{id}/export`、`/import` |
| CRUD | `/categories` | 分类（`PUT /sort` 拖拽排序） |
| CRUD | `/tags` | 标签（`POST /merge` 合并） |
| GET/PUT/DELETE | `/comments` | 审核、拒绝、回复、批量删除 |
| CRUD | `/media` | 上传（`/upload`、`/upload/chunk/*` 分片）、目录、删除 |
| CRUD | `/users` | 用户管理（admin 专属） |
| GET/PUT | `/settings` | 站点设置批量保存 |
| CRUD | `/themes` | 主题列表（含 `isBuiltin` 标识）/ 复制 / 删除（内置默认主题不可删，返回 `2008`）/ 导入导出 |
| POST | `/themes/{id}/activate` | 启用主题（刷新缓存，version +1） |
| PUT | `/themes/{id}/config` | 保存主题配置（草稿态） |
| POST | `/themes/{id}/publish` | 发布上线（覆盖线上样式，version +1） |
| POST | `/themes/{id}/preview` | 生成一次性预览 token |
| GET | `/themes/presets` | 内置预设主题（默认蓝 / 暗夜 / 极简黑白 / 暖橙 / 森绿） |
| GET | `/logs` | 操作日志 |

## 四、【核心】动态主题配置

### 4.1 数据结构（`t_theme.config_json`，LONGTEXT 存 JSON 字符串）

完整结构见 `src/main/resources/theme/default-theme.json`，顶层分组：

- `color`：primary / primaryHover / primarySubtle / bg / bgSubtle / surface / text / textMuted /
  textInvert / border / link / success / warning / danger
- `font`：familyBody / familyHeading / familyCode / sizeBase / scaleRatio / lineHeight /
  letterSpacing / headingWeight
- `radius`：sm / md / lg / full
- `space`：unit / contentWidth / containerWidth / sectionGap / cardPadding
- `layout`：homeLayout(list|grid|magazine) / sidebar(left|right|none) / cardStyle(flat|elevated|bordered) /
  density(compact|comfortable|spacious) / headerStyle(fixed|static) / coverPosition /
  showToc / showBreadcrumb / showExcerpt / articleMetaOrder[] / postCardFields[]
- `homeBlocks[]`：{ type: hero|featured|latest|tagCloud|newsletter, enabled, order, props }
- `dark`：enabled / defaultMode(light|dark|system) / tokens{ bg, surface, text, textMuted, border }
- `customCss`、`customHeadHtml`

### 4.2 Token → CSS 变量映射（前端唯一约定，必须严格遵守）

`/api/public/theme/active` 除返回 `tokens` 外，**额外生成一份 `css` 字符串**，前端注入 `<style id="theme-vars">`：

```css
:root{
  --color-primary: #4f46e5;
  --color-bg: #ffffff;
  --color-text: #1f2328;
  --font-body: system-ui, ...;
  --font-code: 'JetBrains Mono', ...;
  --font-size-base: 16px;
  --line-height: 1.75;
  --radius-md: 10px;
  --space-unit: 4px;
  --content-width: 760px;
  --container-width: 1200px;
  --font-size-h1: 39.06px;  /* sizeBase × scaleRatio^4 */
  ...
}
[data-theme="dark"]{
  --color-bg: #12141a;
  --color-surface: #1b1e26;
  --color-text: #e6e8ee;
}
/* ===== 自定义 CSS ===== */
```

规则：

- 前台组件样式**只允许引用 CSS 变量**，禁止硬编码颜色 / 字号 / 圆角。
- 标题字号由 `sizeBase × scaleRatio^n` 生成 `--font-size-h1 ~ --font-size-h6`。
- 密度 `density` 通过覆盖 `--space-unit` 实现：compact=2 / comfortable=4 / spacious=6。
- `customCss` 追加在变量之后（优先级最高），保存时做安全校验：禁止 `@import` 外链、`javascript:`、
  `<script`、`expression()` 等。

### 4.3 前台加载流程（首屏不闪烁）

1. `index.html` 内联脚本先读 `localStorage.themeCache`，立即生成 `<style id="theme-vars">`，
   并按 `dark.defaultMode` 设置 `<html data-theme>`。
2. Vue 挂载后请求 `/api/public/theme/active`，对比 `version`：不一致则替换 style 内容并写回 localStorage，
   一致则跳过（首屏零闪烁）。
3. 深浅色切换只改 `document.documentElement.dataset.theme` 并持久化到 localStorage。

### 4.4 主题编辑器联动

1. 后台 `POST /api/admin/themes/{id}/preview` 生成 15 分钟有效的 token。
2. 预览 iframe 加载 `/preview?token=xxx`（真实前台页面）。
3. 编辑器通过 `postMessage` 下发 tokens，或前台用 token 调 `/api/public/theme/preview` 拉取 CSS，
   直接 `document.documentElement.style.setProperty(...)` 覆盖，**输入即生效，无需刷新**。
4. 满意后点「发布上线」→ `POST /api/admin/themes/{id}/publish` → 落库 + version+1 + `DEL theme:active`。

## 五、关键实现说明

- **MySQL 5.7 兼容**：JSON 一律用 `LONGTEXT` 存储、应用层 Jackson 处理；不使用窗口函数 / CTE /
  `JSON_TABLE` / `CHECK` 约束 / 生成列。

- **搜索（双模式可切换）**：`blog.search.mode` 取 `fulltext`（默认）或 `like`。`fulltext` 模式基于
  `t_article.search_text` 列 + ngram 全文索引（`ngram_token_size=2`）做中文全文检索，按相关度降序返回，
  且结果 `ArticleListVO` 携带 `highlight` 字段（命中词已用 `<mark>` 包裹并经 XSS 过滤）；`like` 为旧逻辑回退。
  详见 `SEARCH_BACKEND.md` / `SEARCH_UPGRADE.md`。
- **深分页优化**：列表查询先在主键上 `LIMIT offset,size` 取 id，再回表取全字段（见
  `mapper/ArticleMapper.xml`），并限制单页最大 100 条。
- **阅读数**：详情请求写 Redis `article:view:{id}`，`ViewCountFlushTask` 每 5 分钟批量落库；
  Redis 不可用时降级为直接更新数据库。
- **Markdown**：后端用 flexmark 渲染并落库 `content_html`，jsoup 白名单过滤 XSS；
  公式 `$$...$$` / `$...$` 保留原始 LaTeX，输出为 `.math-block` / `.math-inline`，由前端 KaTeX 渲染；
  同时生成标题锚点与 TOC。
- **安全**：BCrypt 存储密码；JWT 无状态鉴权；登录失败 5 次锁定 10 分钟；公开接口 IP 维度限流；
  评论频率限制 + 敏感词过滤 + 默认待审核；上传扩展名白名单 + 大小限制 + 重命名存储 + 路径穿越防护。
- **降级**：Redis、验证码、预览令牌在 Redis 不可用时均有本地兜底，保证单机也能完整跑通。

## 六、扩展方式

- **替换存储**：实现 `com.blog.storage.StorageService`（如 OSS / MinIO），标注 `@Service` 并移除
  `LocalStorageService` 的 `@Service` 即可，业务代码无需改动。
- **新增主题预设**：在 `ThemePresets.presets()` 中追加一个 `preset(...)`，基于默认配置覆盖颜色 / 深色
  token / 布局参数即可。
- **新增主题配置项**：在 `ThemeConfig` 对应分组加字段 → 在 `ThemeCssRenderer` 中补充变量映射 →
  后台属性面板增加控件，无需改表结构（`config_json` 为 JSON 字符串）。

## 七、Nacos 配置中心（本地 / 线上双环境）

敏感配置（数据库连接、Redis、JWT 密钥）放在 Nacos，由 Nacos 鉴权保护，**不进入代码仓库**。
两套环境通过 **命名空间** 隔离，DataId 都是 `blog-server.yaml`（group `DEFAULT_GROUP`）。

| 环境 | profile | Nacos 命名空间（名称 → ID） | 数据源 |
| --- | --- | --- | --- |
| 本地测试 | `local` | blog-local → `486dbba6-76a7-440e-b2fc-02f5edf0e951` | `127.0.0.1:3306/blob` |
| 线上生产 | `prod` | blog-prod → `504c8082-ba22-4c55-a595-3ff943687c57` | `120.53.9.119:3306/blob` |

> 注意：客户端配置 namespace 时用的是 **ID**，不是名称；两个环境的 JWT 密钥相互独立。

**依赖**：`spring-cloud-dependencies:2023.0.1` + `spring-cloud-alibaba-dependencies:2023.0.1.0`
（适配 Spring Boot 3.2.5），实际引入 `spring-cloud-starter-alibaba-nacos-config`。

`application.yml` 中已设置默认 `spring.profiles.active: local`，因此：

```bash
# 本地测试环境：默认就是 local，无需额外参数
export NACOS_PASSWORD=你的Nacos密码
mvn spring-boot:run

# 线上环境：用环境变量（或命令行）覆盖，代码不用改
export SPRING_PROFILES_ACTIVE=prod
export NACOS_PASSWORD=你的Nacos密码
java -jar blog-server.jar
# 等价写法：java -jar blog-server.jar --spring.profiles.active=prod
```

优先级：**命令行参数 > 环境变量 `SPRING_PROFILES_ACTIVE` > `application.yml` 的默认值**。
所以部署机上只要设 `SPRING_PROFILES_ACTIVE=prod` 就会走线上环境。

完全离线调试（不连 Nacos，只用 `application.yml` 里的本地库配置）：

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--spring.profiles.active=default
```

`blog-server.yaml` 至少需包含：`spring.datasource.*`、`spring.data.redis.*`、`blog.jwt.secret`
（可按需加 `server.port`、`blog.site.base-url`、`blog.upload.root` 等）。

> 通过 Open API 发布配置时，命名空间参数名是 **`tenant`**，不是 `namespaceId`
> （`namespaceId` 只用于 `/v1/console/namespaces` 控制台接口），写错会发布到 public。

> Nacos 2.x 端口：客户端需放行 `8848`（HTTP）与 `9848`（gRPC）；`9849`、`7848` 仅集群模式需要。
> 若只开放 8848，nacos-client 2.x 会一直报 `Client not connected`。
