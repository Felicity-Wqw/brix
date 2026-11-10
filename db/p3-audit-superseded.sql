-- ======================================================================
-- p3-audit-superseded.sql
-- 审核日志口径修正：历史「待人工审核」日志行，若对应商品已通过后续重新审核
-- 出结果（当前状态不再是 PENDING），则该行处置状态标记为 SUPERSEDED。
--
-- 背景：t_item_audit_log 为 append-only 证据链（一次自动审核 = 一行），
-- 此前商品编辑重审时旧 PENDING 行未失效，导致后台出现
-- 「审核日志里有待人工审核、商品审核队列却是空的」的口径错位。
-- 后端 record() 已在写入新日志前自动失效同商品旧 PENDING 行（增量行为），
-- 本脚本仅负责一次性回填存量数据。
--
-- 幂等性：重复执行无副作用（SUPERSEDED 行不满足 WHERE，不再变化）。
-- 执行：mysql -ubrix -p brix_campus --default-character-set=utf8mb4 < p3-audit-superseded.sql
-- ======================================================================

UPDATE t_item_audit_log a
LEFT JOIN t_item i ON i.id = a.item_id
SET a.decision_status = 'SUPERSEDED',
    a.decision_note   = '商品后续重新审核产生新结论（或商品已删除），本条待审记录已自动失效（最新结论见该商品最新一行日志）'
WHERE a.decision_status = 'PENDING'
  AND (i.id IS NULL          -- 商品已物理不存在
    OR i.deleted = 1         -- 商品已逻辑删除，不可能再人工审核
    OR i.status <> 'PENDING'); -- 商品已被后续审核出结果

-- 验证（应返回空集或仅剩商品本身仍 PENDING 的行）：
-- SELECT a.id, a.item_id, a.risk_level, a.decision_status
-- FROM t_item_audit_log a JOIN t_item i ON i.id = a.item_id
-- WHERE a.decision_status = 'PENDING' AND i.deleted = 0 AND i.status <> 'PENDING';
