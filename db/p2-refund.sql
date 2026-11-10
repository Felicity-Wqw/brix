-- ============================================================================
-- P2 增量迁移：售后退款 + 小法庭（幂等，可重复执行）
--
-- 设计要点（答辩口径）：
--   1) 售后流程对齐主流电商现行规则（淘宝/京东/拼多多，2025-2026 新规口径）：
--      - 确认收货后 15 天内可申请售后
--      - 卖家 36 小时内处理，超时默认同意并自动退款（淘宝 72h / 拼多多 36h，取严值）
--      - 拒绝后买家 5 日内可修改重提（重新计时）或申请平台介入
--      - 退货环节：买家 7 日内填运单号，超时关单；卖家超时未确认收货也自动退款
--      - 面交单只支持「仅退款」（线下当面退，无物流环节）
--   2) 平台介入不走管理员直接裁决，而是「小法庭」：
--      举证期 24h（双方提交文字+图片证据）→ 陪审期 48h（平台用户匿名投票，
--      一人一票，当事人不可投自己的案子）→ 期满自动推送管理端终审。
--      管理端可见：双方真实身份 + 全部证据 + 投票统计（支持率/参与人数）。
--   3) 状态通知只发给买卖双方当事人，走聊天通道（platform 系统账号发送）；
--      陪审完全自愿，不给围观用户做任何推送。
--   4) 退款为沙箱语义：写 refund_time + 状态，不做真实资金流转。
--
-- 依赖：t_order / t_user 已存在（db/schema.sql）。
-- ============================================================================

USE brix_campus;

-- ----------------------------------------------------------------------------
-- 1. 售后单：一次交易同时最多一张进行中的售后单（代码层保证）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_refund (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  order_id       BIGINT UNSIGNED NOT NULL                COMMENT '订单 ID',
  buyer_id       BIGINT UNSIGNED NOT NULL                COMMENT '买家 ID',
  seller_id      BIGINT UNSIGNED NOT NULL                COMMENT '卖家 ID',
  amount         DECIMAL(10,2)   NOT NULL DEFAULT 0      COMMENT '退款金额（下单时订单金额快照）',
  type           VARCHAR(20)     NOT NULL                COMMENT '类型：REFUND_ONLY 仅退款 / RETURN_REFUND 退货退款（邮寄单专属）',
  status         VARCHAR(20)     NOT NULL DEFAULT 'APPLYING' COMMENT '状态：APPLYING申请中/WAIT_RETURN待买家寄回/WAIT_RECEIVE待卖家收货/REFUNDED已退款/REJECTED已拒绝/JURY_EVIDENCE举证期/JURY_VOTING陪审期/PENDING_RULING待裁决/CLOSED已关闭/CANCELED已撤销',
  reason         VARCHAR(300)    NOT NULL                COMMENT '买家申请原因',
  images         VARCHAR(2000)   DEFAULT NULL            COMMENT '买家凭证图 URL 列表（JSON 数组）',
  seller_reply   VARCHAR(300)    DEFAULT NULL            COMMENT '卖家回复（同意说明或拒绝理由）',
  tracking_no    VARCHAR(50)     DEFAULT NULL            COMMENT '买家退货运单号（退货退款流程）',
  reject_at      DATETIME        DEFAULT NULL            COMMENT '最近一次拒绝时间（重提 5 日窗口起点）',
  revise_count   INT             NOT NULL DEFAULT 0      COMMENT '买家修改重提次数',
  refund_time    DATETIME        DEFAULT NULL            COMMENT '退款完成时间',
  close_reason   VARCHAR(200)    DEFAULT NULL            COMMENT '关闭原因（超时关单/裁决支持卖家等）',
  created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted        TINYINT         NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_refund_order (order_id),
  KEY idx_refund_buyer (buyer_id),
  KEY idx_refund_seller (seller_id),
  KEY idx_refund_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='售后退款单';

-- ----------------------------------------------------------------------------
-- 2. 小法庭案件：一张售后单最多一桩案件（代码层保证）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_jury_case (
  id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  case_no           VARCHAR(20)     NOT NULL                COMMENT '案件编号（如 JF20261107-001）',
  refund_id         BIGINT UNSIGNED NOT NULL                COMMENT '售后单 ID',
  order_id          BIGINT UNSIGNED NOT NULL                COMMENT '订单 ID',
  buyer_id          BIGINT UNSIGNED NOT NULL                COMMENT '买家 ID（仅管理端可见）',
  seller_id         BIGINT UNSIGNED NOT NULL                COMMENT '卖家 ID（仅管理端可见）',
  status            VARCHAR(20)     NOT NULL DEFAULT 'EVIDENCE' COMMENT '阶段：EVIDENCE举证期/VOTING陪审期/PENDING_RULING待裁决/RULED已裁决',
  reason            VARCHAR(300)    NOT NULL                COMMENT '纠纷事由（发起介入一方的陈述）',
  initiator         VARCHAR(10)     NOT NULL DEFAULT 'BUYER' COMMENT '发起方：BUYER / SELLER',
  evidence_deadline DATETIME        NOT NULL                COMMENT '举证截止时间（开庭+24h）',
  vote_deadline     DATETIME        NOT NULL                COMMENT '投票截止时间（举证截止+48h）',
  ruling_result     VARCHAR(10)     DEFAULT NULL            COMMENT '裁决结果：BUYER 支持买家 / SELLER 支持卖家',
  ruling_reason     VARCHAR(300)    DEFAULT NULL            COMMENT '裁决理由',
  ruling_by         BIGINT UNSIGNED DEFAULT NULL            COMMENT '裁决管理员 ID',
  ruled_at          DATETIME        DEFAULT NULL            COMMENT '裁决时间',
  created_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted           TINYINT         NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_case_no (case_no),
  UNIQUE KEY uk_case_refund (refund_id),
  KEY idx_case_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='小法庭案件';

-- ----------------------------------------------------------------------------
-- 3. 案件证据：双方各自提交，只增不改（append-only，与审核日志同风格）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_jury_evidence (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  case_id    BIGINT UNSIGNED NOT NULL                COMMENT '案件 ID',
  side       VARCHAR(10)     NOT NULL                COMMENT '提交方：BUYER / SELLER',
  content    VARCHAR(500)    NOT NULL                COMMENT '证据说明（文字）',
  images     VARCHAR(2000)   DEFAULT NULL            COMMENT '证据图片 URL 列表（JSON 数组）',
  created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted    TINYINT         NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  PRIMARY KEY (id),
  KEY idx_evidence_case (case_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='小法庭证据';

-- ----------------------------------------------------------------------------
-- 4. 陪审投票：每人每案一票（唯一约束兜底），投后不可改
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_jury_vote (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  case_id    BIGINT UNSIGNED NOT NULL                COMMENT '案件 ID',
  user_id    BIGINT UNSIGNED NOT NULL                COMMENT '陪审用户 ID',
  vote       VARCHAR(10)     NOT NULL                COMMENT '立场：BUYER 支持买家 / SELLER 支持卖家',
  created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '投票时间',
  updated_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted    TINYINT         NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_vote_case_user (case_id, user_id),
  KEY idx_vote_case (case_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='小法庭陪审投票';

-- ----------------------------------------------------------------------------
-- 5. 订单补「退款时间」：取消已支付订单 / 售后退款完成时写入（资金链路闭环）
-- ----------------------------------------------------------------------------
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA='brix_campus' AND TABLE_NAME='t_order' AND COLUMN_NAME='refund_time');
SET @ddl = IF(@col_exists = 0,
              'ALTER TABLE t_order ADD COLUMN refund_time DATETIME DEFAULT NULL COMMENT ''退款时间（取消已支付订单或售后退款完成时写入）'' AFTER settle_time',
              'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ----------------------------------------------------------------------------
-- 6. 平台客服系统账号：聊天通知的发送方（不参与交易）
-- ----------------------------------------------------------------------------
INSERT INTO t_user (username, password, nickname, avatar, school, campus, school_id, campus_id,
                    role, status, credit_score, bio)
SELECT 'platform',
       (SELECT password FROM t_user WHERE username='student01' LIMIT 1),
       '物归平台客服', '/uploads/seed/platform.png', '广西师范大学', '王城校区', 1, 1,
       'USER', 'NORMAL', 100, '平台官方账号：订单售后与纠纷处理通知，请以站内消息为准'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM t_user WHERE username='platform');
