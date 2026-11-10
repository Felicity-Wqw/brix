-- ============================================================================
-- P2 增量迁移脚本（幂等，可重复执行）
--
-- 设计原则：
--   1) 只加不删：不 DROP、不改列类型、不重命名，老数据无感；
--   2) 全部字段可空或带默认值，历史行不需要回填；
--   3) 每条 ALTER 前先查 information_schema，重复执行不会报 "Duplicate column"；
--   4) 不改 db/schema.sql（那是重建式脚本，重跑会清库），本文件是升级用。
--
-- 执行方式：
--   mysql -u root -p --default-character-set=utf8mb4 < db/p2-migration.sql
--   或在 Navicat / DataGrip 中整段执行。
--
-- 本次内容（P2-1 校区体系 + 统计预留）：
--   A. 新建 t_school / t_campus
--   B. t_item / t_user 增加 school_id、campus_id（筛选与展示）
--   C. t_order 增加 school_id、campus_id（统计快照，避免 join 商品表）
-- ============================================================================

USE brix_campus;

-- ---------------------------------------------------------------- A1. 学校表
CREATE TABLE IF NOT EXISTS t_school (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  name        VARCHAR(100)    NOT NULL                COMMENT '学校全称，唯一',
  short_name  VARCHAR(50)     DEFAULT NULL            COMMENT '简称',
  level       VARCHAR(20)     DEFAULT NULL            COMMENT '层次：本科/专科（统计维度）',
  nature      VARCHAR(20)     DEFAULT NULL            COMMENT '性质：公办/民办（统计维度）',
  city        VARCHAR(50)     NOT NULL DEFAULT '桂林市' COMMENT '所在城市',
  district    VARCHAR(50)     DEFAULT NULL            COMMENT '主校区所在区县（统计维度）',
  sort        INT             NOT NULL DEFAULT 0      COMMENT '排序值，升序展示',
  enabled     TINYINT         NOT NULL DEFAULT 1      COMMENT '1=启用 0=停用',
  deleted     TINYINT         NOT NULL DEFAULT 0      COMMENT '逻辑删除 0/1',
  created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_school_name (name),
  KEY idx_level_nature (level, nature),
  KEY idx_district (district),
  KEY idx_enabled_sort (enabled, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学校（P2 校区体系）';

-- ---------------------------------------------------------------- A2. 校区表
CREATE TABLE IF NOT EXISTS t_campus (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  school_id   BIGINT UNSIGNED NOT NULL                COMMENT '所属学校 ID',
  name        VARCHAR(100)    NOT NULL                COMMENT '校区名，同校内唯一',
  address     VARCHAR(200)    DEFAULT NULL            COMMENT '详细地址',
  district    VARCHAR(50)     DEFAULT NULL            COMMENT '所在区县（统计维度）',
  lat         DECIMAL(10,7)   DEFAULT NULL            COMMENT '纬度 GCJ-02（定位用，可空）',
  lng         DECIMAL(10,7)   DEFAULT NULL            COMMENT '经度 GCJ-02（定位用，可空）',
  radius_m    INT             NOT NULL DEFAULT 1500   COMMENT '定位判定半径（米）',
  is_main     TINYINT         NOT NULL DEFAULT 0      COMMENT '1=主校区',
  sort        INT             NOT NULL DEFAULT 0      COMMENT '排序值',
  enabled     TINYINT         NOT NULL DEFAULT 1      COMMENT '1=启用 0=停用',
  deleted     TINYINT         NOT NULL DEFAULT 0      COMMENT '逻辑删除 0/1',
  created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_campus_school_name (school_id, name),
  KEY idx_school (school_id, sort),
  KEY idx_district (district),
  KEY idx_lat_lng (lat, lng)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='校区（P2 校区体系）';

-- ---------------------------------------------------------------- B. 商品/用户加列
-- t_item.school_id / campus_id：筛选条件；campus（字符串）保留作历史快照与展示
SET @sql = (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_item' AND COLUMN_NAME = 'school_id') = 0,
  'ALTER TABLE t_item ADD COLUMN school_id BIGINT UNSIGNED DEFAULT NULL COMMENT ''所属学校 ID'' AFTER campus',
  'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_item' AND COLUMN_NAME = 'campus_id') = 0,
  'ALTER TABLE t_item ADD COLUMN campus_id BIGINT UNSIGNED DEFAULT NULL COMMENT ''所属校区 ID'' AFTER school_id',
  'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_item' AND INDEX_NAME = 'idx_school_campus') = 0,
  'ALTER TABLE t_item ADD KEY idx_school_campus (school_id, campus_id)',
  'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_user' AND COLUMN_NAME = 'school_id') = 0,
  'ALTER TABLE t_user ADD COLUMN school_id BIGINT UNSIGNED DEFAULT NULL COMMENT ''所属学校 ID'' AFTER campus',
  'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_user' AND COLUMN_NAME = 'campus_id') = 0,
  'ALTER TABLE t_user ADD COLUMN campus_id BIGINT UNSIGNED DEFAULT NULL COMMENT ''所属校区 ID'' AFTER school_id',
  'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------- C. 订单统计快照
-- 为什么订单也要存：商品可能被删除或改归属，历史成交统计必须保留下单时的事实；
-- 有了这两列，"各学校交易量"直接对 t_order 聚合，无需 join t_item。
SET @sql = (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_order' AND COLUMN_NAME = 'school_id') = 0,
  'ALTER TABLE t_order ADD COLUMN school_id BIGINT UNSIGNED DEFAULT NULL COMMENT ''下单时商品所属学校（统计快照）'' AFTER item_price',
  'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_order' AND COLUMN_NAME = 'campus_id') = 0,
  'ALTER TABLE t_order ADD COLUMN campus_id BIGINT UNSIGNED DEFAULT NULL COMMENT ''下单时商品所属校区（统计快照）'' AFTER school_id',
  'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_order' AND INDEX_NAME = 'idx_order_school_status') = 0,
  'ALTER TABLE t_order ADD KEY idx_order_school_status (school_id, status)',
  'SELECT 1'));
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ---------------------------------------------------------------- 兼容性说明
-- 1) 历史商品的 school_id / campus_id 为 NULL：
--    选「全部学校」时照常展示，选具体学校时被过滤掉（它们本就没有学校归属）。
-- 2) t_item.campus / t_user.campus 字符串列保留：
--    订单、快照、历史展示继续用，不因校区改名而变化。
-- 3) 本脚本不修改 db/schema.sql。全新环境请先执行 schema.sql + data.sql，
--    再执行本文件；已有环境直接执行本文件即可。
