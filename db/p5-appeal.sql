-- ============================================================================
-- P5：商品申诉（任务 #15/#16）
-- 商品被审核拒绝或被运营处置下架后，卖家可提交申诉；
-- 审核员（REVIEWER）处理，AI 复核建议（ai_review）作为处理参考。
-- ============================================================================

USE brix_campus;

CREATE TABLE IF NOT EXISTS t_appeal (
  id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  user_id     BIGINT UNSIGNED NOT NULL COMMENT '申诉人（卖家）',
  item_id     BIGINT UNSIGNED NOT NULL COMMENT '申诉商品',
  reason      VARCHAR(500) NOT NULL COMMENT '申诉理由',
  status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING 待处理 / MAINTAINED 维持 / OVERTURNED 改判',
  ai_review   TEXT         NULL COMMENT 'AI 复核建议 JSON（维持/改判建议+理由+置信度）',
  reply       VARCHAR(500) NULL COMMENT '处理回复',
  handler_id  BIGINT UNSIGNED NULL COMMENT '处理审核员',
  handled_at  DATETIME     NULL COMMENT '处理时间',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted     TINYINT      NOT NULL DEFAULT 0,
  KEY idx_appeal_user (user_id),
  KEY idx_appeal_item (item_id),
  KEY idx_appeal_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='商品申诉';
