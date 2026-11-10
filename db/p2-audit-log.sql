-- ============================================================================
-- P2 增量迁移：商品审核日志表（幂等，可重复执行）
--
-- 为什么需要独立的日志表，而不是只用 t_item 上的几个字段：
--   1) t_item 的字段是「当前状态」，商品被卖家编辑、驳回后重发、甚至删除，
--      上一次审核的判定证据就没了。审核留痕必须是不可变的历史记录。
--   2) 日志要能回答四个问题（答辩必问）：
--        谁发的（卖家信息快照）、发了什么（商品信息快照）、
--        第一道闸命中了哪个词、第二道闸 AI 说了什么、什么时候审的。
--      这些跨 t_item / t_user / t_category 三张表，必须落快照，不能靠 join。
--   3) 低风险商品「直接上架」也要留日志 —— 平台能证明「这条内容我审过了」，
--      出了问题可回溯，这也是把审核做成「分级」而不是「抽查」的意义。
--
-- 与 t_item 的分工：
--   t_item          → 当前结果（status / risk_level / audit_reason），前台后台读它
--   t_item_audit_log → 每次审核的过程证据，只增不改，后台审核日志页读它
--
-- 依赖：需先执行 db/p2-audit-migration.sql（本文件追加在它之后）。
-- ============================================================================

USE brix_campus;

CREATE TABLE IF NOT EXISTS t_item_audit_log (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  item_id         BIGINT UNSIGNED NOT NULL                COMMENT '商品 ID',
  seller_id       BIGINT UNSIGNED NOT NULL                COMMENT '卖家 ID',

  -- ------------------------ 商品信息快照（审核当时的样子） ------------------------
  item_title      VARCHAR(40)     DEFAULT NULL            COMMENT '标题快照',
  item_price      DECIMAL(10,2)   DEFAULT NULL            COMMENT '价格快照',
  item_cover      VARCHAR(255)    DEFAULT NULL            COMMENT '首图快照',
  category_name   VARCHAR(50)     DEFAULT NULL            COMMENT '分类名快照',

  -- ------------------------ 卖家信息快照（审核当时的样子） ------------------------
  seller_username VARCHAR(50)     DEFAULT NULL            COMMENT '卖家登录名',
  seller_nickname VARCHAR(50)     DEFAULT NULL            COMMENT '卖家昵称',
  seller_school   VARCHAR(100)    DEFAULT NULL            COMMENT '卖家学校',
  seller_campus   VARCHAR(50)     DEFAULT NULL            COMMENT '卖家校区',
  seller_credit   INT             DEFAULT NULL            COMMENT '卖家信用分',

  -- ------------------------ 第一道闸：关键词闸 ------------------------
  gate1_result    VARCHAR(20)     NOT NULL DEFAULT 'PASS' COMMENT 'PASS 未命中 / MILD_HIT 命中轻度词 / BLACK_HIT 命中黑名单',
  gate1_word      VARCHAR(50)     DEFAULT NULL            COMMENT '命中的具体词',
  gate1_category  VARCHAR(50)     DEFAULT NULL            COMMENT '命中词所属类目（如「管制武器与危险物品」「代购与批发」）',

  -- ------------------------ 第二道闸：AI 审核 ------------------------
  gate2_result    VARCHAR(20)     DEFAULT NULL            COMMENT 'PASS 通过 / UNCERTAIN 模糊不确定 / BAN 判定无法上架 / UNAVAILABLE 未启用或调用失败',
  gate2_verdict   VARCHAR(20)     DEFAULT NULL            COMMENT 'AI 原始判定值，便于对照 prompt 契约',
  gate2_reason    VARCHAR(500)    DEFAULT NULL            COMMENT 'AI 给出的理由',

  -- ------------------------ 分流结论 ------------------------
  risk_level      VARCHAR(10)     NOT NULL                COMMENT 'LOW / MEDIUM / HIGH',
  decision_status VARCHAR(20)     NOT NULL                COMMENT '本次审核后商品落到什么状态：ON_SALE / PENDING / REJECTED',
  decision_note   VARCHAR(500)    DEFAULT NULL            COMMENT '给卖家看的判定说明',

  -- ------------------------ 人工处置回写（高风险优先区用完写这里） ----------------
  handler_id      BIGINT UNSIGNED DEFAULT NULL            COMMENT '人工处置人',
  handled_at      DATETIME        DEFAULT NULL            COMMENT '人工处置时间',
  handle_note     VARCHAR(200)    DEFAULT NULL            COMMENT '人工处置说明',

  created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '审核时间',

  PRIMARY KEY (id),
  KEY idx_log_item (item_id, created_at),
  KEY idx_log_seller (seller_id, created_at),
  KEY idx_log_risk (risk_level, created_at),
  KEY idx_log_gate1 (gate1_result, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品审核日志（不可变append-only）';

-- 校验
SELECT COUNT(*) AS audit_log_rows FROM t_item_audit_log;
