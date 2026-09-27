-- =============================================================
--  博客系统数据库脚本（MySQL 5.7，utf8mb4_general_ci）
--  可重复执行：建表使用 IF NOT EXISTS，初始数据使用 INSERT IGNORE
-- =============================================================
CREATE DATABASE IF NOT EXISTS `blog` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `blog`;

-- 用户表
CREATE TABLE IF NOT EXISTS `t_user` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`      VARCHAR(64)  NOT NULL                COMMENT '登录名',
    `nickname`      VARCHAR(64)  DEFAULT NULL            COMMENT '昵称',
    `avatar`        VARCHAR(255) DEFAULT NULL            COMMENT '头像',
    `email`         VARCHAR(128) DEFAULT NULL            COMMENT '邮箱',
    `password_hash` VARCHAR(128) NOT NULL                COMMENT 'BCrypt 密码摘要',
    `role`          VARCHAR(16)  NOT NULL DEFAULT 'author' COMMENT '角色：admin/author',
    `status`        TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：1启用 0禁用',
    `last_login_at` DATETIME     DEFAULT NULL            COMMENT '最近登录时间',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_username` (`username`),
    KEY `idx_user_role` (`role`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户';

-- 文章表（content_html 后端渲染后落库，前台不重复渲染）
CREATE TABLE IF NOT EXISTS `t_article` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `title`         VARCHAR(200) NOT NULL                COMMENT '标题',
    `slug`          VARCHAR(200) NOT NULL                COMMENT 'URL 别名，唯一',
    `summary`       VARCHAR(500) DEFAULT NULL            COMMENT '摘要',
    `cover`         VARCHAR(255) DEFAULT NULL            COMMENT '封面图',
    `content_md`    LONGTEXT                             COMMENT 'Markdown 原文',
    `content_html`  LONGTEXT                             COMMENT '渲染后的 HTML（已 XSS 过滤）',
    `search_text`   MEDIUMTEXT    DEFAULT NULL           COMMENT '正文纯文本，用于全文检索与高亮',
    `status`        VARCHAR(16)  NOT NULL DEFAULT 'draft' COMMENT '状态：draft/published/private',
    `category_id`   BIGINT       DEFAULT NULL            COMMENT '分类 ID',
    `type`          VARCHAR(16)  NOT NULL DEFAULT 'article' COMMENT '类型：article/page/note',
    `is_top`        TINYINT      NOT NULL DEFAULT 0      COMMENT '是否置顶',
    `allow_comment` TINYINT      NOT NULL DEFAULT 1      COMMENT '是否允许评论',
    `view_count`    INT          NOT NULL DEFAULT 0      COMMENT '阅读数',
    `like_count`    INT          NOT NULL DEFAULT 0      COMMENT '点赞数',
    `comment_count` INT          NOT NULL DEFAULT 0      COMMENT '评论数',
    `word_count`    INT          NOT NULL DEFAULT 0      COMMENT '字数',
    `reading_time`  INT          NOT NULL DEFAULT 0      COMMENT '预计阅读分钟数',
    `published_at`  DATETIME     DEFAULT NULL            COMMENT '发布时间',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_article_slug` (`slug`),
    KEY `idx_article_status_time` (`status`, `published_at`),
    KEY `idx_article_category` (`category_id`),
    KEY `idx_article_type` (`type`),
    KEY `idx_article_views` (`view_count`),
    FULLTEXT INDEX `ft_article_search` (`title`, `summary`, `search_text`) WITH PARSER ngram
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '文章';

-- 分类表
CREATE TABLE IF NOT EXISTS `t_category` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`          VARCHAR(64)  NOT NULL                COMMENT '名称',
    `slug`          VARCHAR(100) NOT NULL                COMMENT '别名',
    `description`   VARCHAR(500) DEFAULT NULL            COMMENT '描述',
    `parent_id`     BIGINT       NOT NULL DEFAULT 0      COMMENT '父级 ID，0 为顶级',
    `sort`          INT          NOT NULL DEFAULT 0      COMMENT '排序，越大越靠前',
    `article_count` INT          NOT NULL DEFAULT 0      COMMENT '文章数（冗余）',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_category_slug` (`slug`),
    KEY `idx_category_parent` (`parent_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '分类';

-- 标签表
CREATE TABLE IF NOT EXISTS `t_tag` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`        VARCHAR(64)  NOT NULL                COMMENT '名称',
    `slug`        VARCHAR(100) NOT NULL                COMMENT '别名',
    `color`       VARCHAR(16)  DEFAULT NULL            COMMENT '标签云颜色',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tag_slug` (`slug`),
    UNIQUE KEY `uk_tag_name` (`name`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '标签';

-- 文章-标签关联表（不使用外键，避免 5.7 外键约束带来的维护成本）
CREATE TABLE IF NOT EXISTS `t_article_tag` (
    `article_id` BIGINT NOT NULL COMMENT '文章 ID',
    `tag_id`     BIGINT NOT NULL COMMENT '标签 ID',
    PRIMARY KEY (`article_id`, `tag_id`),
    KEY `idx_article_tag_tag` (`tag_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '文章标签关联';

-- 评论表（支持一层回复）
CREATE TABLE IF NOT EXISTS `t_comment` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `article_id`    BIGINT       NOT NULL                COMMENT '文章 ID',
    `parent_id`     BIGINT       NOT NULL DEFAULT 0      COMMENT '父评论 ID，0 为顶层',
    `author_name`   VARCHAR(64)  NOT NULL                COMMENT '评论者昵称',
    `author_email`  VARCHAR(128) DEFAULT NULL            COMMENT '评论者邮箱',
    `author_site`   VARCHAR(255) DEFAULT NULL            COMMENT '评论者站点',
    `author_avatar` VARCHAR(255) DEFAULT NULL            COMMENT '评论者头像',
    `content`       TEXT         NOT NULL                COMMENT '评论内容',
    `status`        VARCHAR(16)  NOT NULL DEFAULT 'pending' COMMENT '状态：pending/approved/spam/deleted',
    `user_agent`    VARCHAR(500) DEFAULT NULL            COMMENT 'UA',
    `ip`            VARCHAR(64)  DEFAULT NULL            COMMENT 'IP',
    `is_admin`      TINYINT      NOT NULL DEFAULT 0      COMMENT '是否管理员评论',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_comment_article_status` (`article_id`, `status`),
    KEY `idx_comment_created` (`created_at`),
    KEY `idx_comment_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '评论';

-- 媒体库
CREATE TABLE IF NOT EXISTS `t_media` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `file_name`     VARCHAR(255) NOT NULL                COMMENT '存储文件名',
    `original_name` VARCHAR(255) DEFAULT NULL            COMMENT '原始文件名',
    `url`           VARCHAR(500) NOT NULL                COMMENT '访问 URL',
    `mime_type`     VARCHAR(100) DEFAULT NULL            COMMENT 'MIME',
    `size`          BIGINT       NOT NULL DEFAULT 0      COMMENT '字节大小',
    `width`         INT          DEFAULT NULL            COMMENT '图片宽度',
    `height`        INT          DEFAULT NULL            COMMENT '图片高度',
    `folder`        VARCHAR(100) NOT NULL DEFAULT ''     COMMENT '目录（存储路径，兼容保留）',
    `folder_id`     BIGINT       DEFAULT NULL            COMMENT '逻辑目录 ID（t_media_folder），NULL 为未分组',
    `uploader_id`   BIGINT       DEFAULT NULL            COMMENT '上传者',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_media_folder` (`folder`),
    KEY `idx_media_folder_id` (`folder_id`),
    KEY `idx_media_created` (`created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '媒体库';

-- 媒体目录（树形）
CREATE TABLE IF NOT EXISTS `t_media_folder` (
    `id`         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`       VARCHAR(64) NOT NULL                COMMENT '目录名称',
    `parent_id`  BIGINT      NOT NULL DEFAULT 0      COMMENT '父目录 ID，0 为顶级',
    `sort`       INT         NOT NULL DEFAULT 0      COMMENT '排序，越小越靠前',
    `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_media_folder_parent` (`parent_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '媒体目录';

-- 主题配置（核心）
CREATE TABLE IF NOT EXISTS `t_theme` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`        VARCHAR(100) NOT NULL                COMMENT '主题名称',
    `description` VARCHAR(500) DEFAULT NULL            COMMENT '描述',
    `config_json` LONGTEXT                             COMMENT '主题配置 JSON 字符串（不用 MySQL JSON 类型，保证 5.7 兼容）',
    `is_active`   TINYINT      NOT NULL DEFAULT 0      COMMENT '是否启用',
    `is_builtin`  TINYINT      NOT NULL DEFAULT 0      COMMENT '是否内置/默认主题：1=内置（不可删除）',
    `version`     INT          NOT NULL DEFAULT 1      COMMENT '版本号，每次发布 +1，用于前台缓存失效',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_theme_active` (`is_active`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '主题';

-- 站点设置（KV）
CREATE TABLE IF NOT EXISTS `t_setting` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `setting_key`   VARCHAR(100) NOT NULL                COMMENT '键',
    `setting_value` LONGTEXT                             COMMENT '值（JSON 字符串或文本）',
    `remark`        VARCHAR(255) DEFAULT NULL            COMMENT '备注',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_setting_key` (`setting_key`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '站点设置';

-- 操作日志
CREATE TABLE IF NOT EXISTS `t_operation_log` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id`    BIGINT       DEFAULT NULL            COMMENT '操作人',
    `module`     VARCHAR(64)  DEFAULT NULL            COMMENT '模块',
    `action`     VARCHAR(64)  DEFAULT NULL            COMMENT '动作',
    `detail`     VARCHAR(1000) DEFAULT NULL           COMMENT '详情',
    `ip`         VARCHAR(64)  DEFAULT NULL            COMMENT 'IP',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_log_created` (`created_at`),
    KEY `idx_log_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '操作日志';

-- =============================================================
--  初始站点设置（INSERT IGNORE，重复执行不会覆盖已有配置）
-- =============================================================
INSERT IGNORE INTO `t_setting` (`setting_key`, `setting_value`, `remark`) VALUES
('site_title',       '我的博客',                                        '站点标题'),
('site_subtitle',    '记录生活与技术',                                   '站点副标题'),
('site_logo',        '',                                                '站点 Logo URL'),
('site_favicon',     '',                                                '站点 Favicon URL'),
('site_footer',      '© 2024 My Blog. Powered by Blog Server.',         '页脚 HTML'),
('icp_no',           '',                                                '备案号'),
('seo_keywords',     '博客,技术,生活',                                   'SEO 关键词'),
('seo_desc',         '一个支持动态主题的博客系统',                        'SEO 描述'),
('comment_review_on','true',                                            '评论是否需要审核'),
('page_size',        '10',                                              '前台每页条数'),
('friend_links',     '[]',                                              '友情链接 JSON 数组'),
('social_links',     '[]',                                              '社交链接 JSON 数组');

-- =============================================================
--  媒体目录初始数据（可重复执行：INSERT IGNORE + UPDATE 幂等）
-- =============================================================

-- 一级目录「博客logo」
INSERT IGNORE INTO `t_media_folder` (`name`, `parent_id`, `sort`)
SELECT '博客logo', 0, 0
WHERE NOT EXISTS (
    SELECT 1 FROM `t_media_folder` WHERE `name` = '博客logo' AND `parent_id` = 0
);

-- 将数据库里已有的媒体文件（尚未分组的）归入「博客logo」目录
UPDATE `t_media`
SET `folder_id` = (
        SELECT `id` FROM `t_media_folder`
        WHERE `name` = '博客logo' AND `parent_id` = 0
        LIMIT 1
    ),
    `folder` = '博客logo'
WHERE `folder_id` IS NULL;
