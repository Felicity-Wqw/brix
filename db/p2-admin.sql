-- ============================================================================
-- P2 管理后台种子账号（双角色）
--
-- 角色口径（最小权限原则，见 RoleEnum）：
--   REVIEWER 审核员 —— 商品审核队列、举报处理。只做「内容判断」，不碰资金与用户。
--   OPERATOR 运营   —— 数据看板、用户管理、订单/分类维护。只做「平台经营」，不单独裁决内容。
--   USER     普通用户 —— 前台交易，无任何 /admin 权限。
--
-- 账号（仅用于本地演示与答辩演示，生产环境必须改口令或改为邀请制开户）：
--   admin_review / admin123456   审核员
--   admin_ops    / admin123456   运营
--
-- 密码为 BCrypt(strength=10, $2a$) 密文，对应明文 admin123456。
-- 该口令仅满足本地演示；上线前务必通过后台改密或直接 UPDATE 替换密文。
--
-- 幂等：ON DUPLICATE KEY UPDATE 已存在则重置口令与角色，可重复执行。
-- 依赖：需先执行 db/schema.sql 与 db/p2-migration.sql。
-- ============================================================================

USE brix_campus;

INSERT INTO t_user
  (username, password, nickname, avatar, school, campus, phone, bio, role, status, credit_score, deleted)
VALUES
  ('admin_review',
   '$2a$10$8J8G2.pbIIHFZc4u7Xu9DO28VOf4Kcj5T6msZ4914UrAQ/otNNhgC',
   '内容审核员', '', NULL, NULL, NULL, '负责商品审核与举报处理', 'REVIEWER', 'NORMAL', 100, 0),
  ('admin_ops',
   '$2a$10$UjQR5GVvBk/FcaRDvRlFx.dudPrM0O1cr8.Qpp87hryHnOQKNIJkK',
   '平台运营', '', NULL, NULL, NULL, '负责数据看板、用户与订单管理', 'OPERATOR', 'NORMAL', 100, 0)
AS new
ON DUPLICATE KEY UPDATE
  password     = new.password,
  nickname     = new.nickname,
  role         = new.role,
  status       = 'NORMAL',
  credit_score = 100,
  deleted      = 0;

-- 校验：应输出 2 行，role 分别为 REVIEWER / OPERATOR
SELECT id, username, nickname, role, status FROM t_user WHERE role IN ('REVIEWER', 'OPERATOR');
