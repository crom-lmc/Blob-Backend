-- 主题删除特性：t_theme 增加 is_builtin 标识 + 补种内置默认主题
-- 幂等脚本：可重复执行，不会重复加列或重复插入内置行。
-- 参考 tmp_search_ddl.sql 的写法；项目 spring.sql.init.mode=never，需手动执行本脚本。

USE blog;

-- 1) 加列（列已存在则跳过）
SET @exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'blog' AND TABLE_NAME = 't_theme' AND COLUMN_NAME = 'is_builtin'
);
SET @sql = IF(@exists = 0,
    "ALTER TABLE t_theme ADD COLUMN is_builtin TINYINT NOT NULL DEFAULT 0 COMMENT '是否内置/默认主题：1=内置（不可删除）'",
    "SELECT 1");
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) 若库里已存在生效的「默认主题」但未标记内置，先将其标记为内置（避免重复造行）
UPDATE t_theme SET is_builtin = 1
WHERE is_active = 1 AND name = '默认主题' AND is_builtin = 0;

-- 3) 若仍无任何内置主题，则补种一条（仅当不存在任何内置主题时）
INSERT INTO t_theme (name, description, config_json, is_builtin, is_active, version, created_at, updated_at)
SELECT '默认主题', '内置默认主题，不可删除',
       (SELECT config_json FROM (SELECT config_json FROM t_theme ORDER BY id LIMIT 1) t),
       1, 0, 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM t_theme WHERE is_builtin = 1);

-- 4) 兜底：有内置主题但全库无生效主题 → 激活内置主题
UPDATE t_theme SET is_active = 1
WHERE is_builtin = 1
  AND NOT EXISTS (SELECT 1 FROM (SELECT 1 FROM t_theme WHERE is_active = 1) x);
