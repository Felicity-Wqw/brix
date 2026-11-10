-- ============================================================================
-- P2 演示数据回填（可选，幂等，可重复执行）
--
-- 背景：
--   P1 阶段的商品/用户用的是占位的「东校区 / 西校区 / 南校区」三个假校区，
--   新加的 school_id / campus_id 全是 NULL。不处理的话：
--     ① 首页「学校 / 校区」筛选永远筛不出东西；
--     ② 后台按学校统计交易量全是空图，答辩演示看不到效果。
--
-- 处理方式：
--   把三个占位校区映射到真实校区（顺带把 campus 名称快照一起改掉，保持自洽），
--   然后把订单/用户按同一映射补齐。只影响 school_id IS NULL 的行，
--   已经手工填过归属的数据不会被覆盖。
--
--   东校区 → 桂林电子科技大学 · 金鸡岭校区
--   西校区 → 桂林理工大学   · 屏风校区
--   南校区 → 广西师范大学   · 育才校区
--
-- 执行方式：
--   mysql -u root -p --default-character-set=utf8mb4 < db/p2-backfill-demo.sql
--
-- 说明：本脚本只为演示数据服务。真实部署请跳过，让用户注册/发布时自己选。
-- ============================================================================

USE brix_campus;

-- ------------------------------------------------------- 1. 商品：东校区 → 桂电金鸡岭
UPDATE t_item i
  JOIN t_campus c ON c.name = '金鸡岭校区' AND c.deleted = 0
  JOIN t_school s ON s.id = c.school_id AND s.name = '桂林电子科技大学'
SET i.school_id = s.id,
    i.campus_id = c.id,
    i.campus    = c.name
WHERE i.school_id IS NULL
  AND i.campus = '东校区';

-- ------------------------------------------------------- 2. 商品：西校区 → 桂工屏风
UPDATE t_item i
  JOIN t_campus c ON c.name = '屏风校区' AND c.deleted = 0
  JOIN t_school s ON s.id = c.school_id AND s.name = '桂林理工大学'
SET i.school_id = s.id,
    i.campus_id = c.id,
    i.campus    = c.name
WHERE i.school_id IS NULL
  AND i.campus = '西校区';

-- ------------------------------------------------------- 3. 商品：南校区 → 广西师大育才
UPDATE t_item i
  JOIN t_campus c ON c.name = '育才校区' AND c.deleted = 0
  JOIN t_school s ON s.id = c.school_id AND s.name = '广西师范大学'
SET i.school_id = s.id,
    i.campus_id = c.id,
    i.campus    = c.name
WHERE i.school_id IS NULL
  AND i.campus = '南校区';

-- --------------------------------------------- 4. 订单：按商品归属补齐统计快照
UPDATE t_order o
  JOIN t_item i ON i.id = o.item_id
SET o.school_id = i.school_id,
    o.campus_id = i.campus_id
WHERE o.school_id IS NULL
  AND i.school_id IS NOT NULL;

-- --------------------------------------------- 5. 用户：同一套映射补齐归属
UPDATE t_user u
  JOIN t_campus c ON c.name = '金鸡岭校区' AND c.deleted = 0
  JOIN t_school s ON s.id = c.school_id AND s.name = '桂林电子科技大学'
SET u.school_id = s.id, u.campus_id = c.id, u.school = s.name, u.campus = c.name
WHERE u.school_id IS NULL AND u.campus = '东校区';

UPDATE t_user u
  JOIN t_campus c ON c.name = '屏风校区' AND c.deleted = 0
  JOIN t_school s ON s.id = c.school_id AND s.name = '桂林理工大学'
SET u.school_id = s.id, u.campus_id = c.id, u.school = s.name, u.campus = c.name
WHERE u.school_id IS NULL AND u.campus = '西校区';

UPDATE t_user u
  JOIN t_campus c ON c.name = '育才校区' AND c.deleted = 0
  JOIN t_school s ON s.id = c.school_id AND s.name = '广西师范大学'
SET u.school_id = s.id, u.campus_id = c.id, u.school = s.name, u.campus = c.name
WHERE u.school_id IS NULL AND u.campus = '南校区';

-- --------------------------------------------------------------- 6. 自检
SELECT '未归属商品' AS metric, COUNT(*) AS cnt FROM t_item WHERE school_id IS NULL
UNION ALL
SELECT '未归属订单', COUNT(*) FROM t_order WHERE school_id IS NULL
UNION ALL
SELECT '未归属用户', COUNT(*) FROM t_user WHERE school_id IS NULL;

-- 各学校商品分布（后台统计的抽样验证）
SELECT s.name AS school, COUNT(i.id) AS item_cnt, IFNULL(SUM(i.price), 0) AS gmv
FROM t_school s
  LEFT JOIN t_item i ON i.school_id = s.id AND i.deleted = 0
GROUP BY s.id
ORDER BY item_cnt DESC;
