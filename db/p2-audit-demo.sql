-- ============================================================================
-- P2 审核数据 · 自查与说明（**只读，不含任何写操作**）
--
-- ⚠️ 重要变更（2026-11-07）：本脚本不再伪造任何审核数据。
--
--   早期版本为了让审核队列在答辩现场有内容，做过两件很糟糕的事：
--     1) 按价格随机挑几件在售商品，直接把 status 改成 PENDING / REJECTED；
--     2) 把 audit_reason 写死成「命中黑名单关键词「私下转账」」这类文案。
--   后果是数据自相矛盾：「小米无线鼠标，描述为备用鼠标买多了闲置」的商品，
--   审核日志却写着「命中私下转账」。审核员一点开详情就能看出判定说明与内容无关，
--   答辩时更是被追问一句就露馅。
--
--   结论：审核结论只能由 PublishAuditService 真实产出。任何绕过它的「造数」，
--   都会让 t_item 与 t_item_audit_log 同时失去可信度——而这两张表就是「平台有没有
--   认真审核」的唯一证据。宁可队列空着，也不要假数据。
--
--   因此本脚本只做三件事：打印真实数据现状、列出真实造样本的方法、校验数据一致性。
--   需要执行写操作时，请走真实业务流程（见第二节）。
--
-- 依赖：db/p2-migration.sql、db/p2-audit-migration.sql、db/p2-admin.sql、db/p2-audit-log.sql
-- ============================================================================

USE brix_campus;

-- ============================================================================
-- 一、当前真实数据现状
-- ============================================================================

-- 1.1 商品状态分布（PENDING=中风险待审，REJECTED=高风险已驳回）
SELECT '商品状态分布' AS section, status, COUNT(*) AS cnt
  FROM t_item WHERE deleted = 0
 GROUP BY status
 ORDER BY FIELD(status, 'PENDING', 'REJECTED', 'ON_SALE', 'OFF_SHELF', 'SOLD');

-- 1.2 审核队列：每条都应能打开「审核证据链」并看到与商品内容对得上的命中词
SELECT '审核队列' AS section,
       i.id, LEFT(i.title, 24) AS title, i.status, i.risk_level, i.audit_source,
       LEFT(i.audit_reason, 40) AS audit_reason
  FROM t_item i
 WHERE i.deleted = 0 AND i.status IN ('PENDING', 'REJECTED')
 ORDER BY FIELD(i.risk_level, 'HIGH', 'MEDIUM'), i.created_at DESC;

-- 1.3 审核日志：条数应与「低风险直发 + 中高风险拦截」的实际发布次数一致
SELECT '审核日志条数' AS section, COUNT(*) AS log_rows FROM t_item_audit_log;

SELECT '审核日志明细' AS section,
       id, item_id, LEFT(item_title, 20) AS item_title,
       gate1_result, gate1_word, gate2_result, gate2_verdict,
       risk_level, decision_status,
       CASE WHEN handler_id IS NULL THEN '待人工确认' ELSE '已人工处置' END AS handled,
       created_at
  FROM t_item_audit_log
 ORDER BY id;

-- 1.4 数据一致性自检：下面两条查询**必须返回 0 行**，否则说明存在绕过业务逻辑的脏数据
SELECT '异常：日志命中词与商品内容完全无关' AS check_name,
       l.id AS log_id, l.item_id, l.gate1_word, LEFT(i.title, 20) AS title
  FROM t_item_audit_log l
  JOIN t_item i ON i.id = l.item_id
 WHERE l.gate1_word IS NOT NULL
   AND i.deleted = 0
   AND CONCAT(i.title, i.description) NOT LIKE CONCAT('%', l.gate1_word, '%');

SELECT '异常：待审/驳回商品没有对应审核日志' AS check_name,
       i.id, LEFT(i.title, 20) AS title, i.status
  FROM t_item i
 WHERE i.deleted = 0
   AND i.status IN ('PENDING', 'REJECTED')
   AND NOT EXISTS (SELECT 1 FROM t_item_audit_log l WHERE l.item_id = i.id);

-- 1.5 举报队列
SELECT '举报队列' AS section, id, item_id, reporter_id, reason, status,
       LEFT(description, 30) AS description
  FROM t_report ORDER BY id;

-- ============================================================================
-- 二、如何造「真实」的审核样本（唯一正确的方式）
-- ============================================================================
--
-- 用 seller01 / 123456 登录前台（http://localhost:5173/#/login），逐条真实发布。
-- 系统会自己跑两道闸、自己落状态、自己写日志，全程不需要碰数据库：
--
--   | 标题                        | 命中                        | 预期结果        |
--   | --------------------------- | --------------------------- | --------------- |
--   | 斯伯丁篮球 7号 室外耐磨      | 两道闸都放行                | LOW   → 在售    |
--   | 水弹玩具枪 送两包弹          | 轻度词「玩具枪」+ AI 拿不准 | MEDIUM → 待审核 |
--   | 日本代购 保温杯 500ml        | 交易形态词「代购」          | MEDIUM → 待审核 |
--   | 演唱会门票转让 内场          | 交易形态词「门票转让」      | MEDIUM → 待审核 |
--   | 游戏账号出售 满级            | 交易形态词「游戏账号」      | MEDIUM → 待审核 |
--   | 代开发票 各类抬头            | 黑名单「代开发票」          | HIGH  → 已驳回  |
--   | 加微信详聊 有货              | 黑名单「加微信」            | HIGH  → 已驳回  |
--
--   举报数据同理：用 student01 打开某件在售商品，点「举报」真实提交一条。
--
-- 这样做的好处：t_item 的状态、t_item_audit_log 的快照、审核员看到的证据链、
-- 看板上的风险分布，四处数据同源，怎么交叉核对都对得上。
--
-- ============================================================================
