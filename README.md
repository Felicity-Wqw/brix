# 物归校园（Brix Campus Market）

> 「物归原主」的「物归」——每件闲置，都在校园里找到它的新主人。

Spring Boot 3 + Vue3 的校园二手交易平台（本地开发态双进程：后端 8080 + 前端 5173）。

## 环境要求

| 依赖 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 17+ | 编译 `--release 17`，JDK 21 / 24 运行均可 |
| Maven | 3.9+ | 也可直接用 IDEA 内置 Maven |
| Node.js | 18+ | 推荐 20 / 22 |
| MySQL | 8.0+ | 默认端口 3306 |

## 项目结构

```
brix-campus-market/
├── backend/               # SpringBoot 3.5.3 + MyBatis-Plus 3.5.17
│   └── src/main/java/com/brix/campus/
│       ├── common/        # R / ResultCode / BusinessException / 全局异常
│       ├── config/        # MyBatis-Plus / Security / WebSocket / Jackson
│       ├── controller/    # 用户端接口
│       ├── service/       # 业务层（接口 + impl）
│       ├── model/         # entity / query / dto / vo / enums
│       ├── ai/            # LangChain4j：估价 / 文案 / 发布审核 三个智能体 + 规则降级
│       ├── ws/            # 私信 WebSocket
│       └── job/           # 待付款超时关单
├── frontend/              # Vue3 + TypeScript + Vite + Element Plus + ECharts
│   └── src/{api,components,layout,router,stores,styles,utils,views}
├── db/                    # schema.sql / data.sql / seed-transactions.sql / init-db.bat
├── docs/                  # STARTUP.md（启动指南）、DESIGN.md（架构/功能结构/E-R/流程图）、API.md（接口文档）、PRD.md、ARCHITECTURE.md
├── start-backend.bat      # 读取根目录 .env.bat 环境变量后启动后端
└── start-frontend.bat
```

## 功能一览

**用户端**：注册登录、首页两级分类筛选搜索、商品详情（同类好物推荐）、发布商品（级联分类 + 品牌快捷标签 + AI 估价 + 分类驱动文案，**合规两道闸通过后直接上架**）、下单与收银台、订单流转（发货 / 收货 / 面交确认完成）、评价、收藏、浏览历史、私信（WebSocket 实时）、举报、个人中心（含买卖可视化分析）。

**发布合规两道闸**：① 违禁词黑名单（内置词表，命中即拒）；② AI 审核员（四选一判定：通过 / 驳回禁止上架 / 驳回修改后重审 / 上报风控，附法规依据）。AI 故障时**降级放行**——AI 只能拒绝，不能阻断发布；审核调用不消耗用户每日 AI 额度。

**技术要点**：统一响应体 `R{code,msg,data,timestamp}`、业务失败 HTTP 仍 200、分页用 MyBatis-Plus `IPage`、`Long` 序列化为字符串防精度丢失、防超卖用条件更新影响行数、AI 能力全部经 `ObjectProvider` 可选装配（无 Key 自动降级，不崩）。

## 本地启动

> 📖 **完整启动指南见 [docs/STARTUP.md](docs/STARTUP.md)**——含数据库初始化细节、
> 环境变量说明、AI Key 配置、演示账号、常见问题 FAQ。Windows 下三步：`db\init-db.bat`
> → `start-backend.bat` → `start-frontend.bat`，然后访问 http://localhost:5173 。
>
> 📐 **设计文档见 [docs/DESIGN.md](docs/DESIGN.md)**（架构图 / 功能结构图 / 数据库 E-R 图 /
> 订单状态机 / 两阶段估价时序，均为 Mermaid 可直接渲染）、**[docs/API.md](docs/API.md)**
> （全部 40+ 接口文档：路径 / 权限 / 入参 / 出参 / 错误约定）。

## 1. 初始化数据库（双击 `db/init-db.bat`）

输入 MySQL 密码即可，脚本会自动完成：建库 `brix_campus` → 建应用账号 `brix` 并授权 → 导入表结构 → 导入种子数据。行数校验参考：users=2、categories=33（6 一级 + 27 二级）、items=101（36 在架 + 65 成交快照）、orders 含近 90 天成交记录。

## 2. 启动后端（双击 `start-backend.bat`）

脚本先加载根目录 `.env.bat`（可存放 `DB_USERNAME` / `DB_PASSWORD` / `AI_API_KEY` 等覆盖项），再自动寻找本机 Maven 执行 `mvn spring-boot:run`。
看到 `Tomcat started on port 8080` 即成功；首次请求稍慢（建 Druid 连接池）。

- 数据库账号/密码通过环境变量 `DB_USERNAME` / `DB_PASSWORD` 覆盖，缺省 `brix`（源码中不硬编码密码）。
- **AI 能力完全可选**：`AI_API_KEY` 留空时——估价走本地规则引擎、文案与审核闸自动降级（审核仅剩词表闸），系统正常启动，不会报错；填入 DeepSeek Key 则全功能可用。

## 3. 启动前端（双击 `start-frontend.bat`）

首次自动 `npm install`，然后 `npm run dev`。浏览器访问 **http://localhost:5173**（Vite 已代理 `/api`、`/uploads`、`/ws`，无需处理跨域）。

## 测试账号

| 用途 | 用户名 | 密码 |
| --- | --- | --- |
| 买家视角主号「小林同学」（约 58 买 / 10 卖，含浏览/收藏/私信数据） | `student01` | ``464585` |
| 卖家视角主号「阿哲的数码铺」（约 58 卖 / 10 买，在架商品最多） | `seller01` | ``464585` |

> 演示数据预置了待面交订单（买家线下见面后「确认完成交易」）、待付款订单（可体验收银台倒计时）以及 90 天成交记录（个人分析页与 AI 估价的数据底座）。
