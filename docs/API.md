# 接口文档（API）

> 基础地址：后端 `http://localhost:8080`；前端统一经 Vite 代理访问，请求路径加 `/api` 前缀
> （如前端 `GET /api/items` → 后端 `GET /items`）。
>
> 统一响应体：
>
> ```json
> { "code": 200, "msg": "成功", "data": { ... }, "timestamp": 1730620800000 }
> ```
>
> - **业务失败 HTTP 仍为 200**，以 `code` 区分：`200` 成功、`400` 参数错误、`401` 未认证、
>   `403` 无权限、`404` 资源不存在、`500` 服务器异常（`RESULT_CODE` 常量）。
> - 除公开接口外均需携带请求头 `Authorization: Bearer <token>`（登录/注册返回）。
> - **所有 `Long` 型字段（id 等）序列化为字符串**，前端不得用 `===` 与数字比较。
> - 分页入参统一 `currentPage`（默认 1）+ `pageSize`（默认 10 / 首页 12）；出参为
>   MyBatis-Plus `IPage`：`{ records: [...], total, size, current, pages }`。
> - `data: null` 时字段不出现（Jackson `NON_NULL`）。

---

## 1. 认证 Auth（/auth）

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| POST | `/auth/register` | 公开 | 注册并直接返回 token。入参 `{username, password, nickname, school?, campus?, phone?}` → `LoginVO{token, user}` |
| POST | `/auth/login` | 公开 | 登录。入参 `{username, password}` → `LoginVO{token, user{...role}}`。密码错误报 `400` |
| GET | `/auth/me` | 登录 | 当前登录用户信息（刷新页面恢复会话用）→ `UserVO` |

> 微信扫码登录为**前端沙箱**（模拟扫码后以 student01 走真实 `/auth/login`），后端无对应端点。

## 2. 用户 User（/users）

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| PUT | `/users/me` | 登录 | 更新资料。入参 `{nickname?, school?, campus?, phone?, bio?}` → `UserVO` |
| PUT | `/users/me/password` | 登录 | 修改密码。入参 `{oldPassword, newPassword}` |
| POST | `/users/me/avatar` | 登录 | 上传头像。`multipart/form-data: file` → `{url}` |
| GET | `/users/me/analysis` | 登录 | 个人买卖数据可视化 → `UserAnalysisVO`（买/卖单数、消费/收入、分类分布等） |
| GET | `/users/me/browse-history` | 登录 | 浏览历史（按人按商品去重，最近优先，上限 50 条）→ `BrowseHistoryVO[]`（商品已删除的条目自动跳过） |
| GET | `/users/{id}/reviews` | 公开 | 某用户的卖家评价列表 → `IPage<ReviewVO>` |

## 3. 分类 Category（/categories）

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/categories` | 公开 | 全部分类（一级 + 二级，含 `icon` 供图标卡渲染）→ `CategoryVO[{id, name, parentId, icon, sort}]` |

## 4. 商品 Item（/items）

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/items` | 公开(游客可见) | 分页列表。入参（均可选）：`keyword`、`categoryId`、`campus`、`minPrice`、`maxPrice`、`sort`(latest/price_asc/price_desc/hot)、`currentPage`、`pageSize` → `IPage<ItemVO>` |
| GET | `/items/{id}` | 公开(游客可见) | 详情 → `ItemDetailVO`（含 images、seller 信息、canEdit 等标记）。浏览历史在此埋点 |
| GET | `/items/{id}/similar` | 公开 | 同类好物（同二级分类优先，不足补同大类）→ `ItemVO[]` |
| POST | `/items` | 登录 | 发布（过两道合规闸后上架/转待审）。入参 `ItemSaveDTO{title, description, categoryId, price, originalPrice?, conditionLevel, campus, tradeType, brandModel?, contact?, images[≤9], useAiCopy?}` → `ItemVO` |
| PUT | `/items/{id}` | 登录(卖家) | 编辑（仅卖家）。入参同 `ItemSaveDTO` |
| DELETE | `/items/{id}` | 登录(卖家) | 逻辑删除 |
| PATCH | `/items/{id}/status` | 登录(卖家) | 上架/下架切换。入参 `{status: "ON_SALE"\|"OFF_SHELF"}` → `ItemVO` |
| GET | `/items/mine` | 登录 | 我的商品（含各状态）。入参同列表 + `status?` |
| POST | `/items/{id}/reports` | 登录 | 举报商品。入参 `{reasonType, description}` → `ReportVO` |
| GET | `/items/{id}/comments` | 公开 | 留言分页（一级倒序 + 单层回复升序）→ `IPage<CommentVO>`（含 `canDelete`）。**卖家查看自己商品时顺带将他人未读留言置已读** |
| POST | `/items/{id}/comments` | 登录 | 发留言 / 回复。入参 `{content(≤500), parentId?}` → `CommentVO`。他人留言触发卖家红点 |
| POST | `/items/{id}/favorite` | 登录 | 收藏/取消收藏（幂等切换）→ `{favorited: bool, count}` |
| POST | `/items/{id}/reports` | 登录 | （同上举报） |

## 5. 留言未读 Comment（/comments）

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/comments/unread-summary` | 登录 | 我收到的未读留言摘要（导航红点）→ `{count, latestItemId}`（Long 为字符串）。已读时机 = 卖家打开对应商品留言列表 |
| DELETE | `/comments/{id}` | 登录(作者) | 删除自己的留言（一级留言连带回复逻辑删除） |

## 6. 订单 Order（/orders）

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| POST | `/orders` | 登录(买家) | 下单。入参 `OrderCreateDTO{itemId, tradeType?, meetTime?, meetPlace?, receiverName?, receiverPhone?, receiverAddress?, buyerNote?}`。**交叉校验**：面交单 `meetTime`(未来时间)+`meetPlace` 必填；邮寄单 `receiverName/Phone/Address` 必填 → `OrderVO`（status=UNPAID） |
| POST | `/orders/{id}/pay` | 登录(买家) | 支付（预付款托管入账；前端微信/支付宝沙箱确认后回调）。入参 `{payMethod: "WECHAT"\|"ALIPAY"}` → `OrderVO`（TO_MEET/TO_SHIP，`escrowDesc="预付款托管中"`）。同事务：抢占商品（CAS 防超卖）+ 关闭同商品其它待付款单 |
| POST | `/orders/{id}/ship` | 登录(卖家) | 发货（TO_SHIP→TO_RECEIVE），写 `ship_time` |
| POST | `/orders/{id}/confirm` | 登录(买家) | 确认收货/完成交易（TO_RECEIVE 或 TO_MEET → DONE），**同一事务写 `settle_time`**（托管释放）→ `escrowDesc="已结算给卖家"` |
| POST | `/orders/{id}/cancel` | 登录(买卖双方) | 取消（见下方约束）。入参 `{reason?}`；待付款仅买家可取消，其余进行中状态双方可取消，商品回滚为在售 |
| GET | `/orders/{id}` | 登录(双方) | 订单详情 → `OrderDetailVO`（含托管状态、面交信息、收货信息、`payRemainSeconds`） |
| GET | `/orders/bought` | 登录 | 我买到的（分页，可按 `status` 过滤） |
| GET | `/orders/sold` | 登录 | 我卖出的（分页，可按 `status` 过滤） |
| POST | `/orders/{id}/review` | 登录(买家) | 评价卖家（仅 DONE 且未评）。入参 `{rating(1-5), content}` → `ReviewVO` |

**订单状态机**：`UNPAID → (pay) → TO_MEET | TO_SHIP → (ship) → TO_RECEIVE → (confirm) → DONE`；
`UNPAID` 30 分钟未付自动关单（定时任务）；`pay` 时同商品其它 UNPAID 单自动关闭（SYSTEM）。

## 7. 私信 Chat（/chat）+ WebSocket

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/chat/conversations` | 登录 | 会话列表（对端信息 + 最后一条 + 未读数）→ `ConversationVO[]` |
| GET | `/chat/messages` | 登录 | 与某人消息分页。入参 `peerId, currentPage, pageSize` → `IPage<ChatMessageVO>` |
| POST | `/chat/messages` | 登录 | 发送私信（同时经 WebSocket 实时推送给在线对端）。入参 `{peerId, content(≤500)}` → `ChatMessageVO` |
| GET | `/chat/unread-count` | 登录 | 全局未读私信数 → `{count}` |
| PUT | `/chat/read?peerId=` | 登录 | 标记与某人的会话已读 → `{count}`（剩余全局未读） |

**WebSocket**：连接 `ws://localhost:8080/ws`（前端经 Vite 代理），`Authorization` token 放连接参数。
服务端推送帧：`{type, fromUserId, toUserId, messageId, content, timestamp}`；
登录后必须发送 `INIT` 帧绑定用户与连接。

## 8. 收藏 Favorite（/favorites）

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| POST | `/items/{id}/favorite` | 登录 | 切换收藏（见第 4 节） |
| GET | `/favorites` | 登录 | 我的收藏分页 → `IPage<FavoriteVO>`（商品已删除的条目跳过） |

## 9. AI 能力（/ai）

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| POST | `/ai/estimate` | 登录 | 智能估价（每日额度 10 次）。入参 `AiEstimateDTO{categoryId, brandModel, conditionLevel, purchaseMonths?, originalPrice?}` → `EstimateResultVO{suggestPrice, priceRange{min,max}, confidence, reasons[], riskTips[], source: "LLM"\|"RULE", remainingQuota}`。**两阶段**：无原价时先 LLM 知识库估新品价 → 与成交库数据（含同款样本核对、原价钳制 ≤原价×0.98）综合定价。LLM 故障自动降级规则引擎 |
| POST | `/ai/copywriting` | 登录 | 一键文案（分类词条驱动）。入参 `AiCopyDTO{categoryId, brandModel, conditionLevel, purchaseMonths?, originalPrice?}` → `CopyResultVO{title(≤24字), description(120~300字三段式), tags[3~5], source, remainingQuota}`。已过合规词表 |

> AI 调用日志与每日额度记于 `t_ai_estimate_log`（`scene`：ESTIMATE/COPYWRITE/AUDIT）。

## 10. 上传 Upload（/upload）

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| POST | `/upload/image` | 登录 | 图片上传。`multipart/form-data: file, biz` → `{url}`（存 `backend/uploads/{biz}/`，经 `/uploads/**` 静态访问）。限图片类型与 5MB |

---

## 错误处理约定

| 场景 | 表现 |
| --- | --- |
| 参数校验失败（@Valid） | HTTP 200 + `{code:400, msg:"具体原因"}` |
| 未登录访问需登录接口 | HTTP 200 + `{code:401}`（前端 axios 拦截器统一跳登录页并带上 redirect） |
| token 过期（401 HTTP） | 同上，提示「登录已过期」 |
| 无权限（他人资源） | `{code:403, msg:"无权操作"}` |
| 业务失败 | `{code:400/404..., msg}`——前端以 `code===200` 判断成功 |
| AI 能力故障 | 不报错：估价降级规则引擎（source=RULE）、审核降级放行、文案降级模板生成 |

## 附：前端调用对照

前端所有请求经 axios 实例（`baseURL: '/api'`）+ Vite 代理，实际发出如
`GET http://localhost:5173/api/items?pageSize=12` → 后端收到 `GET /items?pageSize=12`。
商品图片经 `/uploads/**` 直接静态访问后端 `backend/uploads/` 目录，不经 `/api` 剥前缀。
