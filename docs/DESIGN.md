# 系统设计文档（DESIGN）

> 「物归校园」（Brix Campus Market）架构设计图、功能结构图、数据库 E-R 图与核心流程图。
> 配套：[API.md](API.md)（接口文档）· [STARTUP.md](STARTUP.md)（启动指南）· [ARCHITECTURE.md](ARCHITECTURE.md)（架构文字版）

---

## 一、系统架构图

```mermaid
flowchart TB
    subgraph client["客户端（PC 浏览器 1280px+）"]
        UI["Vue 3 + TypeScript + Element Plus<br/>views / components / stores(Pinia) / api(axios)<br/>游客模式 · 支付沙箱 · iMessage 风聊天"]
    end

    subgraph dev["本地开发态"]
        VITE["Vite Dev Server :5173<br/>代理 /api(剥前缀) /uploads /ws"]
    end

    subgraph backend["Spring Boot 3.5 :8080"]
        direction TB
        SEC["Security 层<br/>JWT 过滤器 · SecurityUtils<br/>公开: /auth /items(GET) /categories<br/>登录: 其余全部"]
        subgraph ctl["Controller 层（11 个）"]
            C1["Auth"] --- C2["User"] --- C3["Item"] --- C4["Order"]
            C5["Comment"] --- C6["Chat"] --- C7["Favorite"]
            C8["Report"] --- C9["Upload"] --- C10["Category"] --- C11["AI"]
        end
        subgraph svc["Service 层"]
            S1["Item / Order(事务+CAS 防超卖)"]
            S2["Comment(未读已读) / Chat"]
            S3["PublishAudit(两道合规闸)"]
            S4["AiEstimate(两阶段估价)"]
            S5["BrowseHistory(内存分页)"]
        end
        subgraph ai["AI 模块（LangChain4j）"]
            A1["ItemEstimationAgent<br/>估价+新品价查询"]
            A2["工具集 AiTools<br/>soldPriceStats(含同款样本)"]
            A3["PublishAuditAgent<br/>四选一合规判定"]
            A4["降级: RuleBasedEstimator"]
        end
        MP["MyBatis-Plus 3.5<br/>逻辑删除 · 分页插件"]
        WS["WebSocket 私信推送"]
        JOB["定时 Job<br/>待付款 30min 超时关单"]
    end

    DB[("MySQL 8.x<br/>12 张表 utf8mb4")]
    LLM["DeepSeek API<br/>deepseek-chat"]
    FS[("backend/uploads<br/>seed 真实主题图 / item 用户上传")]

    UI -->|HTTPS| VITE -->|/api 剥前缀| SEC
    SEC --> ctl --> svc
    svc --> MP --> DB
    svc -.->|ObjectProvider 可选装配| ai
    ai -->|HTTP| LLM
    ai --> MP
    MP --> FS
    WS -->|ws://| UI
    JOB --> DB
```

**架构要点**

| 主题 | 决策 |
| --- | --- |
| 响应体 | 统一 `R{code,msg,data,timestamp}`；业务失败 HTTP 仍 200，凭 `code` 判断 |
| 精度 | 全部 `Long` 序列化为字符串（防 JS 精度丢失），前端以字符串比较 |
| 事务 | 三个事务边界：`pay`（抢占商品+关同品其它待付款单）/ `cancel`（回滚在售）/ `confirm`（完成+托管结算） |
| 防超卖 | 条件更新影响行数 CAS（`WHERE status='ON_SALE'`），不用 `FOR UPDATE` |
| AI 装配 | `ObjectProvider` 可选注入，无 Key 时自动降级规则引擎，系统不崩 |
| 精度外的资金 | 预付款托管：`pay` 入托管，`confirm` 同一事务写 `settle_time` 释放 |

---

## 二、功能结构图

```mermaid
flowchart LR
    ROOT["物归校园"]

    ROOT --> AUTH["认证与账号"]
    ROOT --> ITEM["商品"]
    ROOT --> TRADE["交易"]
    ROOT --> SOCIAL["互动"]
    ROOT --> USER_C["个人中心"]
    ROOT --> AI_F["AI 能力"]

    AUTH --> A1["注册 / 登录(JWT)"]
    AUTH --> A2["微信扫码登录沙箱"]
    AUTH --> A3["游客模式(仅浏览)"]
    AUTH --> A4["资料 / 头像 / 改密"]

    ITEM --> B1["首页: 图标分类选择"]
    ITEM --> B2["二级胶囊 / 校区 / 价格 / 排序"]
    ITEM --> B3["搜索(导航栏,URL 同步)"]
    ITEM --> B4["商品详情 + 同类推荐"]
    ITEM --> B5["发布(级联分类+品牌标签+图片)"]
    ITEM --> B6["编辑 / 下架 / 删除"]

    TRADE --> C1["下单弹窗<br/>面交:时间+地点 / 邮寄:收货三件套"]
    TRADE --> C2["收银台: 微信/支付宝支付沙箱"]
    TRADE --> C3["预付款托管 → 结算给卖家"]
    TRADE --> C4["订单流转<br/>待付款/待发货/待收货/待面交/完成"]
    TRADE --> C5["取消 / 超时自动关单"]

    SOCIAL --> D1["商品留言(一级+单层回复)"]
    SOCIAL --> D2["留言未读红点提醒"]
    SOCIAL --> D3["收藏 / 取消"]
    SOCIAL --> D4["浏览历史"]
    SOCIAL --> D5["私信(iMessage 风+WS 实时)"]
    SOCIAL --> D6["举报"]

    USER_C --> E1["资料编辑 / 改密 / 头像"]
    USER_C --> E2["我的商品 / 订单(买/卖)"]
    USER_C --> E3["收藏夹 / 浏览历史"]
    USER_C --> E4["数据可视化分析"]

    AI_F --> F1["智能估价<br/>两阶段: 新品价锚点+成交库同款核对"]
    AI_F --> F2["一键文案<br/>分类驱动三段式"]
    AI_F --> F3["发布 AI 审核(四选一判定)"]
```

---

## 三、数据库 E-R 图

12 张表：`t_user`（用户）、`t_category`（分类，自关联两级）、`t_item`（商品）、
`t_item_image`（图集）、`t_browse_history`（浏览历史）、`t_favorite`（收藏）、
`t_item_comment`（商品留言，单层回复）、`t_order`（订单，含托管结算）、
`t_review`（评价，一单一条）、`t_report`（举报）、`t_chat_message`（私信）、
`t_ai_estimate_log`（AI 调用日志/额度）。

```mermaid
erDiagram
    t_user ||--o{ t_item : "seller_id 发布"
    t_user ||--o{ t_order : "buyer_id 购买"
    t_user ||--o{ t_order : "seller_id 出售"
    t_user ||--o{ t_item_comment : "user_id 留言"
    t_user ||--o{ t_favorite : "user_id 收藏"
    t_user ||--o{ t_browse_history : "user_id 浏览"
    t_user ||--o{ t_chat_message : "sender/receiver"
    t_user ||--o{ t_review : "buyer_id 评价"
    t_user ||--o{ t_report : "user_id 举报"
    t_user ||--o{ t_ai_estimate_log : "user_id 调用"

    t_category ||--o{ t_item : "category_id 归类"
    t_category ||--o{ t_category : "parent_id 两级自关联"

    t_item ||--o{ t_item_image : "item_id 图集"
    t_item ||--o{ t_item_comment : "item_id 被留言"
    t_item ||--o{ t_browse_history : "item_id 被浏览"
    t_item ||--o{ t_favorite : "item_id 被收藏"
    t_item ||--o{ t_report : "item_id 被举报"
    t_item ||--o{ t_order : "item_id 成交(快照)"

    t_item_comment ||--o{ t_item_comment : "parent_id 单层回复"
    t_order ||--o| t_review : "order_id 一单一评价"
    t_chat_message }o--|| t_user : "from_id/to_id"

    t_user {
        bigint id PK
        varchar username UK "登录名"
        varchar password "BCrypt 密文"
        varchar nickname "昵称"
        varchar avatar "头像URL"
        varchar campus "校区"
        varchar role "USER/REVIEWER/OPERATOR"
        varchar status "NORMAL/DISABLED"
        int credit_score "信用分0-200"
        tinyint deleted "逻辑删除"
    }

    t_category {
        bigint id PK
        varchar name "分类名"
        bigint parent_id "0=一级"
        varchar icon "EP图标名"
        int sort "排序"
        tinyint deleted "逻辑删除"
    }

    t_item {
        bigint id PK
        bigint seller_id FK "卖家"
        bigint category_id FK "分类"
        varchar title "标题≤40"
        varchar description "描述≤2000"
        decimal price "现价"
        decimal original_price "原价"
        varchar condition_level "成色四级"
        varchar trade_type "MEET/SHIP/BOTH"
        varchar campus "校区"
        varchar brand_model "品牌型号"
        varchar status "PENDING/ON_SALE/OFF_SHELF/SOLD/REJECTED"
        varchar cover_image "首图URL"
        int view_count "浏览量"
        int favorite_count "收藏数"
        tinyint deleted "逻辑删除"
    }

    t_item_image {
        bigint id PK
        bigint item_id FK "商品"
        varchar url "图片URL"
        int sort "0=主图"
    }

    t_item_comment {
        bigint id PK
        bigint item_id FK "商品"
        bigint user_id FK "留言人"
        bigint parent_id "空=一级留言"
        varchar content "≤500字"
        tinyint is_read "卖家已读0/1"
        tinyint deleted "逻辑删除"
    }

    t_order {
        bigint id PK
        varchar order_no UK "业务单号"
        bigint item_id FK "商品"
        bigint buyer_id FK "买家"
        bigint seller_id FK "卖家(冗余)"
        decimal amount "成交金额"
        varchar item_title "快照-标题"
        varchar item_cover "快照-首图"
        decimal item_price "快照-单价"
        varchar trade_type "MEET/SHIP"
        varchar status "UNPAID/TO_SHIP/TO_RECEIVE/TO_MEET/DONE/CANCELED"
        varchar pay_method "WECHAT/ALIPAY/BANK"
        datetime pay_time "支付时间"
        datetime ship_time "发货时间"
        datetime finish_time "完成时间"
        datetime settle_time "托管结算时间"
        datetime meet_time "面交时间"
        varchar meet_place "面交地点"
        varchar receiver_name "收货人(邮寄)"
        varchar receiver_phone "收货手机"
        varchar receiver_address "收货地址"
        varchar close_reason "关单原因"
        tinyint deleted "逻辑删除"
    }

    t_review {
        bigint id PK
        bigint order_id FK "一单一条UK"
        bigint item_id FK
        bigint buyer_id FK
        bigint seller_id FK
        tinyint rating "1-5星"
        varchar content "≤500字"
        tinyint deleted "逻辑删除"
    }

    t_favorite {
        bigint id PK
        bigint user_id FK "收藏人"
        bigint item_id FK "商品"
    }

    t_browse_history {
        bigint id PK
        bigint user_id FK "浏览人"
        bigint item_id FK "商品"
        datetime browsed_at "最近浏览(去重)"
    }

    t_report {
        bigint id PK
        bigint item_id FK "被举报商品"
        bigint user_id FK "举报人"
        varchar reason_type "举报类型"
        varchar description "描述"
        varchar status "PENDING/HANDLED"
    }

    t_chat_message {
        bigint id PK
        bigint from_id FK "发送者"
        bigint to_id FK "接收者"
        varchar content "≤500字"
        tinyint is_read "接收者已读0/1"
        tinyint deleted "逻辑删除"
    }

    t_ai_estimate_log {
        bigint id PK
        bigint user_id FK "调用者"
        varchar scene "ESTIMATE/COPYWRITE/AUDIT"
        varchar source "LLM/RULE"
        varchar request_digest "入参摘要"
        tinyint success "成功0/1"
        int cost_ms "耗时"
    }
```

**设计要点**

- 商品与订单解耦：订单冗余 `item_title / item_cover / item_price` 快照，商品改价/删除不影响历史订单展示
- 分类两级自关联（`parent_id=0` 为一级），前端图标卡 → 胶囊两级联动直接消费
- 留言 `parent_id` 只允许一层回复；`is_read` 驱动导航「留言」红点
- `t_browse_history` 按人按商品去重、保留最近浏览时间（内存分页，单用户上限 50 条）
- 软删除统一 `deleted` 字段（MyBatis-Plus `@TableLogic`）

---

## 四、核心流程

### 4.1 订单状态机

```mermaid
stateDiagram-v2
    [*] --> UNPAID: 我想要(选面交/邮寄+信息)
    UNPAID --> TO_MEET: 支付沙箱付款(面交)
    UNPAID --> TO_SHIP: 支付沙箱付款(邮寄)
    UNPAID --> CANCELED: 买家取消 / 30min 超时关单
    TO_SHIP --> TO_RECEIVE: 卖家发货
    TO_RECEIVE --> DONE: 买家确认收货<br/>settle_time=NOW(托管释放)
    TO_MEET --> DONE: 买家确认完成交易<br/>settle_time=NOW(托管释放)
    TO_SHIP --> CANCELED: 双方协商取消
    TO_MEET --> CANCELED: 双方协商取消
    DONE --> [*]
    CANCELED --> [*]
    note right of DONE: 支付即托管<br/>确认收货同一事务写 settle_time<br/>=「已结算给卖家」
```

### 4.2 两阶段 AI 估价时序

```mermaid
sequenceDiagram
    participant U as 用户(发布页)
    participant S as AiEstimateService
    participant L1 as LLM 阶段一(新品价)
    participant T as AiTools 工具集
    participant DB as MySQL
    participant L2 as LLM 阶段二(综合定价)

    U->>S: brandModel / condition / (原价可选)
    S->>S: 额度校验(每日 10 次)
    alt 用户未填原价
        S->>L1: 「该商品全新参考价区间?」
        L1-->>S: {low, high, basis}(知识库估计)
        S->>S: 新品价中值 × 折价系数 = 第二锚点
    end
    S->>L2: 【定价参考数据包】+ 工具列表
    L2->>T: soldPriceStats(具体款式关键词)
    T->>DB: LIKE 查 90 天成交 + 样本标题
    T-->>L2: 笔数/中位数/区间/样本标题
    L2->>L2: 同款判定：样本与商品非同款→忽略成交数据
    L2->>T: onSaleLowestPrice / conditionDiscountFactor
    T-->>L2: 在售最低价 / 折价系数
    L2-->>S: JSON{suggestPrice, priceRange, confidence, reasons, riskTips}
    S->>S: Sanitizer 硬钳制(二手价≤原价×0.98)
    S-->>U: 结果 + 剩余额度(失败自动降级规则引擎)
```

---

## 五、表清单速查

| 表 | 用途 | 关键约束 |
| --- | --- | --- |
| `t_user` | 用户 | `username` 唯一；BCrypt 密文 |
| `t_category` | 两级分类 | `parent_id=0` 一级；图标名须为 EP 真实图标 |
| `t_item` | 商品 | 状态机 `PENDING→ON_SALE→SOLD`；快照外冗余 |
| `t_item_image` | 商品图集 | `sort=0` 主图 |
| `t_item_comment` | 商品留言 | 回复仅一层；`is_read` 驱动红点 |
| `t_order` | 订单 | `order_no` 唯一；快照字段 |
| `t_review` | 评价 | `order_id` 唯一（一单一评） |
| `t_favorite` / `t_browse_history` | 收藏 / 足迹 | 人+商品去重 |
| `t_report` | 举报 | 审核后台处理 |
| `t_chat_message` | 私信 | 双向已读标记 |
| `t_ai_estimate_log` | AI 日志 | 每日额度统计来源 |
