-- ============================================================================
-- Brix Campus Market 校园二手交易平台 —— 表结构脚本
-- 目标库：MySQL 9.x（本机服务名 MySQL80，端口 3306）
-- 使用方式：mysql -u root -p --default-character-set=utf8mb4 < db/schema.sql
-- 说明：
--   1) 本脚本只建库 + 建表 + 建索引，可重复执行（DROP TABLE IF EXISTS）。
--   2) 所有表名统一 t_ 前缀，与后端 mybatis-plus.global-config.db-config.table-prefix 对应。
--   3) 应用账号默认 brix / brix123（见文末注释，按需手工执行，避免脚本因权限失败）。
--   4) 不建外键约束：便于种子数据与演示期增删改。
-- ============================================================================

CREATE DATABASE IF NOT EXISTS brix_campus
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE brix_campus;

-- ---------------------------------------------------------------- 1. 用户
DROP TABLE IF EXISTS t_user;
CREATE TABLE t_user (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  username     VARCHAR(50)     NOT NULL                COMMENT '登录名',
  password     VARCHAR(100)    NOT NULL                COMMENT 'BCrypt 密文',
  nickname     VARCHAR(50)     NOT NULL                COMMENT '昵称',
  avatar       VARCHAR(255)    NOT NULL DEFAULT ''     COMMENT '头像 URL',
  school       VARCHAR(100)    DEFAULT NULL            COMMENT '学校',
  campus       VARCHAR(50)     DEFAULT NULL            COMMENT '校区',
  phone        VARCHAR(20)     DEFAULT NULL            COMMENT '手机号',
  bio          VARCHAR(255)    NOT NULL DEFAULT ''     COMMENT '简介',
  role         VARCHAR(20)     NOT NULL DEFAULT 'USER'   COMMENT 'USER/REVIEWER/OPERATOR',
  status       VARCHAR(20)     NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/DISABLED',
  credit_score INT             NOT NULL DEFAULT 100    COMMENT '信用分 0-200',
  disabled_by  BIGINT UNSIGNED DEFAULT NULL            COMMENT '禁用操作人',
  disabled_at  DATETIME        DEFAULT NULL            COMMENT '禁用操作时间',
  deleted      TINYINT         NOT NULL DEFAULT 0      COMMENT '逻辑删除 0/1',
  created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username),
  KEY idx_role_status (role, status),
  KEY idx_campus (campus),
  KEY idx_nickname (nickname)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户';

-- ---------------------------------------------------------------- 2. 分类
DROP TABLE IF EXISTS t_category;
CREATE TABLE t_category (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  name       VARCHAR(30)     NOT NULL           COMMENT '分类名',
  parent_id  BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '父分类 ID，0=一级分类',
  icon       VARCHAR(50)     NOT NULL DEFAULT '' COMMENT '图标',
  sort       INT             NOT NULL DEFAULT 0  COMMENT '升序',
  deleted    TINYINT         NOT NULL DEFAULT 0,
  created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_name (name),
  KEY idx_parent (parent_id),
  KEY idx_sort (sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='分类（两级）';

-- ---------------------------------------------------------------- 3. 商品
DROP TABLE IF EXISTS t_item;
CREATE TABLE t_item (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  title           VARCHAR(40)     NOT NULL                COMMENT '标题 ≤40',
  description     VARCHAR(2000)   NOT NULL DEFAULT ''     COMMENT '描述 ≤2000',
  category_id     BIGINT UNSIGNED NOT NULL                COMMENT '分类',
  price           DECIMAL(10,2)   NOT NULL                COMMENT '现价',
  original_price  DECIMAL(10,2)   DEFAULT NULL            COMMENT '原价',
  condition_level VARCHAR(20)     NOT NULL                COMMENT 'BRAND_NEW/LIKE_NEW/LIGHT_USE/HEAVY_USE',
  campus          VARCHAR(50)     NOT NULL                COMMENT '校区',
  trade_type      VARCHAR(10)     NOT NULL                COMMENT 'MEET/SHIP/BOTH',
  contact         VARCHAR(50)     DEFAULT NULL            COMMENT '联系方式',
  status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/ON_SALE/OFF_SHELF/SOLD/REJECTED',
  cover_image     VARCHAR(255)    NOT NULL DEFAULT ''     COMMENT '首图',
  view_count      INT             NOT NULL DEFAULT 0,
  favorite_count  INT             NOT NULL DEFAULT 0,
  brand_model     VARCHAR(60)     DEFAULT NULL            COMMENT '品牌型号',
  seller_id       BIGINT UNSIGNED NOT NULL,
  deleted         TINYINT         NOT NULL DEFAULT 0,
  created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_status_created (status, created_at),
  KEY idx_category (category_id, status),
  KEY idx_seller (seller_id, status),
  KEY idx_campus_status (campus, status),
  KEY idx_price (price)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品';

-- ---------------------------------------------------------------- 4. 商品图片
DROP TABLE IF EXISTS t_item_image;
CREATE TABLE t_item_image (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  item_id    BIGINT UNSIGNED NOT NULL,
  url        VARCHAR(255)    NOT NULL,
  sort       INT             NOT NULL DEFAULT 0,
  created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_item_sort (item_id, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品图片';

-- ---------------------------------------------------------------- 4.1 浏览历史
DROP TABLE IF EXISTS t_browse_history;
CREATE TABLE t_browse_history (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id    BIGINT UNSIGNED NOT NULL                COMMENT '浏览者',
  item_id    BIGINT UNSIGNED NOT NULL                COMMENT '商品',
  deleted    TINYINT         NOT NULL DEFAULT 0,
  created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_item (user_id, item_id),
  KEY idx_user_time (user_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='浏览历史（每用户每商品一条，重复浏览刷新时间）';

-- ---------------------------------------------------------------- 5. 收藏
DROP TABLE IF EXISTS t_favorite;
CREATE TABLE t_favorite (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id    BIGINT UNSIGNED NOT NULL,
  item_id    BIGINT UNSIGNED NOT NULL,
  created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_item (user_id, item_id),
  KEY idx_item (item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='收藏';

-- ---------------------------------------------------------------- 6. 留言
DROP TABLE IF EXISTS t_item_comment;
CREATE TABLE t_item_comment (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  item_id    BIGINT UNSIGNED NOT NULL,
  user_id    BIGINT UNSIGNED NOT NULL,
  parent_id  BIGINT UNSIGNED DEFAULT NULL COMMENT '空=一级；非空=回复（仅一层）',
  content    VARCHAR(500)    NOT NULL,
  is_read    TINYINT         NOT NULL DEFAULT 1 COMMENT '卖家是否已读：0 未读 / 1 已读（历史数据默认已读）',
  deleted    TINYINT         NOT NULL DEFAULT 0,
  created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_item_parent (item_id, parent_id, created_at),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='留言';

-- ---------------------------------------------------------------- 7. 订单
DROP TABLE IF EXISTS t_order;
CREATE TABLE t_order (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  order_no       VARCHAR(32)     NOT NULL                COMMENT '业务单号',
  item_id        BIGINT UNSIGNED NOT NULL,
  buyer_id       BIGINT UNSIGNED NOT NULL,
  seller_id      BIGINT UNSIGNED NOT NULL,
  amount         DECIMAL(10,2)   NOT NULL                COMMENT '成交金额',
  item_title     VARCHAR(40)     NOT NULL                COMMENT '快照-标题',
  item_cover     VARCHAR(255)    NOT NULL DEFAULT ''     COMMENT '快照-首图',
  item_price     DECIMAL(10,2)   NOT NULL                COMMENT '快照-单价',
  trade_type     VARCHAR(10)     NOT NULL                COMMENT 'MEET/SHIP',
  status         VARCHAR(20)     NOT NULL DEFAULT 'UNPAID' COMMENT 'UNPAID/TO_SHIP/TO_RECEIVE/TO_MEET/DONE/CANCELED',
  pay_method     VARCHAR(10)     DEFAULT NULL            COMMENT 'WECHAT/ALIPAY/BANK',
  pay_time       DATETIME        DEFAULT NULL,
  ship_time      DATETIME        DEFAULT NULL,
  finish_time    DATETIME        DEFAULT NULL,
  cancel_time    DATETIME        DEFAULT NULL,
  close_reason   VARCHAR(20)     DEFAULT NULL            COMMENT 'TIMEOUT/BUYER_CANCEL/SELLER_CANCEL/SYSTEM',
  meet_place     VARCHAR(100)    DEFAULT NULL            COMMENT '面交地点',
  meet_time      DATETIME        DEFAULT NULL            COMMENT '面交时间（面交单）',
  receiver_name  VARCHAR(30)     DEFAULT NULL            COMMENT '收货人姓名（邮寄单）',
  receiver_phone VARCHAR(20)     DEFAULT NULL            COMMENT '收货人手机号（邮寄单）',
  receiver_address VARCHAR(200)  DEFAULT NULL            COMMENT '收货地址（邮寄单）',
  settle_time    DATETIME        DEFAULT NULL            COMMENT '预付款结算给卖家时间（确认收货即释放托管）',
  buyer_note     VARCHAR(200)    DEFAULT NULL            COMMENT '买家备注',
  deleted        TINYINT         NOT NULL DEFAULT 0,
  created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_no (order_no),
  KEY idx_buyer (buyer_id, status, created_at),
  KEY idx_seller (seller_id, status, created_at),
  KEY idx_item_status (item_id, status),
  KEY idx_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单';

-- ---------------------------------------------------------------- 8. 评价
DROP TABLE IF EXISTS t_review;
CREATE TABLE t_review (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  order_id   BIGINT UNSIGNED NOT NULL COMMENT '一单一条',
  item_id    BIGINT UNSIGNED NOT NULL,
  buyer_id   BIGINT UNSIGNED NOT NULL,
  seller_id  BIGINT UNSIGNED NOT NULL,
  rating     TINYINT         NOT NULL COMMENT '1-5',
  content    VARCHAR(500)    NOT NULL DEFAULT '',
  images     VARCHAR(1000)   NOT NULL DEFAULT '' COMMENT 'JSON 数组，≤3 张',
  created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_order (order_id),
  KEY idx_seller (seller_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评价';

-- ---------------------------------------------------------------- 9. 举报
DROP TABLE IF EXISTS t_report;
CREATE TABLE t_report (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  item_id       BIGINT UNSIGNED NOT NULL,
  reporter_id   BIGINT UNSIGNED NOT NULL,
  reason        VARCHAR(20)     NOT NULL COMMENT 'FAKE_INFO/CONTRABAND/PRICE_FRAUD/STOLEN_IMAGE/OTHER',
  description   VARCHAR(200)    NOT NULL DEFAULT '',
  status        VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/HANDLED',
  handle_action VARCHAR(20)     DEFAULT NULL COMMENT 'OFFLINE/REJECT',
  handler_id    BIGINT UNSIGNED DEFAULT NULL,
  handled_at    DATETIME        DEFAULT NULL,
  created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_status_created (status, created_at),
  KEY idx_item (item_id),
  KEY idx_reporter (reporter_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='举报';

-- ---------------------------------------------------------------- 10. 私信
DROP TABLE IF EXISTS t_chat_message;
CREATE TABLE t_chat_message (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  from_user_id BIGINT UNSIGNED NOT NULL,
  to_user_id   BIGINT UNSIGNED NOT NULL,
  item_id      BIGINT UNSIGNED DEFAULT NULL,
  content      VARCHAR(500)    NOT NULL,
  is_read      TINYINT         NOT NULL DEFAULT 0,
  created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_pair_1 (from_user_id, to_user_id, id),
  KEY idx_pair_2 (to_user_id, from_user_id, id),
  KEY idx_unread (to_user_id, is_read)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='私信';

-- ---------------------------------------------------------------- 11. AI 调用日志
DROP TABLE IF EXISTS t_ai_estimate_log;
CREATE TABLE t_ai_estimate_log (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id      BIGINT UNSIGNED NOT NULL,
  scene        VARCHAR(20)     NOT NULL COMMENT 'ESTIMATE/COPY',
  source       VARCHAR(10)     NOT NULL COMMENT 'LLM/RULE',
  model        VARCHAR(50)     DEFAULT NULL,
  input_digest VARCHAR(255)    DEFAULT NULL,
  success      TINYINT         NOT NULL DEFAULT 1,
  cost_ms      INT             NOT NULL DEFAULT 0,
  biz_date     DATE            NOT NULL COMMENT '自然日，限流按此 count',
  created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_date (user_id, biz_date),
  KEY idx_date (biz_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 调用日志';

-- ---------------------------------------------------------------- 应用账号
-- 后端默认读取 DB_USERNAME / DB_PASSWORD（缺省 brix / brix123）。
-- 若尚未创建该账号，用 root 执行以下三条语句（密码请按需修改，并同步到环境变量）：
--   CREATE USER IF NOT EXISTS 'brix'@'localhost' IDENTIFIED BY 'brix123';
--   GRANT ALL PRIVILEGES ON brix_campus.* TO 'brix'@'localhost';
--   FLUSH PRIVILEGES;
