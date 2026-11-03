-- ============================================================================
-- Brix Campus Market 校园二手交易平台 —— 种子数据（N08）
-- 使用方式：mysql -u root -p --default-character-set=utf8mb4 brix_campus < db/data.sql
-- 说明：
--   1) 先执行 db/schema.sql；本脚本用 TRUNCATE + 显式 id 写入，可重复导入且结果一致。
--   2) 账号与明文密码：
--        student01 / user123456       （普通用户，演示主号）
--        reviewer01 / reviewer123456  （演示用普通用户）
--        operator01 / operator123456  （演示用普通用户）
--        seller01 ~ seller05 / user123456（5 个卖家，同为普通用户）
--      库中密码均为 BCrypt($2a$) 密文，明文不落库。
--   3) 商品 36 件，状态分布：ON_SALE 28 / SOLD 5 / OFF_SHELF 3。
--      （架构附录 B 建议 24/5/4/3；此处把 1 件在售改为已售出，用于凑出 3 笔已完成订单以演示 3 条评价）
--   4) 图片 URL 指向 /uploads/seed/1~6.jpg 占位图，由脚本按 id 循环生成。
-- ============================================================================

USE brix_campus;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

TRUNCATE TABLE t_user;
TRUNCATE TABLE t_category;
TRUNCATE TABLE t_item;
TRUNCATE TABLE t_item_image;
TRUNCATE TABLE t_favorite;
TRUNCATE TABLE t_item_comment;
TRUNCATE TABLE t_order;
TRUNCATE TABLE t_review;
TRUNCATE TABLE t_report;
TRUNCATE TABLE t_chat_message;
TRUNCATE TABLE t_ai_estimate_log;

-- ------------------------------------------------------------------ 用户（8）
INSERT INTO t_user
(id, username, password, nickname, avatar, school, campus, phone, bio, role, status, credit_score, deleted, created_at, updated_at)
VALUES
(1, 'student01',  '$2a$10$t1r6.fmrwP4HwWwc2cmpcOZsY6M1DBCFpG4u9.3DYS5VjxP1cM0qO', '小林同学', '/uploads/seed/1.jpg', '示例大学', '东校区', '13800000001', '大四在读，数码爱好者，出闲置回血', 'USER',     'NORMAL', 120, 0, DATE_SUB(NOW(), INTERVAL 120 DAY), DATE_SUB(NOW(), INTERVAL 120 DAY)),
(2, 'reviewer01', '$2a$10$t1r6.fmrwP4HwWwc2cmpcOZsY6M1DBCFpG4u9.3DYS5VjxP1cM0qO', '审核员小周', '/uploads/seed/2.jpg', '示例大学', '西校区', '13800000002', '平台内容审核员，负责商品审核与举报处理', 'USER', 'NORMAL', 100, 0, DATE_SUB(NOW(), INTERVAL 110 DAY), DATE_SUB(NOW(), INTERVAL 110 DAY)),
(3, 'operator01', '$2a$10$t1r6.fmrwP4HwWwc2cmpcOZsY6M1DBCFpG4u9.3DYS5VjxP1cM0qO', '运营小陈', '/uploads/seed/3.jpg', '示例大学', '南校区', '13800000003', '平台运营，负责数据与用户管理', 'USER', 'NORMAL', 100, 0, DATE_SUB(NOW(), INTERVAL 105 DAY), DATE_SUB(NOW(), INTERVAL 105 DAY)),
(4, 'seller01',   '$2a$10$t1r6.fmrwP4HwWwc2cmpcOZsY6M1DBCFpG4u9.3DYS5VjxP1cM0qO', '阿哲的数码铺', '/uploads/seed/4.jpg', '示例大学', '东校区', '13800000004', '数码控，出闲置手机平板外设，欢迎面交验机', 'USER', 'NORMAL', 118, 0, DATE_SUB(NOW(), INTERVAL 100 DAY), DATE_SUB(NOW(), INTERVAL 100 DAY)),
(5, 'seller02',   '$2a$10$t1r6.fmrwP4HwWwc2cmpcOZsY6M1DBCFpG4u9.3DYS5VjxP1cM0qO', '悦悦二手书', '/uploads/seed/5.jpg', '示例大学', '西校区', '13800000005', '考研上岸，全套资料低价转让', 'USER', 'NORMAL', 112, 0, DATE_SUB(NOW(), INTERVAL 95 DAY), DATE_SUB(NOW(), INTERVAL 95 DAY)),
(6, 'seller03',   '$2a$10$t1r6.fmrwP4HwWwc2cmpcOZsY6M1DBCFpG4u9.3DYS5VjxP1cM0qO', '老李杂货', '/uploads/seed/6.jpg', '示例大学', '南校区', '13800000006', '宿舍用品、小家电清仓，价格都好商量', 'USER', 'NORMAL', 105, 0, DATE_SUB(NOW(), INTERVAL 90 DAY), DATE_SUB(NOW(), INTERVAL 90 DAY)),
(7, 'seller04',   '$2a$10$t1r6.fmrwP4HwWwc2cmpcOZsY6M1DBCFpG4u9.3DYS5VjxP1cM0qO', '阿May', '/uploads/seed/1.jpg', '示例大学', '东校区', '13800000007', '美妆护肤闲置，均为正品自用', 'USER', 'NORMAL', 98, 0, DATE_SUB(NOW(), INTERVAL 80 DAY), DATE_SUB(NOW(), INTERVAL 80 DAY)),
(8, 'seller05',   '$2a$10$t1r6.fmrwP4HwWwc2cmpcOZsY6M1DBCFpG4u9.3DYS5VjxP1cM0qO', '篮球少年', '/uploads/seed/2.jpg', '示例大学', '西校区', '13800000008', '运动装备爱好者，球鞋球拍为主', 'USER', 'NORMAL', 110, 0, DATE_SUB(NOW(), INTERVAL 70 DAY), DATE_SUB(NOW(), INTERVAL 70 DAY));

-- ---------------------------------------------------------------- 分类（6）
INSERT INTO t_category (id, name, icon, sort, deleted, created_at, updated_at) VALUES
(1, '数码电子', 'Cellphone',   1, 0, DATE_SUB(NOW(), INTERVAL 120 DAY), DATE_SUB(NOW(), INTERVAL 120 DAY)),
(2, '电脑办公', 'Monitor',     2, 0, DATE_SUB(NOW(), INTERVAL 120 DAY), DATE_SUB(NOW(), INTERVAL 120 DAY)),
(3, '图书教材', 'Notebook',    3, 0, DATE_SUB(NOW(), INTERVAL 120 DAY), DATE_SUB(NOW(), INTERVAL 120 DAY)),
(4, '生活用品', 'House',       4, 0, DATE_SUB(NOW(), INTERVAL 120 DAY), DATE_SUB(NOW(), INTERVAL 120 DAY)),
(5, '运动户外', 'Basketball',  5, 0, DATE_SUB(NOW(), INTERVAL 120 DAY), DATE_SUB(NOW(), INTERVAL 120 DAY));
(34, '乐器',      'Headset',     7,  0, DATE_SUB(NOW(), INTERVAL 120 DAY), DATE_SUB(NOW(), INTERVAL 120 DAY)),
(35, '服饰鞋包',  'ShoppingBag', 8,  0, DATE_SUB(NOW(), INTERVAL 120 DAY), DATE_SUB(NOW(), INTERVAL 120 DAY)),
(36, '代步工具',  'Bicycle',     9,  0, DATE_SUB(NOW(), INTERVAL 120 DAY), DATE_SUB(NOW(), INTERVAL 120 DAY));

-- --------------------------------------------------------------- 商品（36）
INSERT INTO t_item
(id, title, description, category_id, price, original_price, condition_level, campus, trade_type, contact, status, cover_image,
 view_count, favorite_count, brand_model, seller_id, deleted, created_at, updated_at)
VALUES
-- 数码电子（分类 1）
(1, 'iPad 9代 64G WiFi版 国行深空灰', '2021 年购入，国行 64G WiFi 版，日常看剧记笔记用。屏幕无划痕，电池健康 88%，配原装充电器和保护壳，可当面验机。', 1, 1280.00, 2499.00, 'LIKE_NEW', '东校区', 'BOTH', '微信 aizhe_digital', 'ON_SALE', '', 386, 24, 'iPad', 4, 0, DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_SUB(NOW(), INTERVAL 20 DAY)),
(2, 'AirPods Pro 2 USB-C 全套带发票', '耳机 2023 年 11 月购入，主动降噪正常，耳塞已更换全新，含原装盒与发票，无异味。', 1, 890.00, 1899.00, 'LIKE_NEW', '东校区', 'MEET', '微信 aizhe_digital', 'ON_SALE', '', 254, 18, 'AirPods', 4, 0, DATE_SUB(NOW(), INTERVAL 19 DAY), DATE_SUB(NOW(), INTERVAL 19 DAY)),
(3, '小米 13 12+256 黑色 已过保', '自用主力机，屏幕无烧屏，边框有轻微使用痕迹，功能全正常，附原装 67W 充电器，可当面测试。', 1, 1650.00, 3999.00, 'LIGHT_USE', '南校区', 'BOTH', 'QQ 88223311', 'ON_SALE', '', 312, 15, '小米', 7, 0, DATE_SUB(NOW(), INTERVAL 18 DAY), DATE_SUB(NOW(), INTERVAL 18 DAY)),
(4, 'Kindle Paperwhite 5 8G 带原装保护套', '看书神器，屏幕无划痕，电池待机两周以上，附原装磁吸保护套，适合考研党通勤看资料。', 1, 420.00, 998.00, 'LIGHT_USE', '西校区', 'MEET', '微信 kindle_lover', 'ON_SALE', '', 176, 9, 'Kindle', 8, 0, DATE_SUB(NOW(), INTERVAL 17 DAY), DATE_SUB(NOW(), INTERVAL 17 DAY)),
(5, '罗技 MX Master 3S 无线鼠标 石墨黑', '办公神器，静音按键，滚轮顺滑，附 Type-C 充电线，无磕碰。', 1, 380.00, 799.00, 'LIKE_NEW', '东校区', 'BOTH', '微信 aizhe_digital', 'ON_SALE', '', 143, 11, '罗技', 4, 0, DATE_SUB(NOW(), INTERVAL 16 DAY), DATE_SUB(NOW(), INTERVAL 16 DAY)),
(6, '索尼 WH-1000XM4 头戴降噪耳机', '降噪效果依旧出色，耳罩有轻微使用痕迹（已更换新耳罩），附收纳盒与音频线。', 1, 1050.00, 2299.00, 'HEAVY_USE', '东校区', 'SHIP', '微信 may_audio', 'SOLD', '', 268, 20, '索尼', 7, 0, DATE_SUB(NOW(), INTERVAL 15 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY)),
-- 电脑办公（分类 2）
(7, '红米 27 寸 2K 显示器 165Hz', '毕业出显示器，2K 165Hz IPS 屏，无亮点坏点，附原装支架与 DP 线，宿舍桌面提升明显。', 2, 420.00, 999.00, 'LIKE_NEW', '南校区', 'BOTH', '微信 laoli_shop', 'ON_SALE', '', 402, 25, '红米', 6, 0, DATE_SUB(NOW(), INTERVAL 15 DAY), DATE_SUB(NOW(), INTERVAL 15 DAY)),
(8, '联想小新 Air14 2021 锐龙5 16+512', '写代码看文献够用，电池健康 82%，键盘无失灵，附原装 65W 充电器，可当面跑分。', 2, 1980.00, 4499.00, 'LIGHT_USE', '东校区', 'BOTH', '微信 may_laptop', 'ON_SALE', '', 331, 16, '联想', 7, 0, DATE_SUB(NOW(), INTERVAL 14 DAY), DATE_SUB(NOW(), INTERVAL 14 DAY)),
(9, 'HHKB Pro2 静电容键盘 白色 有刻', '静电容手感，程序员福音，键帽无发黄，附原装数据线。', 2, 1200.00, 2380.00, 'LIKE_NEW', '东校区', 'MEET', '微信 aizhe_digital', 'ON_SALE', '', 187, 13, 'HHKB', 4, 0, DATE_SUB(NOW(), INTERVAL 13 DAY), DATE_SUB(NOW(), INTERVAL 13 DAY)),
(10, '罗技 K380 蓝牙键盘 深灰', '可连三台设备，按键手感清脆，适合平板/笔记本外接，轻微使用痕迹。', 2, 95.00, 219.00, 'LIGHT_USE', '南校区', 'MEET', '微信 laoli_shop', 'ON_SALE', '', 96, 4, '罗技', 6, 0, DATE_SUB(NOW(), INTERVAL 12 DAY), DATE_SUB(NOW(), INTERVAL 12 DAY)),
(11, '机械革命 27寸 165Hz 电竞屏', '屏幕右上角有轻微漏光，不影响使用，因换 4K 屏出掉，仅限自提。', 2, 680.00, 1499.00, 'HEAVY_USE', '南校区', 'SHIP', '微信 laoli_shop', 'OFF_SHELF', '', 88, 2, '机械革命', 6, 0, DATE_SUB(NOW(), INTERVAL 11 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)),
(12, '小米无线鼠标 2 代 白色', '备用鼠标，买多了闲置，功能正常。', 2, 35.00, 69.00, 'LIGHT_USE', '南校区', 'MEET', '微信 laoli_shop', 'ON_SALE', '', 12, 0, '小米', 6, 0, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),
-- 图书教材（分类 3）
(13, '张宇考研数学全套 36讲+1000题+8套卷', '2024 版全套，做过部分题（用铅笔标注已擦），无缺页无水渍，可拆卖。', 3, 85.00, 320.00, 'LIGHT_USE', '西校区', 'MEET', 'QQ 55667788', 'ON_SALE', '', 358, 21, NULL, 5, 0, DATE_SUB(NOW(), INTERVAL 11 DAY), DATE_SUB(NOW(), INTERVAL 11 DAY)),
(14, '肖秀荣考研政治 1000题+背诵手册', '8 成新，重点部分有荧光笔标记，适合二战同学。', 3, 45.00, 89.00, 'LIGHT_USE', '西校区', 'MEET', 'QQ 55667788', 'ON_SALE', '', 210, 12, NULL, 5, 0, DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 10 DAY)),
(15, '大学英语四级真题 2024年12月版', '只做过 2 套听力，其余全新，附赠词汇书。', 3, 25.00, 59.00, 'LIGHT_USE', '西校区', 'MEET', 'QQ 55667788', 'ON_SALE', '', 132, 6, NULL, 5, 0, DATE_SUB(NOW(), INTERVAL 9 DAY), DATE_SUB(NOW(), INTERVAL 9 DAY)),
(16, '深入理解计算机系统 CSAPP 第三版', '正版影印版，书角有轻微磨损，内页干净无笔记。', 3, 78.00, 139.00, 'LIKE_NEW', '西校区', 'MEET', 'QQ 55667788', 'ON_SALE', '', 164, 10, NULL, 5, 0, DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_SUB(NOW(), INTERVAL 8 DAY)),
(17, '算法导论 中文第三版 机械工业', '经典教材，内页有少量铅笔笔记，可擦除。', 3, 65.00, 128.00, 'LIGHT_USE', '西校区', 'BOTH', 'QQ 55667788', 'SOLD', '', 145, 7, NULL, 5, 0, DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY)),
(18, '会计学原理 第23版 中文版', '课程教材，部分章节有划线标注。', 3, 55.00, 96.00, 'HEAVY_USE', '西校区', 'MEET', 'QQ 55667788', 'ON_SALE', '', 9, 0, NULL, 5, 0, DATE_SUB(NOW(), INTERVAL 20 HOUR), DATE_SUB(NOW(), INTERVAL 20 HOUR)),
-- 生活用品（分类 4）
(19, '小米台灯 Pro 护眼版 白色', '多档调光，无频闪，毕业出，附原装电源适配器。', 4, 120.00, 249.00, 'LIKE_NEW', '南校区', 'MEET', '微信 laoli_shop', 'ON_SALE', '', 121, 8, '小米', 6, 0, DATE_SUB(NOW(), INTERVAL 7 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY)),
(20, '宜家小推车 三层 白色 宿舍神器', '收纳零食书籍都合适，轮子顺滑无损坏，需自提。', 4, 65.00, 149.00, 'LIGHT_USE', '南校区', 'MEET', '微信 laoli_shop', 'ON_SALE', '', 118, 7, '宜家', 6, 0, DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_SUB(NOW(), INTERVAL 6 DAY)),
(21, '苏泊尔电热水壶 1.8L 不锈钢', '烧水快，自动断电正常，壶身无水垢，已清洁消毒。', 4, 45.00, 119.00, 'LIGHT_USE', '南校区', 'MEET', '微信 laoli_shop', 'SOLD', '', 87, 3, '苏泊尔', 6, 0, DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY)),
(22, '收纳箱 60L 两个装 带滑轮', '搬宿舍神器，两个一起出，盖子完好。', 4, 40.00, 99.00, 'LIGHT_USE', '南校区', 'MEET', '微信 laoli_shop', 'OFF_SHELF', '', 64, 1, NULL, 6, 0, DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),
(23, '宿舍迷你冰箱 40L 静音款', '制冷正常，噪音小，卖家主动下架（已送同学）。', 4, 260.00, 599.00, 'HEAVY_USE', '南校区', 'MEET', '微信 laoli_shop', 'OFF_SHELF', '', 156, 5, NULL, 6, 0, DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)),
(24, '小米加湿器 4L 上加水', '静音大容量，滤芯已换新，宿舍干燥必备。', 4, 70.00, 169.00, 'LIKE_NEW', '南校区', 'MEET', '微信 laoli_shop', 'ON_SALE', '', 15, 0, '小米', 6, 0, DATE_SUB(NOW(), INTERVAL 10 HOUR), DATE_SUB(NOW(), INTERVAL 10 HOUR)),
-- 运动户外（分类 5）
(25, '斯伯丁 74-602Y 篮球 室内外两用', '打了半年，纹路清晰，气足无变形，送打气筒。', 5, 120.00, 259.00, 'LIGHT_USE', '西校区', 'MEET', 'QQ 11223344', 'ON_SALE', '', 198, 11, '斯伯丁', 8, 0, DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY)),
(26, '耐克 Air Zoom 跑鞋 42码 黑橙', '累计跑步不到 200 公里，鞋底磨损轻，已清洗。', 5, 320.00, 799.00, 'LIKE_NEW', '西校区', 'BOTH', 'QQ 11223344', 'ON_SALE', '', 224, 14, '耐克', 8, 0, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY)),
(27, '迪卡侬羽毛球拍 双拍装 带球', '入门双拍，附赠 3 个球，拍面无线，手胶已换新。', 5, 90.00, 199.00, 'LIGHT_USE', '西校区', 'MEET', 'QQ 11223344', 'ON_SALE', '', 105, 5, '迪卡侬', 8, 0, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY)),
(28, '瑜伽垫 加厚 10mm TPE 防滑', '练了两个月，无破损，附收纳背带。', 5, 45.00, 99.00, 'LIKE_NEW', '西校区', 'MEET', 'QQ 11223344', 'ON_SALE', '', 78, 3, NULL, 8, 0, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)),
(29, '山地自行车 26寸 21速 毕业出', '骑行两年，变速与刹车正常，轮胎刚换过，仅限东校区自提。', 5, 480.00, 1299.00, 'HEAVY_USE', '东校区', 'MEET', 'QQ 11223344', 'SOLD', '', 402, 19, NULL, 8, 0, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)),
(30, '骆驼帐篷 3-4人 自动速开', '露营用过两次，防水正常，附地钉与收纳袋，支持邮寄。', 5, 180.00, 399.00, 'LIGHT_USE', '西校区', 'SHIP', 'QQ 11223344', 'ON_SALE', '', 143, 9, '骆驼', 8, 0, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)),
-- 美妆个护（分类 6）
(31, '兰蔻小黑瓶精华 100ml 全新未拆封', '专柜购入，全新未拆封，有专柜小票，日期新鲜。', 6, 550.00, 1080.00, 'BRAND_NEW', '东校区', 'MEET', '微信 may_beauty', 'ON_SALE', '', 302, 22, '兰蔻', 7, 1, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)),
(32, '戴森吹风机 HD08 紫红色', '自用一年，风嘴齐全，包装完整，功能正常，可当面试用。', 6, 1650.00, 2990.00, 'LIKE_NEW', '东校区', 'BOTH', '微信 may_beauty', 'ON_SALE', '', 268, 17, '戴森', 7, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),
(33, '飞利浦电动牙刷 HX6730 附刷头 3 个', '机身正常，已更换全新刷头，附充电底座。', 6, 180.00, 499.00, 'LIGHT_USE', '东校区', 'MEET', '微信 may_beauty', 'ON_SALE', '', 96, 4, '飞利浦', 7, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),
(34, '雅诗兰黛小棕瓶 50ml 剩余约 80%', '去年购入，剩余约八成，介意者慎拍。', 6, 260.00, 780.00, 'LIGHT_USE', '东校区', 'MEET', '微信 may_beauty', 'ON_SALE', '', 18, 1, '雅诗兰黛', 7, 1, DATE_SUB(NOW(), INTERVAL 6 HOUR), DATE_SUB(NOW(), INTERVAL 6 HOUR)),
(35, '香奈儿口红 58号 全新未使用', '专柜正品，色号不适合，全新未使用，附包装盒。', 6, 260.00, 360.00, 'BRAND_NEW', '东校区', 'MEET', '微信 may_beauty', 'ON_SALE', '', 21, 2, '香奈儿', 7, 1, DATE_SUB(NOW(), INTERVAL 4 HOUR), DATE_SUB(NOW(), INTERVAL 4 HOUR)),
(36, '米家电动剃须刀 S500 附清洁刷', '用了几次，已消毒清洁，功能正常。', 6, 79.00, 199.00, 'LIGHT_USE', '东校区', 'MEET', '微信 may_beauty', 'SOLD', '', 132, 6, '米家', 7, 1, DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY));

-- ------------------------------------------- 商品图片（每件 2~3 张，循环占位图）
INSERT INTO t_item_image (item_id, url, sort, created_at)
SELECT id, CONCAT('/uploads/seed/', ((id - 1) % 6) + 1, '.jpg'), 0, created_at FROM t_item;

INSERT INTO t_item_image (item_id, url, sort, created_at)
SELECT id, CONCAT('/uploads/seed/', ((id + 1) % 6) + 1, '.jpg'), 1, created_at FROM t_item;

INSERT INTO t_item_image (item_id, url, sort, created_at)
SELECT id, CONCAT('/uploads/seed/', ((id + 3) % 6) + 1, '.jpg'), 2, created_at FROM t_item WHERE id % 2 = 0;

-- 首图与 sort=0 的图片保持一致
UPDATE t_item SET cover_image = CONCAT('/uploads/seed/', ((id - 1) % 6) + 1, '.jpg');

-- ------------------------------------------------------------- 收藏（4 条）
INSERT INTO t_favorite (id, user_id, item_id, created_at) VALUES
(1, 1, 2,  DATE_SUB(NOW(), INTERVAL 15 DAY)),
(2, 1, 7,  DATE_SUB(NOW(), INTERVAL 12 DAY)),
(3, 1, 13, DATE_SUB(NOW(), INTERVAL 9 DAY)),
(4, 8, 1,  DATE_SUB(NOW(), INTERVAL 6 DAY));

-- ------------------------------------------------------------- 留言（5 条）
INSERT INTO t_item_comment (id, item_id, user_id, parent_id, content, deleted, created_at, updated_at) VALUES
(1, 1, 5, NULL, '同学，iPad 是国行还是港版？电池健康多少呢？', 0, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY)),
(2, 1, 4, 1,    '国行，电池健康 88%，可以当面用软件测给你看。', 0, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY)),
(3, 1, 8, NULL, '在东校区的话今天下午能面交吗？', 0, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)),
(4, 7, 1, NULL, '显示器有坏点吗？能不能带 DP 线一起出？', 0, DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY)),
(5, 7, 6, 4,    '无坏点，DP 线可以一起给你。', 0, DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY));

-- ------------------------------------------------------------- 订单（7 条）
INSERT INTO t_order
(id, order_no, item_id, buyer_id, seller_id, amount, item_title, item_cover, item_price, trade_type, status,
 pay_method, pay_time, ship_time, finish_time, cancel_time, close_reason,
 meet_place, buyer_note, deleted, created_at, updated_at)
VALUES
-- ① 面交待完成（买卖双方线下见面交易）
(1, '20261021101533000101', 29, 1, 8, 480.00, '山地自行车 26寸 21速 毕业出', '/uploads/seed/5.jpg', 480.00, 'MEET', 'TO_MEET',
 'WECHAT', DATE_SUB(NOW(), INTERVAL 2 DAY), NULL, NULL, NULL, NULL,
 '东校区 3 号宿舍楼下', '周六下午三点前都可以', 0, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)),
-- ② 邮寄待发货
(2, '20261020094512000102', 6, 1, 7, 1050.00, '索尼 WH-1000XM4 头戴降噪耳机', '/uploads/seed/6.jpg', 1050.00, 'SHIP', 'TO_SHIP',
 'ALIPAY', DATE_SUB(NOW(), INTERVAL 3 DAY), NULL, NULL, NULL, NULL,
 NULL, '请寄到西校区菜鸟驿站，收件人小林', 0, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY)),
-- ③ 已完成（供评价演示）
(3, '20261019153044000103', 17, 1, 5, 65.00, '算法导论 中文第三版 机械工业', '/uploads/seed/6.jpg', 65.00, 'MEET', 'DONE',
 'WECHAT', DATE_SUB(NOW(), INTERVAL 4 DAY), NULL, DATE_SUB(NOW(), INTERVAL 3 DAY), NULL, NULL,
 '西校区图书馆门口', NULL, 0, DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY)),
-- ④ 已完成（另一位买家的订单，供评价演示）
(4, '20261018102033000104', 36, 8, 7, 79.00, '米家电动剃须刀 S500 附清洁刷', '/uploads/seed/1.jpg', 79.00, 'MEET', 'DONE',
 'BANK', DATE_SUB(NOW(), INTERVAL 5 DAY), NULL, DATE_SUB(NOW(), INTERVAL 4 DAY), NULL, NULL,
 '东校区食堂二层', NULL, 0, DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY)),
-- ⑤ 已完成（第三条评价，凑出 5/4/3 三种评分）
(5, '20261017091022000105', 21, 1, 6, 45.00, '苏泊尔电热水壶 1.8L 不锈钢', '/uploads/seed/3.jpg', 45.00, 'MEET', 'DONE',
 'ALIPAY', DATE_SUB(NOW(), INTERVAL 6 DAY), NULL, DATE_SUB(NOW(), INTERVAL 5 DAY), NULL, NULL,
 '南校区快递站', NULL, 0, DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY)),
-- ⑥ 已取消（超时未支付，验证状态渲染）
(6, '20261022083011000106', 4, 1, 8, 420.00, 'Kindle Paperwhite 5 8G 带原装保护套', '/uploads/seed/4.jpg', 420.00, 'MEET', 'CANCELED',
 NULL, NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 1 DAY), 'TIMEOUT',
 '西校区 1 号宿舍楼下', NULL, 0, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),
-- ⑦ 待付款（5 分钟前下单，可直接演示收银台倒计时）
(7, '20261023120000000107', 1, 1, 4, 1280.00, 'iPad 9代 64G WiFi版 国行深空灰', '/uploads/seed/1.jpg', 1280.00, 'MEET', 'UNPAID',
 NULL, NULL, NULL, NULL, NULL, NULL,
 '东校区图书馆门口', '希望今天能面交', 0, DATE_SUB(NOW(), INTERVAL 5 MINUTE), DATE_SUB(NOW(), INTERVAL 5 MINUTE));

-- ------------------------------------------------------------- 评价（3 条）
INSERT INTO t_review (id, order_id, item_id, buyer_id, seller_id, rating, content, images, created_at) VALUES
(1, 3, 17, 1, 5, 5, '书保存得很好，几乎全新，学姐人也很好，约在图书馆门口几分钟就到了，推荐！', '[]', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(2, 4, 36, 8, 7, 4, '剃须刀功能正常，清洁得挺干净，就是约时间等了半天，整体还是满意的。', '[]', DATE_SUB(NOW(), INTERVAL 4 DAY)),
(3, 5, 21, 1, 6, 3, '水壶能用，但壶身有一处小凹痕，卖家事先没说明，价格倒是便宜。', '[]', DATE_SUB(NOW(), INTERVAL 5 DAY));

-- ------------------------------------------------------------- 举报（2 条）
INSERT INTO t_report (id, item_id, reporter_id, reason, description, status, handle_action, handler_id, handled_at, created_at, updated_at) VALUES
(1, 11, 1, 'FAKE_INFO',    '描述写的是 165Hz，实际面板型号查不到支持 165Hz，怀疑参数虚假。', 'PENDING', NULL, NULL, NULL, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),
(2, 22, 8, 'PRICE_FRAUD', '同款全新收纳箱电商才 30 元一个，这里两个旧的卖 40 元，价格不合理。', 'HANDLED', 'OFFLINE', 2, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY));

-- ------------------------------------------------------------- 私信（5 条，2 条未读）
INSERT INTO t_chat_message (id, from_user_id, to_user_id, item_id, content, is_read, created_at) VALUES
(1, 1, 4, 1, '同学你好，iPad 还在吗？', 1, DATE_SUB(NOW(), INTERVAL 3 DAY)),
(2, 4, 1, 1, '在的，国行 64G，配件齐全，可以面交验机。', 1, DATE_SUB(NOW(), INTERVAL 3 DAY)),
(3, 1, 4, 1, '电池健康多少呀？', 1, DATE_SUB(NOW(), INTERVAL 2 DAY)),
(4, 4, 1, 1, '电池健康 88%，一直贴膜戴壳，基本无划痕。', 0, DATE_SUB(NOW(), INTERVAL 2 DAY)),
(5, 4, 1, 1, '今天下午我都在东校区，随时可以约。', 0, DATE_SUB(NOW(), INTERVAL 1 HOUR));

SET FOREIGN_KEY_CHECKS = 1;

-- 校验（可手工执行）：
--   SELECT COUNT(*) FROM t_user;             -- 8
--   SELECT COUNT(*) FROM t_category;         -- 6
--   SELECT COUNT(*) FROM t_item;             -- 36
--   SELECT status, COUNT(*) FROM t_item GROUP BY status;  -- ON_SALE 28 / SOLD 5 / OFF_SHELF 3
--   SELECT COUNT(*) FROM t_order;            -- 7


-- ============================================================= 90 天成交数据（商品，SOLD）
-- 用途：AI 估价 soldMedianPrice / onSaleLowestPrice 工具的真实数据底座。
-- 每件商品对应一笔已完成订单，覆盖 24 个关键词 60 笔成交，时间分布在近 90 天内。
INSERT INTO t_item
(id, title, description, category_id, price, original_price, condition_level, campus, trade_type, contact, status, cover_image,
 view_count, favorite_count, brand_model, seller_id, deleted, created_at, updated_at)
VALUES
(37, 'iPhone 12 128G 黑色 国行', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 1, 2350.00, 4999.00, 'LIKE_NEW', '东校区', 'BOTH', '微信 aizhe_digital', 'SOLD', '/uploads/seed/1.jpg', 30, 0, 'iPhone', 4, 0, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),
(38, 'iPhone 12 128G 黑色 国行', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 1, 2480.00, 4999.00, 'LIGHT_USE', '东校区', 'BOTH', '微信 may_life', 'SOLD', '/uploads/seed/2.jpg', 47, 3, 'iPhone', 7, 0, DATE_SUB(NOW(), INTERVAL 14 DAY), DATE_SUB(NOW(), INTERVAL 13 DAY)),
(39, 'iPhone 12 128G 黑色 国行', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 1, 2600.00, 4999.00, 'HEAVY_USE', '东校区', 'BOTH', 'QQ 99887766', 'SOLD', '/uploads/seed/3.jpg', 64, 6, 'iPhone', 8, 0, DATE_SUB(NOW(), INTERVAL 32 DAY), DATE_SUB(NOW(), INTERVAL 31 DAY)),
(40, 'iPad 9代 64G WiFi版 深空灰', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 1, 1180.00, 2499.00, 'LIGHT_USE', '东校区', 'BOTH', '微信 aizhe_digital', 'SOLD', '/uploads/seed/2.jpg', 81, 9, 'iPad', 4, 0, DATE_SUB(NOW(), INTERVAL 75 DAY), DATE_SUB(NOW(), INTERVAL 74 DAY)),
(41, 'iPad 9代 64G WiFi版 深空灰', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 1, 1280.00, 2499.00, 'HEAVY_USE', '东校区', 'BOTH', '微信 laoli_shop', 'SOLD', '/uploads/seed/3.jpg', 98, 12, 'iPad', 6, 0, DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY)),
(42, 'iPad 9代 64G WiFi版 深空灰', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 1, 1350.00, 2499.00, 'LIKE_NEW', '东校区', 'BOTH', '微信 may_life', 'SOLD', '/uploads/seed/4.jpg', 115, 1, 'iPad', 7, 0, DATE_SUB(NOW(), INTERVAL 23 DAY), DATE_SUB(NOW(), INTERVAL 22 DAY)),
(43, 'AirPods Pro 2 USB-C', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 1, 950.00, 1899.00, 'HEAVY_USE', '东校区', 'BOTH', '微信 may_life', 'SOLD', '/uploads/seed/3.jpg', 132, 4, 'AirPods', 7, 0, DATE_SUB(NOW(), INTERVAL 60 DAY), DATE_SUB(NOW(), INTERVAL 59 DAY)),
(44, 'AirPods Pro 2 USB-C', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 1, 1080.00, 1899.00, 'LIKE_NEW', '东校区', 'BOTH', '微信 aizhe_digital', 'SOLD', '/uploads/seed/4.jpg', 149, 7, 'AirPods', 4, 0, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)),
(45, '小米手环 8 NFC版', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 1, 130.00, 249.00, 'LIKE_NEW', '南校区', 'BOTH', '微信 laoli_shop', 'SOLD', '/uploads/seed/4.jpg', 166, 10, '小米', 6, 0, DATE_SUB(NOW(), INTERVAL 23 DAY), DATE_SUB(NOW(), INTERVAL 22 DAY)),
(46, '小米手环 8 NFC版', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 1, 145.00, 249.00, 'LIGHT_USE', '南校区', 'BOTH', 'QQ 99887766', 'SOLD', '/uploads/seed/5.jpg', 183, 13, '小米', 8, 0, DATE_SUB(NOW(), INTERVAL 48 DAY), DATE_SUB(NOW(), INTERVAL 47 DAY)),
(47, '小米手环 8 NFC版', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 1, 158.00, 249.00, 'HEAVY_USE', '南校区', 'BOTH', 'QQ 55667788', 'SOLD', '/uploads/seed/6.jpg', 200, 2, '小米', 5, 0, DATE_SUB(NOW(), INTERVAL 82 DAY), DATE_SUB(NOW(), INTERVAL 81 DAY)),
(48, 'Switch OLED 白色 国行', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 1, 1550.00, 2599.00, 'LIGHT_USE', '东校区', 'BOTH', 'QQ 99887766', 'SOLD', '/uploads/seed/5.jpg', 217, 5, 'Switch', 8, 0, DATE_SUB(NOW(), INTERVAL 16 DAY), DATE_SUB(NOW(), INTERVAL 15 DAY)),
(49, 'Switch OLED 白色 国行', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 1, 1680.00, 2599.00, 'HEAVY_USE', '东校区', 'BOTH', '微信 aizhe_digital', 'SOLD', '/uploads/seed/6.jpg', 234, 8, 'Switch', 4, 0, DATE_SUB(NOW(), INTERVAL 36 DAY), DATE_SUB(NOW(), INTERVAL 35 DAY)),
(50, 'Switch OLED 白色 国行', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 1, 1780.00, 2599.00, 'LIKE_NEW', '东校区', 'BOTH', '微信 laoli_shop', 'SOLD', '/uploads/seed/1.jpg', 251, 11, 'Switch', 6, 0, DATE_SUB(NOW(), INTERVAL 65 DAY), DATE_SUB(NOW(), INTERVAL 64 DAY)),
(51, 'Kindle Paperwhite 5 8G', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 1, 420.00, 998.00, 'HEAVY_USE', '西校区', 'BOTH', 'QQ 99887766', 'SOLD', '/uploads/seed/6.jpg', 268, 0, 'Kindle', 8, 0, DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 9 DAY)),
(52, 'Kindle Paperwhite 5 8G', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 1, 465.00, 998.00, 'LIKE_NEW', '西校区', 'BOTH', '微信 laoli_shop', 'SOLD', '/uploads/seed/1.jpg', 285, 3, 'Kindle', 6, 0, DATE_SUB(NOW(), INTERVAL 26 DAY), DATE_SUB(NOW(), INTERVAL 25 DAY)),
(53, '机械革命 蛟龙15K RTX4060', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 2, 3850.00, 6499.00, 'LIKE_NEW', '东校区', 'BOTH', '微信 aizhe_digital', 'SOLD', '/uploads/seed/1.jpg', 42, 6, '机械革命', 4, 0, DATE_SUB(NOW(), INTERVAL 65 DAY), DATE_SUB(NOW(), INTERVAL 64 DAY)),
(54, '机械革命 蛟龙15K RTX4060', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 2, 4250.00, 6499.00, 'LIGHT_USE', '东校区', 'BOTH', '微信 may_life', 'SOLD', '/uploads/seed/2.jpg', 59, 9, '机械革命', 7, 0, DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY)),
(55, 'MacBook Air M1 8+256 深空灰', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 2, 4250.00, 7999.00, 'LIGHT_USE', '东校区', 'BOTH', '微信 aizhe_digital', 'SOLD', '/uploads/seed/2.jpg', 76, 12, 'MacBook', 4, 0, DATE_SUB(NOW(), INTERVAL 26 DAY), DATE_SUB(NOW(), INTERVAL 25 DAY)),
(56, 'MacBook Air M1 8+256 深空灰', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 2, 4580.00, 7999.00, 'HEAVY_USE', '东校区', 'BOTH', '微信 may_life', 'SOLD', '/uploads/seed/3.jpg', 93, 1, 'MacBook', 7, 0, DATE_SUB(NOW(), INTERVAL 52 DAY), DATE_SUB(NOW(), INTERVAL 51 DAY)),
(57, 'MacBook Air M1 8+256 深空灰', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 2, 4900.00, 7999.00, 'LIKE_NEW', '东校区', 'BOTH', 'QQ 55667788', 'SOLD', '/uploads/seed/4.jpg', 110, 4, 'MacBook', 5, 0, DATE_SUB(NOW(), INTERVAL 88 DAY), DATE_SUB(NOW(), INTERVAL 87 DAY)),
(58, '戴尔 U2723QE 27寸 4K 显示器', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 2, 1380.00, 4299.00, 'HEAVY_USE', '南校区', 'BOTH', '微信 laoli_shop', 'SOLD', '/uploads/seed/3.jpg', 127, 7, '戴尔', 6, 0, DATE_SUB(NOW(), INTERVAL 18 DAY), DATE_SUB(NOW(), INTERVAL 17 DAY)),
(59, '戴尔 U2723QE 27寸 4K 显示器', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 2, 1550.00, 4299.00, 'LIKE_NEW', '南校区', 'BOTH', '微信 aizhe_digital', 'SOLD', '/uploads/seed/4.jpg', 144, 10, '戴尔', 4, 0, DATE_SUB(NOW(), INTERVAL 40 DAY), DATE_SUB(NOW(), INTERVAL 39 DAY)),
(60, '罗技 G502 HERO 游戏鼠标', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 2, 115.00, 299.00, 'LIKE_NEW', '南校区', 'BOTH', '微信 laoli_shop', 'SOLD', '/uploads/seed/4.jpg', 161, 13, '罗技', 6, 0, DATE_SUB(NOW(), INTERVAL 88 DAY), DATE_SUB(NOW(), INTERVAL 87 DAY)),
(61, '罗技 G502 HERO 游戏鼠标', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 2, 135.00, 299.00, 'LIGHT_USE', '南校区', 'BOTH', 'QQ 99887766', 'SOLD', '/uploads/seed/5.jpg', 178, 2, '罗技', 8, 0, DATE_SUB(NOW(), INTERVAL 12 DAY), DATE_SUB(NOW(), INTERVAL 11 DAY)),
(62, '罗技 G502 HERO 游戏鼠标', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 2, 150.00, 299.00, 'HEAVY_USE', '南校区', 'BOTH', '微信 may_life', 'SOLD', '/uploads/seed/6.jpg', 195, 5, '罗技', 7, 0, DATE_SUB(NOW(), INTERVAL 29 DAY), DATE_SUB(NOW(), INTERVAL 28 DAY)),
(63, '索尼 WH-1000XM4 降噪耳机', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 1, 980.00, 2299.00, 'LIGHT_USE', '东校区', 'BOTH', '微信 may_life', 'SOLD', '/uploads/seed/5.jpg', 212, 8, '索尼', 7, 0, DATE_SUB(NOW(), INTERVAL 70 DAY), DATE_SUB(NOW(), INTERVAL 69 DAY)),
(64, '索尼 WH-1000XM4 降噪耳机', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 1, 1120.00, 2299.00, 'HEAVY_USE', '东校区', 'BOTH', '微信 aizhe_digital', 'SOLD', '/uploads/seed/6.jpg', 229, 11, '索尼', 4, 0, DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY)),
(65, '索尼 WH-1000XM4 降噪耳机', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 1, 1250.00, 2299.00, 'LIKE_NEW', '东校区', 'BOTH', 'QQ 99887766', 'SOLD', '/uploads/seed/1.jpg', 246, 0, '索尼', 8, 0, DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_SUB(NOW(), INTERVAL 19 DAY)),
(66, '张宇考研数学 2024 全套', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 3, 38.00, 320.00, 'HEAVY_USE', '西校区', 'BOTH', 'QQ 55667788', 'SOLD', '/uploads/seed/6.jpg', 263, 3, NULL, 5, 0, DATE_SUB(NOW(), INTERVAL 56 DAY), DATE_SUB(NOW(), INTERVAL 55 DAY)),
(67, '张宇考研数学 2024 全套', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 3, 45.00, 320.00, 'LIKE_NEW', '西校区', 'BOTH', '微信 laoli_shop', 'SOLD', '/uploads/seed/1.jpg', 280, 6, NULL, 6, 0, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),
(68, '张宇考研数学 2024 全套', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 3, 52.00, 320.00, 'LIGHT_USE', '西校区', 'BOTH', 'QQ 99887766', 'SOLD', '/uploads/seed/2.jpg', 37, 9, NULL, 8, 0, DATE_SUB(NOW(), INTERVAL 14 DAY), DATE_SUB(NOW(), INTERVAL 13 DAY)),
(69, '肖秀荣考研政治 全套 2024', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 3, 30.00, 168.00, 'LIKE_NEW', '西校区', 'BOTH', 'QQ 55667788', 'SOLD', '/uploads/seed/1.jpg', 54, 12, NULL, 5, 0, DATE_SUB(NOW(), INTERVAL 44 DAY), DATE_SUB(NOW(), INTERVAL 43 DAY)),
(70, '肖秀荣考研政治 全套 2024', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 3, 38.00, 168.00, 'LIGHT_USE', '西校区', 'BOTH', 'QQ 99887766', 'SOLD', '/uploads/seed/2.jpg', 71, 1, NULL, 8, 0, DATE_SUB(NOW(), INTERVAL 75 DAY), DATE_SUB(NOW(), INTERVAL 74 DAY)),
(71, '肖秀荣考研政治 全套 2024', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 3, 45.00, 168.00, 'HEAVY_USE', '西校区', 'BOTH', '微信 aizhe_digital', 'SOLD', '/uploads/seed/3.jpg', 88, 4, NULL, 4, 0, DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY)),
(72, '高等数学 同济第七版 上下册', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 3, 12.00, 98.00, 'LIGHT_USE', '西校区', 'BOTH', 'QQ 55667788', 'SOLD', '/uploads/seed/2.jpg', 105, 7, NULL, 5, 0, DATE_SUB(NOW(), INTERVAL 32 DAY), DATE_SUB(NOW(), INTERVAL 31 DAY)),
(73, '高等数学 同济第七版 上下册', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 3, 16.00, 98.00, 'HEAVY_USE', '西校区', 'BOTH', '微信 aizhe_digital', 'SOLD', '/uploads/seed/3.jpg', 122, 10, NULL, 4, 0, DATE_SUB(NOW(), INTERVAL 60 DAY), DATE_SUB(NOW(), INTERVAL 59 DAY)),
(74, '高等数学 同济第七版 上下册', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 3, 20.00, 98.00, 'LIKE_NEW', '西校区', 'BOTH', '微信 laoli_shop', 'SOLD', '/uploads/seed/4.jpg', 139, 13, NULL, 6, 0, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)),
(75, '算法导论 中文第三版', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 3, 52.00, 128.00, 'HEAVY_USE', '西校区', 'BOTH', 'QQ 55667788', 'SOLD', '/uploads/seed/3.jpg', 156, 2, NULL, 5, 0, DATE_SUB(NOW(), INTERVAL 23 DAY), DATE_SUB(NOW(), INTERVAL 22 DAY)),
(76, '算法导论 中文第三版', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 3, 60.00, 128.00, 'LIKE_NEW', '西校区', 'BOTH', '微信 may_life', 'SOLD', '/uploads/seed/4.jpg', 173, 5, NULL, 7, 0, DATE_SUB(NOW(), INTERVAL 48 DAY), DATE_SUB(NOW(), INTERVAL 47 DAY)),
(77, '算法导论 中文第三版', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 3, 68.00, 128.00, 'LIGHT_USE', '西校区', 'BOTH', 'QQ 99887766', 'SOLD', '/uploads/seed/5.jpg', 190, 8, NULL, 8, 0, DATE_SUB(NOW(), INTERVAL 82 DAY), DATE_SUB(NOW(), INTERVAL 81 DAY)),
(78, '大学英语四级真题 2024.12', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 3, 15.00, 59.00, 'LIKE_NEW', '西校区', 'BOTH', 'QQ 55667788', 'SOLD', '/uploads/seed/4.jpg', 207, 11, NULL, 5, 0, DATE_SUB(NOW(), INTERVAL 16 DAY), DATE_SUB(NOW(), INTERVAL 15 DAY)),
(79, '大学英语四级真题 2024.12', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 3, 22.00, 59.00, 'LIGHT_USE', '西校区', 'BOTH', '微信 laoli_shop', 'SOLD', '/uploads/seed/5.jpg', 224, 0, NULL, 6, 0, DATE_SUB(NOW(), INTERVAL 36 DAY), DATE_SUB(NOW(), INTERVAL 35 DAY)),
(80, '小米台灯 Pro 护眼版', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 4, 78.00, 249.00, 'LIGHT_USE', '南校区', 'BOTH', '微信 laoli_shop', 'SOLD', '/uploads/seed/5.jpg', 241, 3, '小米', 6, 0, DATE_SUB(NOW(), INTERVAL 82 DAY), DATE_SUB(NOW(), INTERVAL 81 DAY)),
(81, '小米台灯 Pro 护眼版', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 4, 95.00, 249.00, 'HEAVY_USE', '南校区', 'BOTH', 'QQ 55667788', 'SOLD', '/uploads/seed/6.jpg', 258, 6, '小米', 5, 0, DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 9 DAY)),
(82, '小米台灯 Pro 护眼版', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 4, 110.00, 249.00, 'LIKE_NEW', '南校区', 'BOTH', '微信 may_life', 'SOLD', '/uploads/seed/1.jpg', 275, 9, '小米', 7, 0, DATE_SUB(NOW(), INTERVAL 26 DAY), DATE_SUB(NOW(), INTERVAL 25 DAY)),
(83, '宜家马库斯电脑椅 转椅', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 4, 260.00, 1499.00, 'HEAVY_USE', '南校区', 'BOTH', '微信 laoli_shop', 'SOLD', '/uploads/seed/6.jpg', 32, 12, '宜家', 6, 0, DATE_SUB(NOW(), INTERVAL 65 DAY), DATE_SUB(NOW(), INTERVAL 64 DAY)),
(84, '宜家马库斯电脑椅 转椅', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 4, 320.00, 1499.00, 'LIKE_NEW', '南校区', 'BOTH', 'QQ 99887766', 'SOLD', '/uploads/seed/1.jpg', 49, 1, '宜家', 8, 0, DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY)),
(85, '小米电饭煲 3L 家用', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 4, 85.00, 199.00, 'LIKE_NEW', '南校区', 'BOTH', '微信 laoli_shop', 'SOLD', '/uploads/seed/1.jpg', 66, 4, '小米', 6, 0, DATE_SUB(NOW(), INTERVAL 26 DAY), DATE_SUB(NOW(), INTERVAL 25 DAY)),
(86, '小米电饭煲 3L 家用', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 4, 100.00, 199.00, 'LIGHT_USE', '南校区', 'BOTH', '微信 may_life', 'SOLD', '/uploads/seed/2.jpg', 83, 7, '小米', 7, 0, DATE_SUB(NOW(), INTERVAL 52 DAY), DATE_SUB(NOW(), INTERVAL 51 DAY)),
(87, '小米电饭煲 3L 家用', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 4, 115.00, 199.00, 'HEAVY_USE', '南校区', 'BOTH', '微信 aizhe_digital', 'SOLD', '/uploads/seed/3.jpg', 100, 10, '小米', 4, 0, DATE_SUB(NOW(), INTERVAL 88 DAY), DATE_SUB(NOW(), INTERVAL 87 DAY)),
(88, '24寸行李箱 静音万向轮', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 4, 105.00, 329.00, 'LIGHT_USE', '南校区', 'BOTH', '微信 laoli_shop', 'SOLD', '/uploads/seed/2.jpg', 117, 13, NULL, 6, 0, DATE_SUB(NOW(), INTERVAL 18 DAY), DATE_SUB(NOW(), INTERVAL 17 DAY)),
(89, '24寸行李箱 静音万向轮', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 4, 135.00, 329.00, 'HEAVY_USE', '南校区', 'BOTH', '微信 aizhe_digital', 'SOLD', '/uploads/seed/3.jpg', 134, 2, NULL, 4, 0, DATE_SUB(NOW(), INTERVAL 40 DAY), DATE_SUB(NOW(), INTERVAL 39 DAY)),
(90, '24寸行李箱 静音万向轮', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 4, 160.00, 329.00, 'LIKE_NEW', '南校区', 'BOTH', 'QQ 99887766', 'SOLD', '/uploads/seed/4.jpg', 151, 5, NULL, 8, 0, DATE_SUB(NOW(), INTERVAL 70 DAY), DATE_SUB(NOW(), INTERVAL 69 DAY)),
(91, '斯伯丁篮球 7号 PU', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 5, 88.00, 229.00, 'HEAVY_USE', '东校区', 'BOTH', 'QQ 99887766', 'SOLD', '/uploads/seed/3.jpg', 168, 8, '斯伯丁', 8, 0, DATE_SUB(NOW(), INTERVAL 12 DAY), DATE_SUB(NOW(), INTERVAL 11 DAY)),
(92, '斯伯丁篮球 7号 PU', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 5, 105.00, 229.00, 'LIKE_NEW', '东校区', 'BOTH', 'QQ 55667788', 'SOLD', '/uploads/seed/4.jpg', 185, 11, '斯伯丁', 5, 0, DATE_SUB(NOW(), INTERVAL 29 DAY), DATE_SUB(NOW(), INTERVAL 28 DAY)),
(93, '斯伯丁篮球 7号 PU', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 5, 125.00, 229.00, 'LIGHT_USE', '东校区', 'BOTH', '微信 laoli_shop', 'SOLD', '/uploads/seed/5.jpg', 202, 0, '斯伯丁', 6, 0, DATE_SUB(NOW(), INTERVAL 56 DAY), DATE_SUB(NOW(), INTERVAL 55 DAY)),
(94, '哑铃 20kg 可调节套装', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 5, 150.00, 359.00, 'LIKE_NEW', '东校区', 'BOTH', 'QQ 99887766', 'SOLD', '/uploads/seed/4.jpg', 219, 3, NULL, 8, 0, DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY)),
(95, '哑铃 20kg 可调节套装', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 5, 185.00, 359.00, 'LIGHT_USE', '东校区', 'BOTH', '微信 laoli_shop', 'SOLD', '/uploads/seed/5.jpg', 236, 6, NULL, 6, 0, DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_SUB(NOW(), INTERVAL 19 DAY)),
(96, '哑铃 20kg 可调节套装', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 5, 210.00, 359.00, 'HEAVY_USE', '东校区', 'BOTH', '微信 aizhe_digital', 'SOLD', '/uploads/seed/6.jpg', 253, 9, NULL, 4, 0, DATE_SUB(NOW(), INTERVAL 44 DAY), DATE_SUB(NOW(), INTERVAL 43 DAY)),
(97, '网球拍 碳素初学 带线', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 5, 195.00, 459.00, 'LIGHT_USE', '东校区', 'BOTH', 'QQ 99887766', 'SOLD', '/uploads/seed/5.jpg', 270, 12, NULL, 8, 0, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),
(98, '网球拍 碳素初学 带线', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 5, 240.00, 459.00, 'HEAVY_USE', '东校区', 'BOTH', 'QQ 55667788', 'SOLD', '/uploads/seed/6.jpg', 287, 1, NULL, 5, 0, DATE_SUB(NOW(), INTERVAL 14 DAY), DATE_SUB(NOW(), INTERVAL 13 DAY)),
(99, '雅迪电动车 48V12A', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 5, 1580.00, 2899.00, 'HEAVY_USE', '东校区', 'BOTH', 'QQ 99887766', 'SOLD', '/uploads/seed/6.jpg', 44, 4, '雅迪', 8, 0, DATE_SUB(NOW(), INTERVAL 44 DAY), DATE_SUB(NOW(), INTERVAL 43 DAY)),
(100, '雅迪电动车 48V12A', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 5, 1850.00, 2899.00, 'LIKE_NEW', '东校区', 'BOTH', '微信 may_life', 'SOLD', '/uploads/seed/1.jpg', 61, 7, '雅迪', 7, 0, DATE_SUB(NOW(), INTERVAL 75 DAY), DATE_SUB(NOW(), INTERVAL 74 DAY)),
(101, '雅迪电动车 48V12A', '已成交的历史商品，仅保留成交快照用于平台价格参考。', 5, 2100.00, 2899.00, 'LIGHT_USE', '东校区', 'BOTH', '微信 laoli_shop', 'SOLD', '/uploads/seed/2.jpg', 78, 10, '雅迪', 6, 0, DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY));

-- ============================================================= 90 天成交数据（订单，DONE）
INSERT INTO t_order
(id, order_no, item_id, buyer_id, seller_id, amount, item_title, item_cover, item_price, trade_type, status,
 pay_method, pay_time, ship_time, finish_time, cancel_time, close_reason,
 meet_place, buyer_note, deleted, created_at, updated_at)
VALUES
(8, '20261101120000000000', 37, 1, 4, 2350.00, 'iPhone 12 128G 黑色 国行', '/uploads/seed/1.jpg', 2350.00, 'MEET', 'DONE', 'WECHAT', DATE_SUB(NOW(), INTERVAL 2 DAY), NULL, DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, NULL, '东校区 3 号宿舍楼下', NULL, 0, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),
(9, '20261020120000000001', 38, 2, 7, 2480.00, 'iPhone 12 128G 黑色 国行', '/uploads/seed/2.jpg', 2480.00, 'MEET', 'DONE', 'ALIPAY', DATE_SUB(NOW(), INTERVAL 14 DAY), NULL, DATE_SUB(NOW(), INTERVAL 13 DAY), NULL, NULL, '东校区 3 号宿舍楼下', NULL, 0, DATE_SUB(NOW(), INTERVAL 14 DAY), DATE_SUB(NOW(), INTERVAL 13 DAY)),
(10, '20261002120000000002', 39, 3, 8, 2600.00, 'iPhone 12 128G 黑色 国行', '/uploads/seed/3.jpg', 2600.00, 'MEET', 'DONE', 'BANK', DATE_SUB(NOW(), INTERVAL 32 DAY), NULL, DATE_SUB(NOW(), INTERVAL 31 DAY), NULL, NULL, '东校区 3 号宿舍楼下', NULL, 0, DATE_SUB(NOW(), INTERVAL 32 DAY), DATE_SUB(NOW(), INTERVAL 31 DAY)),
(11, '20260820120000000003', 40, 1, 4, 1180.00, 'iPad 9代 64G WiFi版 深空灰', '/uploads/seed/2.jpg', 1180.00, 'MEET', 'DONE', 'WECHAT', DATE_SUB(NOW(), INTERVAL 75 DAY), NULL, DATE_SUB(NOW(), INTERVAL 74 DAY), NULL, NULL, '东校区 图书馆门口', NULL, 0, DATE_SUB(NOW(), INTERVAL 75 DAY), DATE_SUB(NOW(), INTERVAL 74 DAY)),
(12, '20261026120000000004', 41, 2, 6, 1280.00, 'iPad 9代 64G WiFi版 深空灰', '/uploads/seed/3.jpg', 1280.00, 'MEET', 'DONE', 'ALIPAY', DATE_SUB(NOW(), INTERVAL 8 DAY), NULL, DATE_SUB(NOW(), INTERVAL 7 DAY), NULL, NULL, '东校区 图书馆门口', NULL, 0, DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY)),
(13, '20261011120000000005', 42, 3, 7, 1350.00, 'iPad 9代 64G WiFi版 深空灰', '/uploads/seed/4.jpg', 1350.00, 'MEET', 'DONE', 'BANK', DATE_SUB(NOW(), INTERVAL 23 DAY), NULL, DATE_SUB(NOW(), INTERVAL 22 DAY), NULL, NULL, '东校区 图书馆门口', NULL, 0, DATE_SUB(NOW(), INTERVAL 23 DAY), DATE_SUB(NOW(), INTERVAL 22 DAY)),
(14, '20260904120000000006', 43, 1, 7, 950.00, 'AirPods Pro 2 USB-C', '/uploads/seed/3.jpg', 950.00, 'MEET', 'DONE', 'WECHAT', DATE_SUB(NOW(), INTERVAL 60 DAY), NULL, DATE_SUB(NOW(), INTERVAL 59 DAY), NULL, NULL, '东校区 3 号宿舍楼下', NULL, 0, DATE_SUB(NOW(), INTERVAL 60 DAY), DATE_SUB(NOW(), INTERVAL 59 DAY)),
(15, '20261031120000000007', 44, 2, 4, 1080.00, 'AirPods Pro 2 USB-C', '/uploads/seed/4.jpg', 1080.00, 'MEET', 'DONE', 'ALIPAY', DATE_SUB(NOW(), INTERVAL 3 DAY), NULL, DATE_SUB(NOW(), INTERVAL 2 DAY), NULL, NULL, '东校区 3 号宿舍楼下', NULL, 0, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)),
(16, '20261011120000000008', 45, 1, 6, 130.00, '小米手环 8 NFC版', '/uploads/seed/4.jpg', 130.00, 'MEET', 'DONE', 'BANK', DATE_SUB(NOW(), INTERVAL 23 DAY), NULL, DATE_SUB(NOW(), INTERVAL 22 DAY), NULL, NULL, '南校区 图书馆门口', NULL, 0, DATE_SUB(NOW(), INTERVAL 23 DAY), DATE_SUB(NOW(), INTERVAL 22 DAY)),
(17, '20260916120000000009', 46, 2, 8, 145.00, '小米手环 8 NFC版', '/uploads/seed/5.jpg', 145.00, 'MEET', 'DONE', 'WECHAT', DATE_SUB(NOW(), INTERVAL 48 DAY), NULL, DATE_SUB(NOW(), INTERVAL 47 DAY), NULL, NULL, '南校区 图书馆门口', NULL, 0, DATE_SUB(NOW(), INTERVAL 48 DAY), DATE_SUB(NOW(), INTERVAL 47 DAY)),
(18, '20260813120000000010', 47, 3, 5, 158.00, '小米手环 8 NFC版', '/uploads/seed/6.jpg', 158.00, 'MEET', 'DONE', 'ALIPAY', DATE_SUB(NOW(), INTERVAL 82 DAY), NULL, DATE_SUB(NOW(), INTERVAL 81 DAY), NULL, NULL, '南校区 图书馆门口', NULL, 0, DATE_SUB(NOW(), INTERVAL 82 DAY), DATE_SUB(NOW(), INTERVAL 81 DAY)),
(19, '20261018120000000011', 48, 1, 8, 1550.00, 'Switch OLED 白色 国行', '/uploads/seed/5.jpg', 1550.00, 'MEET', 'DONE', 'BANK', DATE_SUB(NOW(), INTERVAL 16 DAY), NULL, DATE_SUB(NOW(), INTERVAL 15 DAY), NULL, NULL, '东校区 3 号宿舍楼下', NULL, 0, DATE_SUB(NOW(), INTERVAL 16 DAY), DATE_SUB(NOW(), INTERVAL 15 DAY)),
(20, '20260928120000000012', 49, 2, 4, 1680.00, 'Switch OLED 白色 国行', '/uploads/seed/6.jpg', 1680.00, 'MEET', 'DONE', 'WECHAT', DATE_SUB(NOW(), INTERVAL 36 DAY), NULL, DATE_SUB(NOW(), INTERVAL 35 DAY), NULL, NULL, '东校区 3 号宿舍楼下', NULL, 0, DATE_SUB(NOW(), INTERVAL 36 DAY), DATE_SUB(NOW(), INTERVAL 35 DAY)),
(21, '20260830120000000013', 50, 3, 6, 1780.00, 'Switch OLED 白色 国行', '/uploads/seed/1.jpg', 1780.00, 'MEET', 'DONE', 'ALIPAY', DATE_SUB(NOW(), INTERVAL 65 DAY), NULL, DATE_SUB(NOW(), INTERVAL 64 DAY), NULL, NULL, '东校区 3 号宿舍楼下', NULL, 0, DATE_SUB(NOW(), INTERVAL 65 DAY), DATE_SUB(NOW(), INTERVAL 64 DAY)),
(22, '20261024120000000014', 51, 1, 8, 420.00, 'Kindle Paperwhite 5 8G', '/uploads/seed/6.jpg', 420.00, 'MEET', 'DONE', 'BANK', DATE_SUB(NOW(), INTERVAL 10 DAY), NULL, DATE_SUB(NOW(), INTERVAL 9 DAY), NULL, NULL, '西校区 图书馆门口', NULL, 0, DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 9 DAY)),
(23, '20261008120000000015', 52, 2, 6, 465.00, 'Kindle Paperwhite 5 8G', '/uploads/seed/1.jpg', 465.00, 'MEET', 'DONE', 'WECHAT', DATE_SUB(NOW(), INTERVAL 26 DAY), NULL, DATE_SUB(NOW(), INTERVAL 25 DAY), NULL, NULL, '西校区 图书馆门口', NULL, 0, DATE_SUB(NOW(), INTERVAL 26 DAY), DATE_SUB(NOW(), INTERVAL 25 DAY)),
(24, '20260830120000000016', 53, 1, 4, 3850.00, '机械革命 蛟龙15K RTX4060', '/uploads/seed/1.jpg', 3850.00, 'MEET', 'DONE', 'ALIPAY', DATE_SUB(NOW(), INTERVAL 65 DAY), NULL, DATE_SUB(NOW(), INTERVAL 64 DAY), NULL, NULL, '东校区 3 号宿舍楼下', NULL, 0, DATE_SUB(NOW(), INTERVAL 65 DAY), DATE_SUB(NOW(), INTERVAL 64 DAY)),
(25, '20261029120000000017', 54, 2, 7, 4250.00, '机械革命 蛟龙15K RTX4060', '/uploads/seed/2.jpg', 4250.00, 'MEET', 'DONE', 'BANK', DATE_SUB(NOW(), INTERVAL 5 DAY), NULL, DATE_SUB(NOW(), INTERVAL 4 DAY), NULL, NULL, '东校区 3 号宿舍楼下', NULL, 0, DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY)),
(26, '20261008120000000018', 55, 1, 4, 4250.00, 'MacBook Air M1 8+256 深空灰', '/uploads/seed/2.jpg', 4250.00, 'MEET', 'DONE', 'WECHAT', DATE_SUB(NOW(), INTERVAL 26 DAY), NULL, DATE_SUB(NOW(), INTERVAL 25 DAY), NULL, NULL, '东校区 图书馆门口', NULL, 0, DATE_SUB(NOW(), INTERVAL 26 DAY), DATE_SUB(NOW(), INTERVAL 25 DAY)),
(27, '20260912120000000019', 56, 2, 7, 4580.00, 'MacBook Air M1 8+256 深空灰', '/uploads/seed/3.jpg', 4580.00, 'MEET', 'DONE', 'ALIPAY', DATE_SUB(NOW(), INTERVAL 52 DAY), NULL, DATE_SUB(NOW(), INTERVAL 51 DAY), NULL, NULL, '东校区 图书馆门口', NULL, 0, DATE_SUB(NOW(), INTERVAL 52 DAY), DATE_SUB(NOW(), INTERVAL 51 DAY)),
(28, '20260807120000000020', 57, 3, 5, 4900.00, 'MacBook Air M1 8+256 深空灰', '/uploads/seed/4.jpg', 4900.00, 'MEET', 'DONE', 'BANK', DATE_SUB(NOW(), INTERVAL 88 DAY), NULL, DATE_SUB(NOW(), INTERVAL 87 DAY), NULL, NULL, '东校区 图书馆门口', NULL, 0, DATE_SUB(NOW(), INTERVAL 88 DAY), DATE_SUB(NOW(), INTERVAL 87 DAY)),
(29, '20261016120000000021', 58, 1, 6, 1380.00, '戴尔 U2723QE 27寸 4K 显示器', '/uploads/seed/3.jpg', 1380.00, 'SHIP', 'DONE', 'WECHAT', DATE_SUB(NOW(), INTERVAL 18 DAY), DATE_SUB(NOW(), INTERVAL 17 DAY), DATE_SUB(NOW(), INTERVAL 15 DAY), NULL, NULL, NULL, NULL, 0, DATE_SUB(NOW(), INTERVAL 18 DAY), DATE_SUB(NOW(), INTERVAL 15 DAY)),
(30, '20260924120000000022', 59, 2, 4, 1550.00, '戴尔 U2723QE 27寸 4K 显示器', '/uploads/seed/4.jpg', 1550.00, 'MEET', 'DONE', 'ALIPAY', DATE_SUB(NOW(), INTERVAL 40 DAY), NULL, DATE_SUB(NOW(), INTERVAL 39 DAY), NULL, NULL, '南校区 3 号宿舍楼下', NULL, 0, DATE_SUB(NOW(), INTERVAL 40 DAY), DATE_SUB(NOW(), INTERVAL 39 DAY)),
(31, '20260807120000000023', 60, 1, 6, 115.00, '罗技 G502 HERO 游戏鼠标', '/uploads/seed/4.jpg', 115.00, 'MEET', 'DONE', 'BANK', DATE_SUB(NOW(), INTERVAL 88 DAY), NULL, DATE_SUB(NOW(), INTERVAL 87 DAY), NULL, NULL, '南校区 图书馆门口', NULL, 0, DATE_SUB(NOW(), INTERVAL 88 DAY), DATE_SUB(NOW(), INTERVAL 87 DAY)),
(32, '20261022120000000024', 61, 2, 8, 135.00, '罗技 G502 HERO 游戏鼠标', '/uploads/seed/5.jpg', 135.00, 'MEET', 'DONE', 'WECHAT', DATE_SUB(NOW(), INTERVAL 12 DAY), NULL, DATE_SUB(NOW(), INTERVAL 11 DAY), NULL, NULL, '南校区 图书馆门口', NULL, 0, DATE_SUB(NOW(), INTERVAL 12 DAY), DATE_SUB(NOW(), INTERVAL 11 DAY)),
(33, '20261005120000000025', 62, 3, 7, 150.00, '罗技 G502 HERO 游戏鼠标', '/uploads/seed/6.jpg', 150.00, 'MEET', 'DONE', 'ALIPAY', DATE_SUB(NOW(), INTERVAL 29 DAY), NULL, DATE_SUB(NOW(), INTERVAL 28 DAY), NULL, NULL, '南校区 图书馆门口', NULL, 0, DATE_SUB(NOW(), INTERVAL 29 DAY), DATE_SUB(NOW(), INTERVAL 28 DAY)),
(34, '20260825120000000026', 63, 1, 7, 980.00, '索尼 WH-1000XM4 降噪耳机', '/uploads/seed/5.jpg', 980.00, 'MEET', 'DONE', 'BANK', DATE_SUB(NOW(), INTERVAL 70 DAY), NULL, DATE_SUB(NOW(), INTERVAL 69 DAY), NULL, NULL, '东校区 3 号宿舍楼下', NULL, 0, DATE_SUB(NOW(), INTERVAL 70 DAY), DATE_SUB(NOW(), INTERVAL 69 DAY)),
(35, '20261028120000000027', 64, 2, 4, 1120.00, '索尼 WH-1000XM4 降噪耳机', '/uploads/seed/6.jpg', 1120.00, 'MEET', 'DONE', 'WECHAT', DATE_SUB(NOW(), INTERVAL 6 DAY), NULL, DATE_SUB(NOW(), INTERVAL 5 DAY), NULL, NULL, '东校区 3 号宿舍楼下', NULL, 0, DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY)),
(36, '20261014120000000028', 65, 3, 8, 1250.00, '索尼 WH-1000XM4 降噪耳机', '/uploads/seed/1.jpg', 1250.00, 'MEET', 'DONE', 'ALIPAY', DATE_SUB(NOW(), INTERVAL 20 DAY), NULL, DATE_SUB(NOW(), INTERVAL 19 DAY), NULL, NULL, '东校区 3 号宿舍楼下', NULL, 0, DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_SUB(NOW(), INTERVAL 19 DAY)),
(37, '20260908120000000029', 66, 1, 5, 38.00, '张宇考研数学 2024 全套', '/uploads/seed/6.jpg', 38.00, 'MEET', 'DONE', 'BANK', DATE_SUB(NOW(), INTERVAL 56 DAY), NULL, DATE_SUB(NOW(), INTERVAL 55 DAY), NULL, NULL, '西校区 图书馆门口', NULL, 0, DATE_SUB(NOW(), INTERVAL 56 DAY), DATE_SUB(NOW(), INTERVAL 55 DAY)),
(38, '20261101120000000030', 67, 2, 6, 45.00, '张宇考研数学 2024 全套', '/uploads/seed/1.jpg', 45.00, 'MEET', 'DONE', 'WECHAT', DATE_SUB(NOW(), INTERVAL 2 DAY), NULL, DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, NULL, '西校区 图书馆门口', NULL, 0, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),
(39, '20261020120000000031', 68, 3, 8, 52.00, '张宇考研数学 2024 全套', '/uploads/seed/2.jpg', 52.00, 'MEET', 'DONE', 'ALIPAY', DATE_SUB(NOW(), INTERVAL 14 DAY), NULL, DATE_SUB(NOW(), INTERVAL 13 DAY), NULL, NULL, '西校区 图书馆门口', NULL, 0, DATE_SUB(NOW(), INTERVAL 14 DAY), DATE_SUB(NOW(), INTERVAL 13 DAY)),
(40, '20260920120000000032', 69, 1, 5, 30.00, '肖秀荣考研政治 全套 2024', '/uploads/seed/1.jpg', 30.00, 'MEET', 'DONE', 'BANK', DATE_SUB(NOW(), INTERVAL 44 DAY), NULL, DATE_SUB(NOW(), INTERVAL 43 DAY), NULL, NULL, '西校区 3 号宿舍楼下', NULL, 0, DATE_SUB(NOW(), INTERVAL 44 DAY), DATE_SUB(NOW(), INTERVAL 43 DAY)),
(41, '20260820120000000033', 70, 2, 8, 38.00, '肖秀荣考研政治 全套 2024', '/uploads/seed/2.jpg', 38.00, 'MEET', 'DONE', 'WECHAT', DATE_SUB(NOW(), INTERVAL 75 DAY), NULL, DATE_SUB(NOW(), INTERVAL 74 DAY), NULL, NULL, '西校区 3 号宿舍楼下', NULL, 0, DATE_SUB(NOW(), INTERVAL 75 DAY), DATE_SUB(NOW(), INTERVAL 74 DAY)),
(42, '20261026120000000034', 71, 3, 4, 45.00, '肖秀荣考研政治 全套 2024', '/uploads/seed/3.jpg', 45.00, 'MEET', 'DONE', 'ALIPAY', DATE_SUB(NOW(), INTERVAL 8 DAY), NULL, DATE_SUB(NOW(), INTERVAL 7 DAY), NULL, NULL, '西校区 3 号宿舍楼下', NULL, 0, DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY)),
(43, '20261002120000000035', 72, 1, 5, 12.00, '高等数学 同济第七版 上下册', '/uploads/seed/2.jpg', 12.00, 'MEET', 'DONE', 'BANK', DATE_SUB(NOW(), INTERVAL 32 DAY), NULL, DATE_SUB(NOW(), INTERVAL 31 DAY), NULL, NULL, '西校区 图书馆门口', NULL, 0, DATE_SUB(NOW(), INTERVAL 32 DAY), DATE_SUB(NOW(), INTERVAL 31 DAY)),
(44, '20260904120000000036', 73, 2, 4, 16.00, '高等数学 同济第七版 上下册', '/uploads/seed/3.jpg', 16.00, 'MEET', 'DONE', 'WECHAT', DATE_SUB(NOW(), INTERVAL 60 DAY), NULL, DATE_SUB(NOW(), INTERVAL 59 DAY), NULL, NULL, '西校区 图书馆门口', NULL, 0, DATE_SUB(NOW(), INTERVAL 60 DAY), DATE_SUB(NOW(), INTERVAL 59 DAY)),
(45, '20261031120000000037', 74, 3, 6, 20.00, '高等数学 同济第七版 上下册', '/uploads/seed/4.jpg', 20.00, 'MEET', 'DONE', 'ALIPAY', DATE_SUB(NOW(), INTERVAL 3 DAY), NULL, DATE_SUB(NOW(), INTERVAL 2 DAY), NULL, NULL, '西校区 图书馆门口', NULL, 0, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)),
(46, '20261011120000000038', 75, 1, 5, 52.00, '算法导论 中文第三版', '/uploads/seed/3.jpg', 52.00, 'MEET', 'DONE', 'BANK', DATE_SUB(NOW(), INTERVAL 23 DAY), NULL, DATE_SUB(NOW(), INTERVAL 22 DAY), NULL, NULL, '西校区 3 号宿舍楼下', NULL, 0, DATE_SUB(NOW(), INTERVAL 23 DAY), DATE_SUB(NOW(), INTERVAL 22 DAY)),
(47, '20260916120000000039', 76, 2, 7, 60.00, '算法导论 中文第三版', '/uploads/seed/4.jpg', 60.00, 'MEET', 'DONE', 'WECHAT', DATE_SUB(NOW(), INTERVAL 48 DAY), NULL, DATE_SUB(NOW(), INTERVAL 47 DAY), NULL, NULL, '西校区 3 号宿舍楼下', NULL, 0, DATE_SUB(NOW(), INTERVAL 48 DAY), DATE_SUB(NOW(), INTERVAL 47 DAY)),
(48, '20260813120000000040', 77, 3, 8, 68.00, '算法导论 中文第三版', '/uploads/seed/5.jpg', 68.00, 'MEET', 'DONE', 'ALIPAY', DATE_SUB(NOW(), INTERVAL 82 DAY), NULL, DATE_SUB(NOW(), INTERVAL 81 DAY), NULL, NULL, '西校区 3 号宿舍楼下', NULL, 0, DATE_SUB(NOW(), INTERVAL 82 DAY), DATE_SUB(NOW(), INTERVAL 81 DAY)),
(49, '20261018120000000041', 78, 1, 5, 15.00, '大学英语四级真题 2024.12', '/uploads/seed/4.jpg', 15.00, 'MEET', 'DONE', 'BANK', DATE_SUB(NOW(), INTERVAL 16 DAY), NULL, DATE_SUB(NOW(), INTERVAL 15 DAY), NULL, NULL, '西校区 图书馆门口', NULL, 0, DATE_SUB(NOW(), INTERVAL 16 DAY), DATE_SUB(NOW(), INTERVAL 15 DAY)),
(50, '20260928120000000042', 79, 2, 6, 22.00, '大学英语四级真题 2024.12', '/uploads/seed/5.jpg', 22.00, 'MEET', 'DONE', 'WECHAT', DATE_SUB(NOW(), INTERVAL 36 DAY), NULL, DATE_SUB(NOW(), INTERVAL 35 DAY), NULL, NULL, '西校区 图书馆门口', NULL, 0, DATE_SUB(NOW(), INTERVAL 36 DAY), DATE_SUB(NOW(), INTERVAL 35 DAY)),
(51, '20260813120000000043', 80, 1, 6, 78.00, '小米台灯 Pro 护眼版', '/uploads/seed/5.jpg', 78.00, 'MEET', 'DONE', 'ALIPAY', DATE_SUB(NOW(), INTERVAL 82 DAY), NULL, DATE_SUB(NOW(), INTERVAL 81 DAY), NULL, NULL, '南校区 3 号宿舍楼下', NULL, 0, DATE_SUB(NOW(), INTERVAL 82 DAY), DATE_SUB(NOW(), INTERVAL 81 DAY)),
(52, '20261024120000000044', 81, 2, 5, 95.00, '小米台灯 Pro 护眼版', '/uploads/seed/6.jpg', 95.00, 'MEET', 'DONE', 'BANK', DATE_SUB(NOW(), INTERVAL 10 DAY), NULL, DATE_SUB(NOW(), INTERVAL 9 DAY), NULL, NULL, '南校区 3 号宿舍楼下', NULL, 0, DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 9 DAY)),
(53, '20261008120000000045', 82, 3, 7, 110.00, '小米台灯 Pro 护眼版', '/uploads/seed/1.jpg', 110.00, 'MEET', 'DONE', 'WECHAT', DATE_SUB(NOW(), INTERVAL 26 DAY), NULL, DATE_SUB(NOW(), INTERVAL 25 DAY), NULL, NULL, '南校区 3 号宿舍楼下', NULL, 0, DATE_SUB(NOW(), INTERVAL 26 DAY), DATE_SUB(NOW(), INTERVAL 25 DAY)),
(54, '20260830120000000046', 83, 1, 6, 260.00, '宜家马库斯电脑椅 转椅', '/uploads/seed/6.jpg', 260.00, 'MEET', 'DONE', 'ALIPAY', DATE_SUB(NOW(), INTERVAL 65 DAY), NULL, DATE_SUB(NOW(), INTERVAL 64 DAY), NULL, NULL, '南校区 图书馆门口', NULL, 0, DATE_SUB(NOW(), INTERVAL 65 DAY), DATE_SUB(NOW(), INTERVAL 64 DAY)),
(55, '20261029120000000047', 84, 2, 8, 320.00, '宜家马库斯电脑椅 转椅', '/uploads/seed/1.jpg', 320.00, 'MEET', 'DONE', 'BANK', DATE_SUB(NOW(), INTERVAL 5 DAY), NULL, DATE_SUB(NOW(), INTERVAL 4 DAY), NULL, NULL, '南校区 图书馆门口', NULL, 0, DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY)),
(56, '20261008120000000048', 85, 1, 6, 85.00, '小米电饭煲 3L 家用', '/uploads/seed/1.jpg', 85.00, 'MEET', 'DONE', 'WECHAT', DATE_SUB(NOW(), INTERVAL 26 DAY), NULL, DATE_SUB(NOW(), INTERVAL 25 DAY), NULL, NULL, '南校区 3 号宿舍楼下', NULL, 0, DATE_SUB(NOW(), INTERVAL 26 DAY), DATE_SUB(NOW(), INTERVAL 25 DAY)),
(57, '20260912120000000049', 86, 2, 7, 100.00, '小米电饭煲 3L 家用', '/uploads/seed/2.jpg', 100.00, 'MEET', 'DONE', 'ALIPAY', DATE_SUB(NOW(), INTERVAL 52 DAY), NULL, DATE_SUB(NOW(), INTERVAL 51 DAY), NULL, NULL, '南校区 3 号宿舍楼下', NULL, 0, DATE_SUB(NOW(), INTERVAL 52 DAY), DATE_SUB(NOW(), INTERVAL 51 DAY)),
(58, '20260807120000000050', 87, 3, 4, 115.00, '小米电饭煲 3L 家用', '/uploads/seed/3.jpg', 115.00, 'MEET', 'DONE', 'BANK', DATE_SUB(NOW(), INTERVAL 88 DAY), NULL, DATE_SUB(NOW(), INTERVAL 87 DAY), NULL, NULL, '南校区 3 号宿舍楼下', NULL, 0, DATE_SUB(NOW(), INTERVAL 88 DAY), DATE_SUB(NOW(), INTERVAL 87 DAY)),
(59, '20261016120000000051', 88, 1, 6, 105.00, '24寸行李箱 静音万向轮', '/uploads/seed/2.jpg', 105.00, 'MEET', 'DONE', 'WECHAT', DATE_SUB(NOW(), INTERVAL 18 DAY), NULL, DATE_SUB(NOW(), INTERVAL 17 DAY), NULL, NULL, '南校区 图书馆门口', NULL, 0, DATE_SUB(NOW(), INTERVAL 18 DAY), DATE_SUB(NOW(), INTERVAL 17 DAY)),
(60, '20260924120000000052', 89, 2, 4, 135.00, '24寸行李箱 静音万向轮', '/uploads/seed/3.jpg', 135.00, 'MEET', 'DONE', 'ALIPAY', DATE_SUB(NOW(), INTERVAL 40 DAY), NULL, DATE_SUB(NOW(), INTERVAL 39 DAY), NULL, NULL, '南校区 图书馆门口', NULL, 0, DATE_SUB(NOW(), INTERVAL 40 DAY), DATE_SUB(NOW(), INTERVAL 39 DAY)),
(61, '20260825120000000053', 90, 3, 8, 160.00, '24寸行李箱 静音万向轮', '/uploads/seed/4.jpg', 160.00, 'MEET', 'DONE', 'BANK', DATE_SUB(NOW(), INTERVAL 70 DAY), NULL, DATE_SUB(NOW(), INTERVAL 69 DAY), NULL, NULL, '南校区 图书馆门口', NULL, 0, DATE_SUB(NOW(), INTERVAL 70 DAY), DATE_SUB(NOW(), INTERVAL 69 DAY)),
(62, '20261022120000000054', 91, 1, 8, 88.00, '斯伯丁篮球 7号 PU', '/uploads/seed/3.jpg', 88.00, 'MEET', 'DONE', 'WECHAT', DATE_SUB(NOW(), INTERVAL 12 DAY), NULL, DATE_SUB(NOW(), INTERVAL 11 DAY), NULL, NULL, '东校区 3 号宿舍楼下', NULL, 0, DATE_SUB(NOW(), INTERVAL 12 DAY), DATE_SUB(NOW(), INTERVAL 11 DAY)),
(63, '20261005120000000055', 92, 2, 5, 105.00, '斯伯丁篮球 7号 PU', '/uploads/seed/4.jpg', 105.00, 'MEET', 'DONE', 'ALIPAY', DATE_SUB(NOW(), INTERVAL 29 DAY), NULL, DATE_SUB(NOW(), INTERVAL 28 DAY), NULL, NULL, '东校区 3 号宿舍楼下', NULL, 0, DATE_SUB(NOW(), INTERVAL 29 DAY), DATE_SUB(NOW(), INTERVAL 28 DAY)),
(64, '20260908120000000056', 93, 3, 6, 125.00, '斯伯丁篮球 7号 PU', '/uploads/seed/5.jpg', 125.00, 'MEET', 'DONE', 'BANK', DATE_SUB(NOW(), INTERVAL 56 DAY), NULL, DATE_SUB(NOW(), INTERVAL 55 DAY), NULL, NULL, '东校区 3 号宿舍楼下', NULL, 0, DATE_SUB(NOW(), INTERVAL 56 DAY), DATE_SUB(NOW(), INTERVAL 55 DAY)),
(65, '20261028120000000057', 94, 1, 8, 150.00, '哑铃 20kg 可调节套装', '/uploads/seed/4.jpg', 150.00, 'MEET', 'DONE', 'WECHAT', DATE_SUB(NOW(), INTERVAL 6 DAY), NULL, DATE_SUB(NOW(), INTERVAL 5 DAY), NULL, NULL, '东校区 图书馆门口', NULL, 0, DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY)),
(66, '20261014120000000058', 95, 2, 6, 185.00, '哑铃 20kg 可调节套装', '/uploads/seed/5.jpg', 185.00, 'MEET', 'DONE', 'ALIPAY', DATE_SUB(NOW(), INTERVAL 20 DAY), NULL, DATE_SUB(NOW(), INTERVAL 19 DAY), NULL, NULL, '东校区 图书馆门口', NULL, 0, DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_SUB(NOW(), INTERVAL 19 DAY)),
(67, '20260920120000000059', 96, 3, 4, 210.00, '哑铃 20kg 可调节套装', '/uploads/seed/6.jpg', 210.00, 'MEET', 'DONE', 'BANK', DATE_SUB(NOW(), INTERVAL 44 DAY), NULL, DATE_SUB(NOW(), INTERVAL 43 DAY), NULL, NULL, '东校区 图书馆门口', NULL, 0, DATE_SUB(NOW(), INTERVAL 44 DAY), DATE_SUB(NOW(), INTERVAL 43 DAY)),
(68, '20261101120000000060', 97, 1, 8, 195.00, '网球拍 碳素初学 带线', '/uploads/seed/5.jpg', 195.00, 'MEET', 'DONE', 'WECHAT', DATE_SUB(NOW(), INTERVAL 2 DAY), NULL, DATE_SUB(NOW(), INTERVAL 1 DAY), NULL, NULL, '东校区 3 号宿舍楼下', NULL, 0, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),
(69, '20261020120000000061', 98, 2, 5, 240.00, '网球拍 碳素初学 带线', '/uploads/seed/6.jpg', 240.00, 'MEET', 'DONE', 'ALIPAY', DATE_SUB(NOW(), INTERVAL 14 DAY), NULL, DATE_SUB(NOW(), INTERVAL 13 DAY), NULL, NULL, '东校区 3 号宿舍楼下', NULL, 0, DATE_SUB(NOW(), INTERVAL 14 DAY), DATE_SUB(NOW(), INTERVAL 13 DAY)),
(70, '20260920120000000062', 99, 1, 8, 1580.00, '雅迪电动车 48V12A', '/uploads/seed/6.jpg', 1580.00, 'MEET', 'DONE', 'BANK', DATE_SUB(NOW(), INTERVAL 44 DAY), NULL, DATE_SUB(NOW(), INTERVAL 43 DAY), NULL, NULL, '东校区 图书馆门口', NULL, 0, DATE_SUB(NOW(), INTERVAL 44 DAY), DATE_SUB(NOW(), INTERVAL 43 DAY)),
(71, '20260820120000000063', 100, 2, 7, 1850.00, '雅迪电动车 48V12A', '/uploads/seed/1.jpg', 1850.00, 'MEET', 'DONE', 'WECHAT', DATE_SUB(NOW(), INTERVAL 75 DAY), NULL, DATE_SUB(NOW(), INTERVAL 74 DAY), NULL, NULL, '东校区 图书馆门口', NULL, 0, DATE_SUB(NOW(), INTERVAL 75 DAY), DATE_SUB(NOW(), INTERVAL 74 DAY)),
(72, '20261026120000000064', 101, 3, 6, 2100.00, '雅迪电动车 48V12A', '/uploads/seed/2.jpg', 2100.00, 'MEET', 'DONE', 'ALIPAY', DATE_SUB(NOW(), INTERVAL 8 DAY), NULL, DATE_SUB(NOW(), INTERVAL 7 DAY), NULL, NULL, '东校区 图书馆门口', NULL, 0, DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY));

-- ====================================================================
-- 测试账号精简：只保留两个演示账号
--   id=1 student01 小林同学   —— 买家用（历史上有 10 笔卖出，见下方翻转）
--   id=4 seller01  阿哲的数码铺 —— 卖+买用（主力卖家）
-- 其余种子用户删除，所有商品/订单归属收敛到这两个账号，
-- 保证订单页/评价页的对方昵称永远可显示（不出现已注销用户）。
-- ====================================================================
UPDATE t_item SET seller_id = 4 WHERE seller_id NOT IN (1, 4);
UPDATE t_order SET seller_id = 4 WHERE seller_id NOT IN (1, 4);
UPDATE t_order SET buyer_id  = 1 WHERE buyer_id  NOT IN (1, 4);

-- 挑 10 笔跨分类/跨月份的 DONE 订单翻转为「阿哲买入」（卖家=小林），
-- 让两个账号的买入/卖出可视化数据都足够丰满
UPDATE t_order SET buyer_id = 4, seller_id = 1
WHERE id IN (5, 16, 31, 32, 34, 43, 49, 51, 62, 64);
UPDATE t_item SET seller_id = 1
WHERE id IN (21, 45, 60, 61, 63, 72, 79, 80, 91, 93);

UPDATE t_review SET seller_id = 1 WHERE order_id IN (5, 16, 31, 32, 34, 43, 49, 51, 62, 64);
UPDATE t_review SET seller_id = 4 WHERE seller_id NOT IN (1, 4);
UPDATE t_review SET buyer_id  = 1 WHERE buyer_id  NOT IN (1, 4);
UPDATE t_item_comment SET user_id = 1 WHERE user_id NOT IN (1, 4);
DELETE FROM t_favorite        WHERE user_id NOT IN (1, 4);
DELETE FROM t_report          WHERE reporter_id NOT IN (1, 4);
DELETE FROM t_chat_message    WHERE from_user_id NOT IN (1, 4) OR to_user_id NOT IN (1, 4);
DELETE FROM t_ai_estimate_log WHERE user_id NOT IN (1, 4);
DELETE FROM t_user            WHERE id NOT IN (1, 4);

-- 已成交商品的挂牌时间前移 3~12 天，让「平均成交天数」指标更真实
UPDATE t_item i
JOIN t_order o ON o.item_id = i.id AND o.deleted = 0 AND o.status = 'DONE'
SET i.created_at = DATE_SUB(o.created_at, INTERVAL (3 + FLOOR(RAND() * 10)) DAY),
    i.updated_at = o.created_at
WHERE i.deleted = 0;

-- ====================================================================
-- 二级分类（发布页级联选择：大类 → 子类，回收平台式引导）
-- id 7~33 与线上库按相同顺序自增，保持一致
-- ====================================================================
INSERT INTO t_category (id, name, parent_id, icon, sort, deleted, created_at, updated_at) VALUES
(7,  '手机/平板',      1, 'Cellphone',   11, 0, NOW(), NOW()),
(8,  '耳机/音箱',      1, 'Headset',     12, 0, NOW(), NOW()),
(9,  '相机/无人机',    1, 'Camera',      13, 0, NOW(), NOW()),
(10, '智能手表/手环',  1, 'Watch',       14, 0, NOW(), NOW()),
(11, '游戏设备',       1, 'Switch',      15, 0, NOW(), NOW()),
(12, '数码配件',       1, 'Connection',  16, 0, NOW(), NOW()),
(13, '笔记本',         2, 'Laptop',      21, 0, NOW(), NOW()),
(14, '台式机/一体机',  2, 'Platform',    22, 0, NOW(), NOW()),
(15, '显示器',         2, 'Monitor',     23, 0, NOW(), NOW()),
(16, '键鼠外设',       2, 'Mouse',       24, 0, NOW(), NOW()),
(17, '打印机/办公设备', 2, 'Printer',    25, 0, NOW(), NOW()),
(18, '教材/教辅',      3, 'Notebook',    31, 0, NOW(), NOW()),
(19, '考试资料',       3, 'Document',    32, 0, NOW(), NOW()),
(20, '文学小说',       3, 'Reading',     33, 0, NOW(), NOW()),
(21, '课外读物',       3, 'Collection',  34, 0, NOW(), NOW()),
(22, '小家电',         4, 'Coffee',      41, 0, NOW(), NOW()),
(23, '家具/床品',      4, 'Bed',         42, 0, NOW(), NOW()),
(24, '厨房用品',       4, 'Dish',        43, 0, NOW(), NOW()),
(25, '收纳/清洁',      4, 'Brush',       44, 0, NOW(), NOW()),
(26, '日用百货',       4, 'ShoppingCart',45, 0, NOW(), NOW()),
(27, '球类运动',       5, 'Basketball',  51, 0, NOW(), NOW()),
(28, '健身器材',       5, 'Trophy',      52, 0, NOW(), NOW()),
(29, '户外装备',       5, 'Place',       53, 0, NOW(), NOW()),
(30, '骑行/球鞋',      5, 'Bicycle',     54, 0, NOW(), NOW()),
(38, '吉他/贝斯',     34, 'Music',       71, 0, NOW(), NOW()),
(39, '键盘/电子琴',   34, 'Microphone',  72, 0, NOW(), NOW()),
(40, '管弦/打击',     34, 'Microphone',  73, 0, NOW(), NOW()),
(41, '乐器配件',      34, 'Connection',  74, 0, NOW(), NOW()),
(42, '服装',          35, 'ShoppingBag', 75, 0, NOW(), NOW()),
(43, '鞋靴',          35, 'ShoppingBag', 76, 0, NOW(), NOW()),
(44, '箱包',          35, 'ShoppingBag', 77, 0, NOW(), NOW()),
(45, '配饰',          35, 'ShoppingBag', 78, 0, NOW(), NOW()),
(46, '自行车',        36, 'Bicycle',     79, 0, NOW(), NOW()),
(47, '电动车',        36, 'Bicycle',     80, 0, NOW(), NOW()),
(48, '滑板/平衡车',   36, 'Bicycle',     81, 0, NOW(), NOW()),
(49, '电子书/阅读器',  1, 'Reading',     85, 0, NOW(), NOW()),
(53, '存储/硬盘',      1, 'Connection',  86, 0, NOW(), NOW()),
(54, '网络设备',       2, 'Connection',  87, 0, NOW(), NOW()),
(55, '笔记讲义',       3, 'Document',    88, 0, NOW(), NOW()),
(56, '餐具水杯',       4, 'Dish',        89, 0, NOW(), NOW());
