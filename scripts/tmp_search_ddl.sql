-- 1) 新增 search_text 列（幂等）
SET @c = (SELECT COUNT(*) FROM information_schema.columns
          WHERE table_schema='blog' AND table_name='t_article' AND column_name='search_text');
SET @s1 = IF(@c=0, 'ALTER TABLE t_article ADD COLUMN search_text MEDIUMTEXT NULL', 'SELECT 1');
PREPARE st1 FROM @s1; EXECUTE st1; DEALLOCATE PREPARE st1;

-- 2) 新增 ngram 全文索引（幂等）
SET @i = (SELECT COUNT(*) FROM information_schema.statistics
          WHERE table_schema='blog' AND table_name='t_article' AND index_name='ft_article_search');
SET @s2 = IF(@i=0, 'ALTER TABLE t_article ADD FULLTEXT INDEX ft_article_search (title, summary, search_text) WITH PARSER ngram', 'SELECT 1');
PREPARE st2 FROM @s2; EXECUTE st2; DEALLOCATE PREPARE st2;

-- 3) 存量回填：用 Markdown 原文作为检索文本（应用层 save 会自动写入纯文本）
UPDATE t_article SET search_text = content_md WHERE search_text IS NULL OR search_text = '';

-- 4) 校验
SELECT COUNT(*) AS articles_with_search_text FROM t_article WHERE search_text IS NOT NULL AND search_text <> '';
