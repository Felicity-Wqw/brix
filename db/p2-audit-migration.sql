-- ============================================================================
-- P2 增量迁移：商品审核三级分流（幂等，可重复执行）
--
-- 现状问题：
--   1) t_item.status 里有 PENDING / REJECTED 两个状态，但发布链路直接写 ON_SALE，
--      这两个状态从未被写入 → 人工审核队列永远是空的，审核页无数据可审。
--   2) 违规内容在服务层直接抛异常，商品根本不落库 → 平台侧无任何留痕，
--      卖家只看到一句报错，无法申诉，审核员也无从复核。
--
-- 三级分流口径（低 / 中 / 高）：
--   低风险 LOW    → 直接 ON_SALE，发布体验与改造前完全一致；
--   中风险 MEDIUM → PENDING，进人工审核队列，审核员通过后上架；
--   高风险 HIGH   → REJECTED，落库但前台不可见，卖家可看到驳回原因，审核员可纠正误杀。
--
-- 新增列：审核结论与审计痕迹，只加不删，历史行保持 NULL / 默认值。
--   risk_level   风险级别 LOW/MEDIUM/HIGH（NULL = 改造前的历史数据，视为已放行）
--   audit_source 判定来源 WORD 违禁词 / RULE 规则 / AI 智能体 / MANUAL 人工
--   audit_reason 判定说明（命中词、AI 理由、人工驳回原因）
--   audit_by     最后一次人工审核人 ID（自动分流时为 NULL）
--   audit_at     最后一次人工审核时间
--
-- 依赖：需先执行 db/p2-migration.sql（本文件追加在它之后）。
-- ============================================================================

USE brix_campus;

-- ---------------------------------------------------------------- A. 审核结论列
SET @sql = (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_item' AND COLUMN_NAME = 'risk_level') = 0,
  'ALTER TABLE t_item ADD COLUMN risk_level VARCHAR(10) DEFAULT NULL COMMENT ''风险级别 LOW/MEDIUM/HIGH（NULL=历史数据）'' AFTER status',
  'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_item' AND COLUMN_NAME = 'audit_source') = 0,
  'ALTER TABLE t_item ADD COLUMN audit_source VARCHAR(20) DEFAULT NULL COMMENT ''判定来源 WORD/RULE/AI/MANUAL'' AFTER risk_level',
  'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_item' AND COLUMN_NAME = 'audit_reason') = 0,
  'ALTER TABLE t_item ADD COLUMN audit_reason VARCHAR(500) DEFAULT NULL COMMENT ''判定说明：命中词 / AI 理由 / 人工驳回原因'' AFTER audit_source',
  'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_item' AND COLUMN_NAME = 'audit_by') = 0,
  'ALTER TABLE t_item ADD COLUMN audit_by BIGINT UNSIGNED DEFAULT NULL COMMENT ''最后一次人工审核人'' AFTER audit_reason',
  'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_item' AND COLUMN_NAME = 'audit_at') = 0,
  'ALTER TABLE t_item ADD COLUMN audit_at DATETIME DEFAULT NULL COMMENT ''最后一次人工审核时间'' AFTER audit_by',
  'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------- B. 队列索引
-- 审核队列的典型查询：按状态 + 风险级别倒序取最新，走这个联合索引。
SET @sql = (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_item' AND INDEX_NAME = 'idx_status_risk') = 0,
  'ALTER TABLE t_item ADD KEY idx_status_risk (status, risk_level, created_at)',
  'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------- C. 举报处理备注
-- 举报原本只有「处理动作 + 处理人」，没有留下判断依据。
-- 审核员写一句「图片与实物不符，已下架」对后续申诉与复盘都有用，加一列即可。
SET @sql = (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_report' AND COLUMN_NAME = 'handle_remark') = 0,
  'ALTER TABLE t_report ADD COLUMN handle_remark VARCHAR(200) DEFAULT NULL COMMENT ''处理备注（审核员填写）''',
  'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------- D. 历史数据口径
-- 改造前发布的商品一律视为「已放行」，标注 LOW 便于统计审核覆盖率时区分；
-- 只更新 risk_level 为 NULL 的行，重复执行不影响已审核数据。
UPDATE t_item SET risk_level = 'LOW', audit_source = 'LEGACY'
 WHERE risk_level IS NULL;

-- 校验：应输出 status × risk_level 的分布
SELECT status, risk_level, COUNT(*) AS cnt FROM t_item GROUP BY status, risk_level ORDER BY status;
