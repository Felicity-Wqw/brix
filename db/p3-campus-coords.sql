-- ======================================================================
-- p3-campus-coords.sql
-- 大屏地图前置数据：补齐 7 个非主校区的经纬度（桂林各高校公开位置，
-- 学校级聚合展示用，不涉及个人位置信息）。坐标系 GCJ-02。
-- 幂等：仅更新 lat/lng 为 NULL 的行，已填坐标不覆盖。
-- 执行：mysql -ubrix -p brix_campus --default-character-set=utf8mb4 < p3-campus-coords.sql
-- ======================================================================

UPDATE t_campus SET lat = 25.2830000, lng = 110.3000000 WHERE id = 1  AND lat IS NULL;  -- 广西师大 王城校区（秀峰区王城）
UPDATE t_campus SET lat = 25.2730000, lng = 110.3300000 WHERE id = 2  AND lat IS NULL;  -- 广西师大 育才校区（七星区育才路）
UPDATE t_campus SET lat = 25.0640000, lng = 110.3060000 WHERE id = 3  AND lat IS NULL;  -- 广西师大 雁山校区（雁中路）
UPDATE t_campus SET lat = 25.2770000, lng = 110.3540000 WHERE id = 5  AND lat IS NULL;  -- 桂电 六合路校区（七星区六合路）
UPDATE t_campus SET lat = 25.2850000, lng = 110.2940000 WHERE id = 9  AND lat IS NULL;  -- 桂医 乐群校区（秀峰区乐群路）
UPDATE t_campus SET lat = 25.2905000, lng = 110.3200000 WHERE id = 10 AND lat IS NULL;  -- 桂医 东城校区（七星区环城北二路）
UPDATE t_campus SET lat = 25.2360000, lng = 110.1790000 WHERE id = 11 AND lat IS NULL;  -- 桂医 临桂校区（临桂区致远路）

-- 验证：应返回 0
-- SELECT COUNT(*) FROM t_campus WHERE lat IS NULL OR lng IS NULL;
