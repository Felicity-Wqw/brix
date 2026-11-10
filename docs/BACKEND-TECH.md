# 后端技术实现说明（JWT / WebSocket / AI 服务）

> 讲清楚三个"为什么"和"怎么做"：登录态为什么用 JWT 不用 Session、私信为什么要 WebSocket、AI 服务是怎么搭起来并且提示词怎么写的。
> 配套：[BACKEND-AI-DESIGN.md](BACKEND-AI-DESIGN.md)（简版）· [DESIGN.md](DESIGN.md)（架构图/E-R）

---

## 一、一次请求的完整链路

```
浏览器 axios（请求头 Authorization: Bearer <token>）
   ↓
CORS 过滤器（预检 OPTIONS 直接放行）
   ↓
JwtAuthFilter（OncePerRequestFilter）——解析 token → 查库校验 → 写入 SecurityContext
   ↓
Spring Security 规则判定（permitAll / authenticated）
   ↓
Controller（@Valid 参数校验）→ Service（@Transactional 业务与事务）→ Mapper → MySQL
   ↓
统一响应 R{code, msg, data, timestamp}（业务失败 HTTP 仍 200）
```

---

## 二、JWT 认证：为什么不使用 Session

### 2.1 概念澄清

| 方案 | 登录态存在哪 | 服务端是否有状态 | 本项目是否使用 |
| --- | --- | --- | --- |
| **HttpSession**（传统） | 服务端内存 + 浏览器 Cookie（JSESSIONID） | 有状态，需会话存储 | ❌ 不用 |
| **JWT**（本项目） | 客户端 localStorage 里的签名 token | 无状态，服务端不存会话 | ✅ 使用 |
| **WebSocket Session** | Tomcat 的 `jakarta.websocket.Session` 对象 | 长连接对象，不是登录态 | ✅ 私信用 |

> 答辩常见误区：把 WebSocket 的 `Session` 和 HttpSession 混为一谈。**本项目登录态完全无状态（JWT），`Session` 只出现在 WebSocket 连接管理里。**

### 2.2 JWT 是什么、解决什么问题

JWT = 三段 Base64 字符串（header.payload.signature）。它解决的唯一问题：**服务端不用存会话，也能确认"你是谁"**——因为 payload 被密钥签名，客户端改一位，验签就失败。

### 2.3 本项目的 token 设计

```java
// JwtUtil.generate()
Jwts.builder()
    .subject(String.valueOf(userId))      // sub = 用户 ID
    .claim("role", role)                  // USER / REVIEWER / OPERATOR
    .claim("nickname", nickname)          // 展示用
    .issuedAt(now)                        // iat 签发时间
    .expiration(now + 24h)                // exp 有效期 24 小时
    .signWith(HS256 密钥)                  // 对称签名，密钥来自环境变量
```

**载荷里绝不放密码、手机号等敏感信息**——JWT 只是 Base64 编码，任何人可解码查看，签名只保证不被篡改。

### 2.4 认证过滤器做了什么（三条硬规则）

```java
// JwtAuthFilter.doFilterInternal()
① 没有 Authorization 头 → 直接放行（是否需要登录交给 Security 的 URL 规则）
② 有 token 但解析失败（过期/签名错/格式错）→ 立刻写 401 并 return
   —— 不能继续往下走，否则请求会被 permitAll 误放行
③ 解析成功后每次都按 userId 查库：
   · 用户不存在 → 401
   · status = DISABLED → 403「账号已被禁用」
   —— 这样"禁用账号"能立即生效，不用等 token 过期
最后把 LoginUser 塞进 SecurityContext，后续用 SecurityUtils.currentUserId() 取
```

> **无状态的代价**：标准 JWT 本可以完全不查库，但那样禁用用户要等 24 小时才失效，所以这里每次请求查一次主键（`selectById`，走聚簇索引，成本极低），换取「封号即时生效」。

### 2.5 URL 权限规则（SecurityConfig）

```java
/auth/register, /auth/login          permitAll   // 注册登录
/uploads/**, /ws/**, /error          permitAll   // 静态资源、WS 握手、错误页
GET /categories                       permitAll   // 分类（首页图标卡）
GET /items/**                         permitAll   // 商品浏览（游客模式靠这个）
GET /items/mine                       authenticated  // ⚠ 必须先于 /items/** 声明
GET /items/*/comments                 permitAll   // 留言读取
GET /users/*/reviews                  permitAll   // 卖家评价
其余                                    authenticated
```

> 顺序陷阱：`/items/mine` 必须写在 `/items/**` 之前，否则会被 `permitAll` 通配吞掉——Spring Security 的 match 是**先到先得**。

### 2.6 前端配合

- token 存 localStorage，axios 请求拦截器统一加 `Authorization: Bearer`
- 响应 401 → 清空登录态 → 跳 `/login?redirect=<当前地址>`，登录后回跳
- 密码用 BCrypt 加盐哈希（`{bcrypt}` 前缀），不可逆

---

## 三、WebSocket：私信为什么不用轮询

### 3.1 两种方案对比

| 方案 | 实时性 | 服务器压力 | 实现成本 |
| --- | --- | --- | --- |
| HTTP 轮询（3 秒一次） | 延迟 0-3 秒 | 每人每分钟 20 次空请求 | 低 |
| **WebSocket 长连接**（本项目） | 毫秒级 | 连接常驻，心跳维持 | 中 |

私信是高频交互场景，轮询要么慢要么浪费，因此用 WebSocket。

### 3.2 连接与握手

```
ws://localhost:8080/ws/chat?token=<JWT>
```

用 `@ServerEndpoint("/ws/chat")` 而不是 Spring 的 `WebSocketHandler`：**握手时只需从 query string 取 token**，实现量最小。

```java
@OnOpen
public void onOpen(Session session) {
    Long userId = 从 query 解析 token;              // 失败 → 关闭连接
    User user = userMapper.selectById(userId);
    if (user == null || DISABLED) closeQuietly();   // 禁用/不存在 → 关闭
    wsSessionManager.add(userId, session);          // 用户 → 连接 的映射
}
```

**多端登录**：`WsSessionManager` 支持一个用户挂多条连接（手机+电脑），推送时遍历该用户所有 session。

### 3.3 帧协议（JSON）

| type | 方向 | 含义 |
| --- | --- | --- |
| `INIT` | 客户端 → 服务端 | 绑定用户（兼容通过 REST 登录后建连） |
| `CHAT` | 双向 | 发消息：服务端先落库，再推给对端，并回 ACK 给发送方 |
| `ACK` | 服务端 → 客户端 | 发送回执（多标签页同步、失败重试判断） |
| `PING` / `PONG` | 双向 | 心跳保活，前端 30 秒发一次 PING；断线 3 秒后自动重连（持续重试） |
| `ERROR` | 服务端 | 单条帧有误只回错误，**不断连** |

关键设计：**消息必须"先落库再推送"**——即使对方离线、推送失败，历史消息也能通过 REST 拉取，保证不丢消息。送达不等于已读，已读由 `PUT /chat/read` 单独标记。

### 3.4 一个真实坑：端点实例与依赖注入

容器为**每个连接 new 一个端点实例**，Spring 不会给它注入依赖。因此用「静态字段 + `@Autowired` setter 赋值」共享单例 Bean（`JwtUtil / UserMapper / WsSessionManager / ChatService`）。这是 `@ServerEndpoint` 方案的标配写法。

---

## 四、其他后端机制速览

| 机制 | 实现 |
| --- | --- |
| 统一响应 | `R{code,msg,data,timestamp}`，业务失败 HTTP 仍 200；Spring Security 的 401/403 保持真实 HTTP 码 |
| 全局异常 | `@RestControllerAdvice` 捕获 `BusinessException` / 校验异常 / 兜底 500 |
| 事务边界 | `pay`（抢商品 + 关同品待付款单）/ `cancel`（回滚在售）/ `confirm`（完成 + 托管结算）|
| 防超卖 | `UPDATE ... WHERE status='ON_SALE'` 判影响行数（CAS），不 `FOR UPDATE` |
| 定时任务 | `@Scheduled` 每分钟扫描待付款超 30 分钟订单 → 关闭 + 商品回滚 |
| 逻辑删除 | MyBatis-Plus `@TableLogic`，全表 `deleted` 字段 |
| 序列化 | `Long` 全部转字符串（防 JS 精度丢失）、日期统一 `yyyy-MM-dd HH:mm:ss` |
| 文件上传 | 校验类型/大小 → 存 `uploads/{biz}/` → 静态映射 `/uploads/**` |

---

## 五、AI 服务怎么搭的

### 5.1 依赖与装配（不用 starter，手工装配）

```java
// AiConfig
@Bean RuleBasedEstimator ruleBasedEstimator(...)   // 永远存在：无 Key 时的唯一实现 + LLM 的降级目标

@Bean
@ConditionalOnExpression("!'${brix.ai.api-key:} '.isBlank()")  // 有 Key 才创建
ChatModel chatModel(...) {
    return OpenAiChatModel.builder()
        .baseUrl("https://api.deepseek.com/v1")   // OpenAI 兼容协议，DeepSeek 直接可用
        .modelName("deepseek-chat")
        .temperature(0.3)        // 低温度：定价要稳定，不要发散
        .timeout(Duration.ofSeconds(10))  // 10 秒超时
        .maxRetries(0)           // 不重试 → 直接降级，不给用户叠加延迟
        .build();
}

@Bean @Primary  // 有 Key 时 LLM 优先注入，规则引擎留作降级
LlmEstimator llmEstimator(ChatModel model, ItemEstimationAgent agent) { ... }
```

> 为什么不用 `langchain4j-spring-boot-starter`：稳定核心包 + 手工装配，**功能等价、无 beta 依赖、无 Key 时启动零报错**。这是「AI 可选」的落地关键。

### 5.2 智能体的定义方式：接口即智能体

```java
public interface ItemEstimationAgent {
    @SystemMessage(AiPrompts.ESTIMATE_SYSTEM)      // 系统提示词：角色 + 规则
    EstimateResult estimate(@UserMessage String userPrompt);   // 返回 POJO → 自动 JSON 反序列化

    @SystemMessage(AiPrompts.NEW_PRICE_SYSTEM)
    NewPriceHint estimateNewPrice(@UserMessage String userPrompt);

    @SystemMessage(AiPrompts.COPY_SYSTEM)
    CopyResult copywrite(@UserMessage String userPrompt);
}
```

`AiServices.builder(ItemEstimationAgent.class).chatModel(model).tools(aiTools).build()` —— 把「工具集」挂上去，模型自己决定调几次、调哪个。

### 5.3 工具调用：让模型查真实数据，而不是编

```java
@Component
public class AiTools {
    @Tool("查询近 90 天成交统计（优先调用）。keyword 必须传能唯一确定具体款式的词"
        + "（如「iPhone 13」），禁止传品牌名。返回统计 + 成交样本标题…")
    public String soldPriceStats(@P("keyword") String keyword, @P("categoryId") Long categoryId) {
        return aiRepository.soldPriceStats(keyword);   // 内部：LIKE 查订单表 + 统计
    }

    @Tool("查询同款在售最低价（元），无数据返回 0")
    public BigDecimal onSaleLowestPrice(...) { ... }

    @Tool("按成色返回折价系数：BRAND_NEW=1.0、LIKE_NEW=0.85、LIGHT_USE=0.70、HEAVY_USE=0.55")
    public double conditionDiscountFactor(@P("conditionLevel") String level) { ... }
}
```

**工具是"防幻觉"的核心**：价格必须来自数据库或明确的折价公式，模型不能在提示词里凭空编一个数字。

### 5.4 提示词怎么写的（真实片段）

**估价系统提示词**（节选）：

```
你是校园二手交易平台的定价顾问，只服务在校大学生。
规则：
1) 必须先调用工具查询真实数据：soldPriceStats、onSaleLowestPrice、conditionDiscountFactor。
2) soldPriceStats 的 keyword 必须用能唯一确定具体款式的词（如「iPhone 13」「小黑瓶精华」），
   优先取待估商品标题的核心词；查到 0 笔属正常，严禁把关键词放宽成品牌名去"凑"数据。
3) 同款判定（最重要）：工具返回的成交样本标题必须逐一核对——同品牌但不同产品线
   （如兰蔻小黑瓶 vs 兰蔻保湿水）、不同容量/型号，一律视为不同产品，必须完全忽略该成交数据；
   此时改用「用户原价 × 成色折价系数」估算，并在 reasons 中注明「按原价折价估算」。
3a) 用户消息可能附带【定价参考数据包】……优先级：同款成交 > 用户原价 > LLM 新品价估计。
4) 只输出 JSON，不要输出任何解释文字或 Markdown 代码块。
5) JSON 字段必须与 EstimateResult 完全一致：{suggestPrice, priceRange{min,max}, confidence, reasons, riskTips}
6) reasons 至少 2 条，且必须包含具体金额。
7) suggestPrice 必须落在 priceRange 区间内。
8) 不提供违规、翻新冒充全新等建议。
```

**审核系统提示词**（节选）：

```
你是上架审核专员，严格依据《电子商务法》《广告法》《商标法》《网络交易监督管理办法》等 8 部法规。
判定四选一：PASS / REJECT_BAN / REJECT_REVISE / REPORT_RISK，每条规则标注对应法条：
【1 高危禁售 → REJECT_BAN｜依据：电子商务法13条、电子烟管理办法…】管制刀具、枪支配件、处方药、烟草…
【2 假冒侵权 → REJECT_BAN｜依据：商标法57条】高仿、复刻、A货、盗图…
【3 引导站外交易 → REJECT_BAN】微信、QQ、加好友、私下转账…
```

**提示词设计原则**（答辩可讲）：

1. **规则编号**：`1) 2) 3)` 可被引用、可被评测，方便定位"第 3 条没遵守"
2. **只输出 JSON**：禁止 Markdown 代码块，否则反序列化失败
3. **禁止"凑数据"**：明确 0 笔是正常结果，堵住模型"换个宽泛关键词找数据"的偷懒路径
4. **法条依据**：审核不是主观判断，每条规则挂法规，可解释、可申诉
5. **真名实姓的反例**：把真实 bug（兰蔻小黑瓶 vs 保湿水）写进提示词，比抽象描述有效得多

### 5.5 两阶段估价的两次调用

```java
// AiEstimateServiceImpl.estimate()
String hints = null;
if (llm 路径) {
    hints = buildPricingHints(profile);
    // ① 无原价 → 调 LLM 估「全新参考价区间」（NEW_PRICE_SYSTEM），× 折价系数 = 第二锚点
    // ② 有原价 → 直接算出「原价 × 系数」
}
// ③ 主估价：数据包 + 工具调用一起给模型
result = estimator.estimate(profile, hints);
// ④ 清洗：二手价硬钳制 ≤ 原价×0.98、区间合法化、reasons 补足
result = AiResultSanitizer.sanitize(result, profile);
```

一次用户请求内部最多 2 次 LLM 调用（新品价 + 主估价），**但额度只扣 1 次**——额度衡量的是"功能使用次数"，不是 token 消耗。

### 5.6 降级与额度

| 场景 | 行为 |
| --- | --- |
| 无 Key | 整个 AI 模块不装配 LLM，估价走规则引擎（分类基准价 × 成色 × 年限），文案走模板 |
| 超时（>10s）/ 5xx / 解析失败 | catch 后切规则引擎，接口仍返回 200，`source="RULE"` |
| 额度用尽（每日 10 次） | 提示「今日额度已用完」，不调用 LLM |
| 结果不合规 | `AiResultSanitizer` 兜底：钳制价格、补足依据、限制标签数 |

所有调用记入 `t_ai_estimate_log`（scene/source/success/cost_ms/摘要），日志里**不存用户原文与手机号**，只存脱敏摘要。

---

## 六、答辩可用的一句话总结

> **登录态用 JWT 无状态认证（每次请求查库换即时封号）；私信用 WebSocket 长连接（先落库再推送保证不丢）；AI 用 LangChain4j 手工装配、工具调用取真数、提示词编号化约束输出、无 Key/超时/异常全部降级——三块设计都遵循同一个原则：核心链路绝不依赖外部不确定因素。**
