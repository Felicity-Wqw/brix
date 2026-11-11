-- ============================================================================
-- P5：小法庭 AI 预审参考（任务 #13）
-- 举证期转投票期时由平台 AI 生成案件预审（争议焦点/证据摘要/倾向参考），
-- 落库后随案件详情下发；AI 不可用时字段留空，页面不展示，不影响任何流程。
-- 幂等：列不存在则添加，可重复执行。
-- ============================================================================

USE brix_campus;

SET @col := (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = 'brix_campus' AND table_name = 't_jury_case' AND column_name = 'ai_preview');
SET @sql := IF(@col = 0,
  'ALTER TABLE t_jury_case ADD COLUMN ai_preview TEXT NULL COMMENT ''AI 预审参考 JSON（争议焦点/证据摘要/倾向）'' AFTER ruling_reason',
  'SELECT ''ai_preview 已存在''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col := (SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = 'brix_campus' AND table_name = 't_jury_case' AND column_name = 'ai_previewed_at');
SET @sql := IF(@col = 0,
  'ALTER TABLE t_jury_case ADD COLUMN ai_previewed_at DATETIME NULL COMMENT ''AI 预审生成时间'' AFTER ai_preview',
  'SELECT ''ai_previewed_at 已存在''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
