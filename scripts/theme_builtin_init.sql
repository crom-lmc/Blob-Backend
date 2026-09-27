-- ============================================================
-- 主题内置标识（is_builtin）：支持默认主题强保护
-- 配套后端文档：THEME_DELETE_BACKEND.md
--
-- 幂等脚本，可重复执行；适用于「已上线数据库」做增量迁移。
-- 全新部署请直接使用 db/schema.sql 基线（已含 is_builtin 列）。
--
-- 执行方式（MySQL 客户端 / CI）：
--   mysql -u<user> -p<pass> blog < scripts/theme_builtin_init.sql
-- ============================================================

-- 1) 新增 is_builtin 列（幂等）
SET @c = (SELECT COUNT(*) FROM information_schema.columns
          WHERE table_schema = DATABASE()
            AND table_name = 't_theme'
            AND column_name = 'is_builtin');
SET @s1 = IF(@c = 0,
  'ALTER TABLE t_theme ADD COLUMN is_builtin TINYINT NOT NULL DEFAULT 0 COMMENT ''是否内置/默认主题：1=内置（不可删除）''',
  'SELECT 1');
PREPARE st1 FROM @s1; EXECUTE st1; DEALLOCATE PREPARE st1;

-- 2) 存量初始化：若库中无任何内置主题，则补种一条内置默认主题行；
--    若同时没有任何生效主题，则将其置为生效，保证前台始终有主题可用。
INSERT INTO t_theme (name, description, config_json, is_builtin, is_active, version)
SELECT '默认主题', '内置默认主题，不可删除',
       (SELECT config_json FROM (SELECT config_json FROM t_theme ORDER BY id LIMIT 1) t),
       1, 0, 1
WHERE NOT EXISTS (SELECT 1 FROM t_theme WHERE is_builtin = 1);

-- 3) 兜底：存在内置主题但全库无生效主题 → 激活内置主题
UPDATE t_theme SET is_active = 1
WHERE is_builtin = 1
  AND NOT EXISTS (SELECT 1 FROM (SELECT 1 FROM t_theme WHERE is_active = 1) x);

-- 4) 校验
SELECT
  (SELECT COUNT(*) FROM t_theme WHERE is_builtin = 1) AS builtin_count,
  (SELECT COUNT(*) FROM t_theme WHERE is_active = 1)  AS active_count;
