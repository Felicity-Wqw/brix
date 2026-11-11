-- ======================================================================
-- p3-image-audit.sql
-- 图片审核（GLM-4V-Flash 视觉模型）：
--   1) t_image_audit_cache：图片审核结论缓存，按图片内容 MD5 去重，
--      上传时触发审核、发布时直接读结论（命中零调用零时延）；
--   2) t_item_audit_log 加 gate2_findings：AI 闸的逐图审核明细（JSON 数组），
--      与文本结论合并成一条审核记录，不单独开「图片审核」视图。
-- 幂等：IF NOT EXISTS / 列存在性由执行方保证（重复执行会报 Duplicate column，可忽略）。
-- 执行：mysql -ubrix -p brix_campus --default-character-set=utf8mb4 < p3-image-audit.sql
-- ======================================================================

CREATE TABLE IF NOT EXISTS t_image_audit_cache (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  image_md5   CHAR(32)        NOT NULL COMMENT '图片内容 MD5（去重键）',
  status      VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING 审核中 / PASS / REJECT / UNCERTAIN / FAILED',
  reason      VARCHAR(500)    NULL COMMENT '审核理由（给审核员与卖家看）',
  model       VARCHAR(50)     NULL COMMENT '执行的模型（降级排查用）',
  cost_ms     INT             NULL COMMENT '耗时毫秒',
  audited_at  DATETIME        NULL COMMENT '结论产生时间（PENDING 为 NULL）',
  created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_image_md5 (image_md5)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '图片审核结论缓存（按内容 MD5 去重）';

ALTER TABLE t_item_audit_log
    ADD COLUMN gate2_findings TEXT NULL COMMENT 'AI 闸逐图审核明细 JSON：[{index,url,verdict,reason}]' AFTER gate2_reason;
