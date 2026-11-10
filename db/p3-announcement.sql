-- ============================================================
-- 平台公告：首页公告轮播文字，运营端维护
-- 执行方式：mysql -ubrix -p brix_campus < db/p3-announcement.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS t_announcement (
  id BIGINT NOT NULL AUTO_INCREMENT,
  title VARCHAR(60) NOT NULL COMMENT '公告标题',
  content VARCHAR(200) NOT NULL COMMENT '公告内容',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '1 上架 / 0 下架',
  sort INT NOT NULL DEFAULT 0 COMMENT '排序，越小越靠前',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_ann_enabled (enabled, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台公告';

-- 演示数据
INSERT INTO t_announcement (title, content, enabled, sort) VALUES
('开学季活动', '开学季闲置集市每周六在中心广场开摊，欢迎来淘好物！', 1, 0),
('交易提醒', '当面交易请选择人多的公共场所，验货后再付款，谨防线下转账。', 1, 1),
('新功能上线', '求购墙已上线：想要什么发一条求购，让卖家来找你！', 1, 2);
