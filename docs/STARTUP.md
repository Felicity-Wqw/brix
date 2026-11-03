# 启动指南（STARTUP）

> 从拉取代码到本地跑通「物归校园」全平台的完整步骤。本文面向**首次拉取本项目**的同学，
> Windows 为主（提供一键 .bat），macOS / Linux 用户看每步末尾的等价命令即可。
>
> 配套文档：[ARCHITECTURE.md](ARCHITECTURE.md)（架构）· [PRD.md](PRD.md)（需求）

---

## 一、环境要求

| 依赖 | 版本要求 | 说明 |
| --- | --- | --- |
| JDK | **17+**（21/24 也可） | 后端编译目标 `--release 17` |
| Maven | **3.9+** | IDEA 内置 Maven 也可以 |
| Node.js | **≥ 20.19** | Vite 7 的硬性要求，`node -v` 自查 |
| MySQL | **8.0+ / 9.x** | 默认端口 3306，需知道 root 密码 |
| （可选）DeepSeek API Key | — | 不填也能跑，AI 功能降级，见下文「AI 能力说明」 |

> 检查命令：`java -version`、`mvn -v`、`node -v`、`mysql --version`

---

## 二、拉取代码

```bash
git clone <仓库地址> brix-campus-market
cd brix-campus-market
```

目录速览：

```
brix-campus-market/
├── backend/              # Spring Boot 3.5 后端（端口 8080）
├── frontend/             # Vue3 + Vite 前端（端口 5173）
├── db/                   # schema.sql / data.sql / seed-transactions.sql / init-db.bat
├── docs/                 # 本文档 + ARCHITECTURE.md + PRD.md
├── start-backend.bat     # Windows 一键启动后端
└── start-frontend.bat    # Windows 一键启动前端
```

---

## 三、初始化数据库（首次必做）

### 方式 A：一键脚本（Windows 推荐）

双击 `db\init-db.bat`，按提示输入 **MySQL root 密码**，脚本自动完成 5 步：

1. 建库 `brix_campus`（utf8mb4）
2. 创建应用账号 `brix / brix123` 并授权
3. 导入表结构 `schema.sql`（可重复执行，会 DROP 重建）
4. 导入种子数据 `data.sql`（演示商品、订单、留言、收藏等）
5. 打印各表行数校验 + 演示账号清单

### 方式 B：手动执行 SQL（跨平台）

```bash
mysql -u root -p --default-character-set=utf8mb4 \
  -e "CREATE DATABASE IF NOT EXISTS brix_campus DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; \
      CREATE USER IF NOT EXISTS 'brix'@'localhost' IDENTIFIED BY 'brix123'; \
      GRANT ALL PRIVILEGES ON brix_campus.* TO 'brix'@'localhost'; FLUSH PRIVILEGES;"

mysql -u root -p --default-character-set=utf8mb4 brix_campus < db/schema.sql
mysql -u root -p --default-character-set=utf8mb4 brix_campus < db/data.sql
```

### 方式 C（可选但推荐）：导入 90 天成交记录

`seed-transactions.sql` 包含近 90 天的模拟成交订单，是 **AI 估价「成交参考价」的数据源**。
不导入时 AI 估价仍可用（按原价折价 + LLM 新品价锚点），但会提示「无同款成交记录」。

```bash
mysql -u root -p --default-character-set=utf8mb4 brix_campus < db/seed-transactions.sql
```

> ⚠️ 执行顺序必须是 schema → data → seed-transactions。

---

## 四、启动后端（端口 8080）

### 方式 A：一键脚本（Windows 推荐）

```bat
双击 start-backend.bat
```

脚本行为：自动加载根目录 `.env.bat`（如存在）→ 定位 Maven（找不到 mvn 时自动搜索
`%USERPROFILE%\.m2\wrapper\dists`）→ `mvn spring-boot:run`。
看到 Spring 启动日志结束、无报错即成功。

### 方式 B：命令行 / IDEA

```bash
cd backend
mvn spring-boot:run
```

IDEA 直接运行启动类 `BrixCampusApplication` 时，**`.env.bat` 不会生效**，
需在 Run Configuration → Environment variables 里手动填同样的值（见下表），
或者干脆不填——所有配置项都有默认值。

### 环境变量说明（全部有默认值，可不配置）

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `DB_HOST` | localhost | 数据库地址 |
| `DB_PORT` | 3306 | 数据库端口 |
| `DB_NAME` | brix_campus | 库名 |
| `DB_USERNAME` | brix | 应用账号（init-db.bat 已创建） |
| `DB_PASSWORD` | brix123 | 应用密码 |
| `SERVER_PORT` | 8080 | 后端端口（Vite 代理固定指向 8080，勿改） |
| `AI_API_KEY` | 空 | DeepSeek API Key，**可选** |
| `AI_BASE_URL` | https://api.deepseek.com/v1 | LLM 网关 |
| `AI_MODEL` | deepseek-chat | 模型名 |

> **本地凭据文件 `.env.bat`**（已被 gitignore，拉取后不存在）：在项目根目录新建，
> 内容格式如下。不创建也能启动（走 yml 默认值），只在默认值与你的环境不符时才需要。
>
> ```bat
> rem 数据库
> set "DB_USERNAME=brix"
> set "DB_PASSWORD=brix123"
> set "DB_HOST=localhost"
> set "DB_PORT=3306"
> set "DB_NAME=brix_campus"
>
> rem DeepSeek API Key（可选）
> set "AI_API_KEY=sk-xxxxxxxx"
> ```

### AI 能力说明

- **不配 Key**：系统正常启动，AI 估价走本地规则引擎（原价×折价系数）、AI 文案与发布
  AI 审核闸自动降级（审核仅剩违禁词表），前端对应按钮仍可点击并返回结果。
- **配了 Key**（DeepSeek）：估价升级为两阶段智能定价（LLM 新品价锚点 + 成交库同款核对）、
  文案生成、AI 审核员全部可用。

---

## 五、启动前端（端口 5173）

### 方式 A：一键脚本（Windows 推荐）

```bat
双击 start-frontend.bat
```

首次运行自动 `npm install`（约 1-2 分钟），之后直接 `npm run dev`。

### 方式 B：命令行

```bash
cd frontend
npm install        # 首次必做
npm run dev
```

启动成功后终端显示 `Local: http://localhost:5173/`。

> Vite 已配置好代理，无需任何额外配置：
> `/api` → 8080（剥离前缀）、`/uploads` → 8080（商品图片）、`/ws` → 8080（私信 WebSocket）。

---

## 六、访问平台

浏览器打开 **http://localhost:5173**

- 首次进入会先看到**登录页**：可以登录，也可以点「先逛逛，游客访问」进入游客模式
  （仅可浏览首页与商品详情）
- **微信扫码登录沙箱**：登录页点微信图标 → 弹出模拟扫码界面 → 点「模拟已扫描」→
  「确认登录」，将以演示账号 student01 登录（纯前端模拟，无需微信）

### 演示账号（密码均为 `123456`）

| 账号 | 角色 | 说明 |
| --- | --- | --- |
| `student01` | 普通用户 | **主演示账号**，买家视角，名下也有历史订单 |
| `seller01` ~ `seller05` | 普通用户 | 卖家视角（seller01 名下商品最多） |
| `reviewer01` | 审核员 | 内容审核后台 |
| `operator01` | 运营 | 数据看板后台 |

- 管理后台入口：http://localhost:5173/#/admin/login
- 推荐演示动线：student01 登录 → 首页筛选/搜索 → 商品详情（留言、收藏）→
  「我想要」下单（面交/邮寄表单）→ 收银台微信/支付宝**支付沙箱** →
  「我的订单」确认收货 → 订单完成显示「已结算给卖家」→「数据分析」看可视化

---

## 七、常见问题（FAQ）

| 现象 | 原因与处理 |
| --- | --- |
| 后端启动报 `Access denied for user 'brix'` | root 密码与 init-db.bat 设置不一致，或没跑 init-db.bat。用 root 登录后执行 `ALTER USER 'brix'@'localhost' IDENTIFIED BY 'brix123';` |
| 后端启动报数据库不存在 | 没执行 init-db.bat / schema.sql，见第三节 |
| 前端 `npm install` 卡住 | 换国内镜像：`npm config set registry https://registry.npmmirror.com` 后重装 |
| 前端页面空白 / 接口 404 | 后端没启动，或 8080 被其他程序占用（`netstat -ano | findstr :8080`）；后端端口被占用时必须释放 8080 而不是改前端代理 |
| `Port 5173 is in use` | Vite 配置了 `strictPort`，需先结束占用 5173 的旧进程 |
| AI 估价返回「本地规则」字样 | `AI_API_KEY` 未配置或 Key 失效，属预期降级，不影响其他功能 |
| AI 估价显示「未找到同款成交记录」 | 没导入 `seed-transactions.sql`（第三节方式 C），或成交库中确无同款商品——此时按原价折价估算，属正常 |
| 私信无 WebSocket 连接 | 确认通过 http://localhost:5173 访问（代理 `/ws` 才生效），不要直接访问 8080 |
| IDEA 里启动后连不上库 | `.env.bat` 只对 `start-backend.bat` 生效；IDEA 请在 Run Configuration 的 Environment variables 里填 `DB_USERNAME` / `DB_PASSWORD` 等 |
| MySQL 9.x 驱动兼容 | 已使用 `com.mysql.cj.jdbc.Driver` 且连接串带 `allowPublicKeyRetrieval=true`，无需额外处理 |

---

## 八、构建产物（可选）

```bash
# 前端生产构建 → frontend/dist/
cd frontend && npm run build

# 后端打包 → backend/target/*.jar
cd backend && mvn -q package -DskipTests
```

> 本项目当前定位为本地开发态演示（前端 Vite 双进程模式），生产部署（jar + Nginx
> 静态托管 / 反向代理）需要额外配置，详见 ARCHITECTURE.md。

---

## 九、再次启动（非首次）

数据库已初始化过，直接两步：

1. 双击 `start-backend.bat`（或 IDEA 运行主类）
2. 双击 `start-frontend.bat`（或 `cd frontend && npm run dev`）

等待两个终端都就绪后访问 http://localhost:5173 即可。
