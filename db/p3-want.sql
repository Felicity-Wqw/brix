-- ============================================================
-- 求购墙：买家发求购帖，卖家私信接单，成交后发帖人关闭
-- 执行方式：mysql -ubrix -p brix_campus < db/p3-want.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS t_want (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL COMMENT '发帖人',
  title VARCHAR(60) NOT NULL COMMENT '求购标题',
  description VARCHAR(500) NULL COMMENT '需求描述',
  budget DECIMAL(10,2) NULL COMMENT '预算上限',
  campus VARCHAR(50) NULL COMMENT '期望交易校区',
  school_id BIGINT NULL COMMENT '学校',
  status VARCHAR(20) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN 求购中 / CLOSED 已结束',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_want_status (status),
  KEY idx_want_user (user_id),
  KEY idx_want_school (school_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='求购墙';

-- 早期版本升级（已有表补学校字段）
-- ALTER TABLE t_want ADD COLUMN school_id BIGINT NULL COMMENT '学校' AFTER campus, ADD KEY idx_want_school (school_id);
