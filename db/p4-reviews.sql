-- ============================================================================
-- 评价种子 + 信用分反算（可重复执行：INSERT IGNORE 不覆盖已有真实评价）
-- 目的：让信用分「长」在真实评价上——每条评价对应一笔已完成订单，
--       信用分 = 100 + Σ(4-5星 +2 / 3星 +1 / 1-2星 -1)，clamp [0,200]，
--       与 ReviewServiceImpl.adjustCreditScore 的线上规则完全同口径。
-- 执行：mysql -ubrix -p brix_campus < db/p4-reviews.sql
-- ============================================================================
USE brix_campus;

-- 1. 为所有已完成订单补评价（一单一条；已存在的订单评价跳过，保护真实数据）
INSERT IGNORE INTO t_review (order_id, item_id, buyer_id, seller_id, rating, content, images, created_at)
SELECT
  t.order_id,
  t.item_id,
  t.buyer_id,
  t.seller_id,
  t.rating,
  IF(t.rating >= 4,
     ELT(1 + (t.order_id % 6),
         '宝贝和描述完全一致，验机很顺利，卖家人也爽快，好评！',
         '沟通很及时，当面验完才付款，很安心的交易。',
         '成色比照片还好一点，价格也实在，推荐给同学了。',
         '交易很顺利，卖家还主动介绍了使用情况，靠谱。',
         '东西很新，性价比高，以后有需要还来。',
         '卖家很守时，东西没有任何暗病，五星。'),
     ELT(1 + (t.order_id % 3),
         '东西能用，但成色比描述的旧一些，整体还行。',
         '交易过程没问题，只是约时间来回改了几次。',
         '基本符合预期，有小瑕疵但卖家提前说明了。')),
  '',
  t.finish_time
FROM (
  SELECT
    o.id AS order_id,
    o.item_id,
    o.buyer_id,
    o.seller_id,
    o.finish_time,
    CASE
      WHEN o.id % 17 = 0 THEN 3          -- 少量中评，分布自然
      WHEN o.id % 5  = 0 THEN 4          -- 部分好评
      ELSE 5                             -- 多数五星
    END AS rating
  FROM t_order o
  WHERE o.status = 'DONE' AND o.deleted = 0
) t;

-- 2. 信用分反算：以评价数据为唯一事实来源，与线上 adjustCreditScore 同口径
UPDATE t_user u
LEFT JOIN (
  SELECT seller_id,
         SUM(CASE WHEN rating >= 4 THEN 2 WHEN rating = 3 THEN 1 ELSE -1 END) AS delta
  FROM t_review
  GROUP BY seller_id
) r ON r.seller_id = u.id
SET u.credit_score = LEAST(200, GREATEST(0, 100 + COALESCE(r.delta, 0)));
