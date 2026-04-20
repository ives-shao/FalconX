# FalconX 统一接口文档

## 1. 目的

本文件是 FalconX v1 全部已开发接口的统一交付文档。

规则：

- 每次接口开发并测试通过后，必须更新本文件
- 未更新本文件，不视为接口任务完成
- 本文件只记录“已开发且已验证”的接口

适用范围：

- REST 接口
- WebSocket 接口
- 后续如启用的内部接口

## 2. 统一模板

后续新增接口时，必须按下面模板追加。

### 2.1 接口基础信息

- 所属服务：
- 接口名称：
- 接口说明：
- 接口类型：`REST / WebSocket / Internal`
- 请求路径或主题：
- 请求方法：
- 认证要求：
- 幂等要求：

### 2.2 请求信息

- 请求头：
- Path 参数：
- Query 参数：
- 请求体：

请求示例：

```json
{}
```

### 2.3 响应信息

- 成功业务码：
- 失败业务码：
- 响应说明：

成功响应示例：

```json
{}
```

失败响应示例：

```json
{}
```

### 2.4 日志与链路要求

- 关键日志点：
- 是否要求写审计日志：
- 是否要求透传 `traceId`：

### 2.5 测试结论

- 开发人员：
- 测试日期：
- 测试环境：
- 测试结果：
- 备注：

---

## 3. 当前接口清单

说明：

- 自 Stage 4 起，外部 `REST` 接口统一经 `falconx-gateway` 进入
- 文档中的“所属服务”统一按 `gateway -> owner-service` 记录北向调用关系
- 网关生成新的 `X-Trace-Id` 并向下游服务透传，前端不允许自定义传入
- 所有 `/api/v1/**` 请求都受 gateway 全局 IP 每分钟 200 次兜底限流约束，超限返回 HTTP `429` + `10013 / Global IP Rate Limited`
- 所有 `/api/v1/trading/**` 请求在鉴权通过后都受 gateway 每用户每秒 10 次限流约束，超限返回 HTTP `429` + `10012 / Trading Rate Limited`
- 当前正式执行阶段口径固定为 `Stage 6A 收口专项`。若 `main` 上存在超前接口或代码事实，不等于对应阶段已验收完成。

### 3.1 identity-service - 用户注册

#### 3.1.1 接口基础信息

- 所属服务：`falconx-gateway -> falconx-identity-service`
- 接口名称：用户注册
- 接口说明：创建最小身份用户，并返回对外 `UID` 与当前用户状态
- 接口类型：`REST`
- 请求路径或主题：`/api/v1/auth/register`
- 请求方法：`POST`
- 认证要求：无需认证
- 幂等要求：无；重复邮箱注册会返回业务失败码

#### 3.1.2 请求信息

- 请求头：
  - `Content-Type: application/json`
- Path 参数：无
- Query 参数：无
- 请求体：
  - `email`：注册邮箱
  - `password`：明文密码

请求示例：

```json
{
  "email": "alice@example.com",
  "password": "Passw0rd!"
}
```

#### 3.1.3 响应信息

- 成功业务码：`0`
- 失败业务码：`10004`、`10008`、`90004`
- 响应说明：
  - 成功时返回 `userId`、`uid`、`email`、`status`
  - 当前新用户状态固定为 `PENDING_DEPOSIT`
  - 同一 IP 在 1 小时内第 6 次注册会返回 `10004`
  - 响应头必须包含服务端自动生成的 `X-Trace-Id`

成功响应示例：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "userId": 1,
    "uid": "U00000001",
    "email": "alice@example.com",
    "status": "PENDING_DEPOSIT"
  },
  "timestamp": "2026-04-17T10:12:06.055+08:00",
  "traceId": "4f3c2a7e9b6d41f8a1c0e5b2d7f9a3c1"
}
```

失败响应示例：

```json
{
  "code": "10008",
  "message": "User Already Exists",
  "data": null,
  "timestamp": "2026-04-17T10:12:06.406+08:00",
  "traceId": "a8b6c0a31f7d44a9b1dce03f7c2c2b51"
}
```

#### 3.1.4 日志与链路要求

- 关键日志点：
  - `identity.http.register.received`
  - `identity.register.request`
  - `identity.register.completed`
- 是否要求写审计日志：否
- 是否要求透传 `traceId`：是，由服务入口自动生成并写入响应头与日志

#### 3.1.5 测试结论

- 开发人员：Codex
- 测试日期：`2026-04-19`
- 测试环境：本地 `SpringBootTest + MockMvc`
- 测试结果：通过
- 备注：已验证重复邮箱注册返回 `10008`
- 备注：已验证同一 IP 连续 6 次注册时，第 6 次返回 `10004`

### 3.2 identity-service - 用户登录

#### 3.2.1 接口基础信息

- 所属服务：`falconx-gateway -> falconx-identity-service`
- 接口名称：用户登录
- 接口说明：基于邮箱和密码完成认证，并返回 Access Token 与 Refresh Token
- 接口类型：`REST`
- 请求路径或主题：`/api/v1/auth/login`
- 请求方法：`POST`
- 认证要求：无需认证
- 幂等要求：无

#### 3.2.2 请求信息

- 请求头：
  - `Content-Type: application/json`
- Path 参数：无
- Query 参数：无
- 请求体：
  - `email`：登录邮箱
  - `password`：明文密码

请求示例：

```json
{
  "email": "alice@example.com",
  "password": "Passw0rd!"
}
```

#### 3.2.3 响应信息

- 成功业务码：`0`
- 失败业务码：`10002`、`10003`、`10005`、`10007`、`10011`、`90004`
- 响应说明：
  - 成功时返回 Access Token、Refresh Token、两个过期秒数和当前用户状态
  - `PENDING_DEPOSIT` 用户登录会返回 `10011`
  - 同一 IP 连续 5 次密码错误后会进入 15 分钟锁定，第 6 次返回 `10003`
  - 响应头必须包含服务端自动生成的 `X-Trace-Id`

成功响应示例：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "accessToken": "eyJraWQiOiJmYWxjb254LWlkZW50aXR5LXJzYSIsImFsZyI6IlJTMjU2In0...",
    "refreshToken": "c16b8be8-ec0c-4f07-8306-a18efcad89d8",
    "accessTokenExpiresIn": 900,
    "refreshTokenExpiresIn": 259200,
    "userStatus": "ACTIVE"
  },
  "timestamp": "2026-04-17T10:12:06.187+08:00",
  "traceId": "c1f35755d4b94e3c9df4cfc1774b58d0"
}
```

失败响应示例：

```json
{
  "code": "10011",
  "message": "User Not Activated",
  "data": null,
  "timestamp": "2026-04-17T10:12:06.200+08:00",
  "traceId": "bc73bf3b6c4e4d44b265c9f3a7da0e90"
}
```

#### 3.2.4 日志与链路要求

- 关键日志点：
  - `identity.http.login.received`
  - `identity.login.request`
  - `identity.login.completed`
- 是否要求写审计日志：否
- 是否要求透传 `traceId`：是，由服务入口自动生成并写入响应头与日志

#### 3.2.5 测试结论

- 开发人员：Codex
- 测试日期：`2026-04-19`
- 测试环境：本地 `SpringBootTest + MockMvc`
- 测试结果：通过
- 备注：已验证 `PENDING_DEPOSIT` 登录返回 `10011`，旧 Refresh Token 第二次使用返回 `10006`
- 备注：登录成功后已验证可继续走 Refresh Token 轮换链路
- 备注：已验证同一 IP 连续 5 次密码错误后，第 6 次返回 `10003`

### 3.3 identity-service - Refresh Token 刷新

#### 3.3.1 接口基础信息

- 所属服务：`falconx-gateway -> falconx-identity-service`
- 接口名称：刷新认证令牌
- 接口说明：消费旧 Refresh Token 并返回一组新的认证令牌
- 接口类型：`REST`
- 请求路径或主题：`/api/v1/auth/refresh`
- 请求方法：`POST`
- 认证要求：无需认证
- 幂等要求：无；旧 Refresh Token 一次性使用，重复调用会失败

#### 3.3.2 请求信息

- 请求头：
  - `Content-Type: application/json`
- Path 参数：无
- Query 参数：无
- 请求体：
  - `refreshToken`：上一次登录或刷新得到的 Refresh Token

请求示例：

```json
{
  "refreshToken": "c16b8be8-ec0c-4f07-8306-a18efcad89d8"
}
```

#### 3.3.3 响应信息

- 成功业务码：`0`
- 失败业务码：`10006`、`90004`
- 响应说明：
  - 成功时返回新的 Access Token 与 Refresh Token
  - 旧 Refresh Token 被消费后再次调用会返回 `10006`
  - 响应头必须包含服务端自动生成的 `X-Trace-Id`

成功响应示例：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "accessToken": "eyJraWQiOiJmYWxjb254LWlkZW50aXR5LXJzYSIsImFsZyI6IlJTMjU2In0...",
    "refreshToken": "f8983e13-8445-42be-b7b5-2422f2570b19",
    "accessTokenExpiresIn": 900,
    "refreshTokenExpiresIn": 259200,
    "userStatus": "ACTIVE"
  },
  "timestamp": "2026-04-17T10:12:06.298+08:00",
  "traceId": "5e5442da5b7a495cb0ae426ae5f9f204"
}
```

失败响应示例：

```json
{
  "code": "10006",
  "message": "Refresh Token Invalid",
  "data": null,
  "timestamp": "2026-04-17T10:12:06.309+08:00",
  "traceId": "0b8f5367d8854f37a53787869d311f84"
}
```

#### 3.3.4 日志与链路要求

- 关键日志点：
  - `identity.http.refresh.received`
  - `identity.refresh.request`
  - `identity.refresh.completed`
- 是否要求写审计日志：否
- 是否要求透传 `traceId`：是，由服务入口自动生成并写入响应头与日志

#### 3.3.5 测试结论

- 开发人员：Codex
- 测试日期：`2026-04-17`
- 测试环境：本地 `SpringBootTest + MockMvc`
- 测试结果：通过
- 备注：已验证旧 Refresh Token 二次使用返回 `10006`

### 3.4 market-service - 查询最新报价

#### 3.4.1 接口基础信息

- 所属服务：`falconx-gateway -> falconx-market-service`
- 接口名称：查询最新报价
- 接口说明：查询指定交易品种当前最新标准报价快照
- 接口类型：`REST`
- 请求路径或主题：`/api/v1/market/quotes/{symbol}`
- 请求方法：`GET`
- 认证要求：需要 `Bearer Access Token`
- 幂等要求：天然幂等

#### 3.4.2 请求信息

- 请求头：
  - `Authorization: Bearer <accessToken>`
- Path 参数：
  - `symbol`：品种代码，例如 `EURUSD`
- Query 参数：无
- 请求体：无

请求示例：

```http
GET /api/v1/market/quotes/EURUSD
Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
```

#### 3.4.3 响应信息

- 成功业务码：`0`
- 失败业务码：`10001`、`30003`
- 响应说明：
  - 成功时返回品种最新 `bid / ask / mid / mark`
  - `mark` 当前仍是 `market-service` 的兼容报价字段；`trading-core-service` 做逐仓估值、TP/SL、强平和账户浮盈亏时，不直接使用该单值字段，而是按方向从 `bid / ask` 解析有效标记价
  - `30003` 表示当前品种无可用报价
  - 响应头必须包含 gateway 生成的 `X-Trace-Id`

成功响应示例：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "symbol": "EURUSD",
    "bid": 1.08000000,
    "ask": 1.08010000,
    "mid": 1.08005000,
    "mark": 1.08005000,
    "ts": "2026-04-17T11:23:23.275519+08:00",
    "source": "TIINGO_FOREX",
    "stale": false
  },
  "timestamp": "2026-04-17T11:23:25.726+08:00",
  "traceId": "d2a50968a90542ec95cb7f3075b7b9d6"
}
```

失败响应示例：

```json
{
  "code": "30003",
  "message": "Quote Not Available",
  "data": null,
  "timestamp": "2026-04-17T11:23:25.949+08:00",
  "traceId": "2b8bb063f89b4cb5a741ec0caefad1f8"
}
```

#### 3.4.4 日志与链路要求

- 关键日志点：
  - `gateway.request.received`
  - `gateway.auth.accepted`
  - `market.http.quote.received`
- 是否要求写审计日志：否
- 是否要求透传 `traceId`：是，由 gateway 生成并向 market-service 透传

#### 3.4.5 测试结论

- 开发人员：Codex
- 测试日期：`2026-04-17`
- 测试环境：本地 `SpringBootTest + MockMvc`、`SpringBootTest + WebTestClient`
- 测试结果：通过
- 备注：已验证存在报价与无报价两条分支；网关受保护路由已验证鉴权拦截

### 3.5 trading-core-service - 查询当前交易账户

#### 3.5.1 接口基础信息

- 所属服务：`falconx-gateway -> falconx-trading-core-service`
- 接口名称：查询当前交易账户
- 接口说明：查询当前登录用户在默认结算币种下的交易账户快照
- 接口类型：`REST`
- 请求路径或主题：`/api/v1/trading/accounts/me`
- 请求方法：`GET`
- 认证要求：需要 `Bearer Access Token`
- 幂等要求：天然幂等

#### 3.5.2 请求信息

- 请求头：
  - `Authorization: Bearer <accessToken>`
- Path 参数：无
- Query 参数：无
- 请求体：无

请求示例：

```http
GET /api/v1/trading/accounts/me
Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
```

#### 3.5.3 响应信息

- 成功业务码：`0`
- 失败业务码：`10001`、`10012`、`10013`
- 响应说明：
  - 返回当前用户账户的 `balance / frozen / marginUsed / available`
  - `openPositions` 返回当前 `OPEN` 持仓视图
  - `markPrice` 不直接回显市场事件里的单值 `mark`，而是按持仓方向解析有效标记价：`BUY -> bid`，`SELL -> ask`
  - `unrealizedPnl` 不持久化，查询时基于 Redis 最新 `bid / ask` 动态计算有效标记价后再实时计算
  - 若账户不存在，服务会按默认结算币种自动初始化一个空账户
  - 若同一用户 1 秒内第 11 次访问 `/api/v1/trading/**`，gateway 返回 HTTP `429` + `10012 / Trading Rate Limited`
  - 若同一 IP 1 分钟内第 201 次访问任意 `/api/v1/**`，gateway 返回 HTTP `429` + `10013 / Global IP Rate Limited`

成功响应示例：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "accountId": 1,
    "userId": 31001,
    "currency": "USDT",
    "balance": 0.00000000,
    "frozen": 0.00000000,
    "marginUsed": 0.00000000,
    "available": 0.00000000,
    "openPositions": [
      {
        "positionId": 10,
        "symbol": "BTCUSDT",
        "side": "BUY",
        "quantity": 1.00000000,
        "entryPrice": 10000.00000000,
        "markPrice": 9990.00000000,
        "unrealizedPnl": -10.00000000,
        "liquidationPrice": 9000.00000000,
        "takeProfitPrice": 10200.00000000,
        "stopLossPrice": 9800.00000000,
        "quoteStale": false,
        "quoteTs": "2026-04-17T11:22:39.000Z",
        "priceSource": "integration-test"
      }
    ]
  },
  "timestamp": "2026-04-17T11:22:40.253+08:00",
  "traceId": "1e5cbb3a4f674c7d8dca5e343b2fe6bb"
}
```

失败响应示例：

```json
{
  "code": "10001",
  "message": "Unauthorized",
  "data": null,
  "timestamp": "2026-04-17T11:27:07.843+08:00",
  "traceId": "fa0f6d966c69453183bbfbd95647f396"
}
```

#### 3.5.4 日志与链路要求

- 关键日志点：
  - `gateway.request.received`
  - `gateway.auth.accepted`
  - `trading.http.account.received`
- 是否要求写审计日志：否
- 是否要求透传 `traceId`：是，由 gateway 生成并透传

#### 3.5.5 测试结论

- 开发人员：Codex
- 测试日期：`2026-04-17`
- 测试环境：本地 `SpringBootTest + MockMvc`、`SpringBootTest + WebTestClient`
- 测试结果：通过
- 备注：已验证网关透传 `X-User-*` 头到 trading-core-service

### 3.6 trading-core-service - 提交市价单

#### 3.6.1 接口基础信息

- 所属服务：`falconx-gateway -> falconx-trading-core-service`
- 接口名称：提交市价单
- 接口说明：提交一笔最小骨架市价单，由 trading-core-service 完成同步风控、保证金和订单落地
- 接口类型：`REST`
- 请求路径或主题：`/api/v1/trading/orders/market`
- 请求方法：`POST`
- 认证要求：需要 `Bearer Access Token`
- 幂等要求：使用 `clientOrderId` 做幂等；重复请求返回同一订单结果并标记 `duplicate=true`

#### 3.6.2 请求信息

- 请求头：
  - `Authorization: Bearer <accessToken>`
  - `Content-Type: application/json`
- Path 参数：无
- Query 参数：无
- 请求体：
  - `symbol`：交易品种
  - `side`：`BUY / SELL`
  - `quantity`：下单数量
  - `leverage`：杠杆倍数
  - `takeProfitPrice`：可选，持仓级止盈触发价
  - `stopLossPrice`：可选，持仓级止损触发价
  - `clientOrderId`：客户端幂等键

请求示例：

```json
{
  "symbol": "BTCUSDT",
  "side": "BUY",
  "quantity": 1.0,
  "leverage": 10,
  "takeProfitPrice": 10100.0,
  "stopLossPrice": 9800.0,
  "clientOrderId": "integration-order-31002"
}
```

#### 3.6.3 响应信息

- 成功业务码：`0`
- 失败业务码：`10001`、`10007`、`40002`、`40008`、`90004`
- 响应说明：
  - 下单成功时返回订单、持仓、成交和账户快照
  - `requestPrice` 记录的是下单时的可成交参考价：`BUY -> ask`，`SELL -> bid`
  - 风控拒绝时返回业务码 `40002`，同时保留拒单原因和订单骨架
  - 非交易时段或节假日休市时返回业务码 `40008`，拒单原因固定为 `SYMBOL_TRADING_SUSPENDED`
  - 写操作场景下，若用户状态为 `FROZEN`，gateway 会直接返回 `10007`
  - 开仓成功后若 `net_exposure_usd` 首次超过 `hedge_threshold_usd`，或方向切换后仍处于超阈值状态，会在事务提交后发布服务内 `TradingHedgeAlertEvent` stub，并同步写入 `t_hedge_log`

成功响应示例：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "orderNo": "O00000001",
    "orderStatus": "FILLED",
    "rejectionReason": null,
    "duplicate": false,
    "symbol": "BTCUSDT",
    "side": "BUY",
    "quantity": 1.00000000,
    "requestPrice": 10000.00000000,
    "filledPrice": 10000.00000000,
    "leverage": 10,
    "margin": 1000.00000000,
    "fee": 5.00000000,
    "positionId": 1,
    "positionStatus": "OPEN",
    "takeProfitPrice": 10100.00000000,
    "stopLossPrice": 9800.00000000,
    "tradeId": 1,
    "account": {
      "accountId": 2,
      "userId": 31002,
      "currency": "USDT",
      "balance": 1995.00000000,
      "frozen": 0.00000000,
      "marginUsed": 1000.00000000,
      "available": 995.00000000,
      "openPositions": []
    }
  },
  "timestamp": "2026-04-17T11:22:40.172+08:00",
  "traceId": "b8c3f6696af34c13be20f2ff65c6b36d"
}
```

失败响应示例：

```json
{
  "code": "40002",
  "message": "Order Rejected",
  "data": {
    "orderNo": "O00000002",
    "orderStatus": "REJECTED",
    "rejectionReason": "MARKET_QUOTE_STALE",
    "duplicate": false,
    "symbol": "ETHUSDT",
    "side": "SELL",
    "quantity": 1.00000000,
    "requestPrice": 1990.00000000,
    "filledPrice": null,
    "leverage": 10,
    "margin": 0.00000000,
    "fee": 0.00000000,
    "positionId": null,
    "positionStatus": null,
    "takeProfitPrice": null,
    "stopLossPrice": null,
    "tradeId": null,
    "account": {
      "accountId": 3,
      "userId": 31003,
      "currency": "USDT",
      "balance": 2000.00000000,
      "frozen": 0.00000000,
      "marginUsed": 0.00000000,
      "available": 2000.00000000,
      "openPositions": []
    }
  },
  "timestamp": "2026-04-17T11:22:40.280+08:00",
  "traceId": "9de49fbb96dc430699ac0d0df589ab20"
}
```

节假日休市失败响应示例：

```json
{
  "code": "40008",
  "message": "Symbol Trading Suspended",
  "data": {
    "orderNo": null,
    "orderStatus": "REJECTED",
    "rejectionReason": "SYMBOL_TRADING_SUSPENDED",
    "duplicate": false,
    "symbol": "BTCUSDT",
    "side": "BUY",
    "quantity": 1.00000000,
    "requestPrice": null,
    "filledPrice": null,
    "leverage": 10,
    "margin": 0.00000000,
    "fee": 0.00000000,
    "positionId": null,
    "positionStatus": null,
    "takeProfitPrice": null,
    "stopLossPrice": null,
    "tradeId": null,
    "account": {
      "accountId": 4,
      "userId": 31004,
      "currency": "USDT",
      "balance": 2000.00000000,
      "frozen": 0.00000000,
      "marginUsed": 0.00000000,
      "available": 2000.00000000,
      "openPositions": []
    }
  },
  "timestamp": "2026-04-17T17:17:37.427+08:00",
  "traceId": "f31c7acbcb5f4b9cb3d05d3f9aef4ee4"
}
```

#### 3.6.4 日志与链路要求

- 关键日志点：
  - `gateway.request.received`
  - `gateway.auth.accepted`
  - `trading.http.order.received`
  - `trading.order.place.request`
  - `trading.order.place.completed / rejected`
  - `trading.risk.hedge.alert / recovered`（仅在 FX-026 阈值状态变化时出现）
- 是否要求写审计日志：否
- 是否要求透传 `traceId`：是，由 gateway 生成并透传

#### 3.6.5 测试结论

- 开发人员：Codex
- 测试日期：`2026-04-17`
- 测试环境：本地 `SpringBootTest + MockMvc`
- 测试结果：通过
- 备注：已验证成功成交、持仓级 TP/SL 字段落库回显、`MARKET_QUOTE_STALE` 拒单，以及节假日休市返回 `40008 / SYMBOL_TRADING_SUSPENDED`

### 3.7 trading-core-service - 手动平仓

#### 3.7.1 接口基础信息

- 所属服务：`falconx-gateway -> falconx-trading-core-service`
- 接口名称：手动平仓
- 接口说明：手动关闭当前用户自己的 `OPEN` 持仓；手动平仓与 TP/SL 自动触发、强平共用正式结算写路径，但本接口只执行手动平仓
- 接口类型：`REST`
- 请求路径或主题：`/api/v1/trading/positions/{positionId}/close`
- 请求方法：`POST`
- 认证要求：需要 `Bearer Access Token`
- 幂等要求：同一终态持仓重复提交返回 `40007`
- 当前实现状态：`已实现`

#### 3.7.2 请求信息

- 请求头：
  - `Authorization: Bearer <accessToken>`
  - `Content-Type: application/json`
- Path 参数：
  - `positionId`：持仓 ID
- Query 参数：无
- 请求体：无

#### 3.7.3 响应信息

- 成功业务码：`0`
- 失败业务码：`10001`、`30002`、`30003`、`40004`、`40007`
- 响应说明：
  - 平仓价按持仓方向解析有效标记价：`BUY -> bid`，`SELL -> ask`
  - 非交易时段或节假日全休不阻塞手动平仓
  - Redis 无报价时返回 `30003`
  - Redis 报价 stale 时返回 `30002`
  - 持仓不存在或不属于当前用户时返回 `40004`
  - 持仓已关闭时返回 `40007`
  - 成功平仓时同事务写入 `t_outbox.event_type=trading.position.closed`
  - 成功响应中的 `account.openPositions` 回显当前用户剩余的 `OPEN` 持仓视图，而不是固定返回空数组
  - 手动平仓不会新增 `t_order`
  - 手动平仓会同步刷新 FX-026 风险观测状态；若因此首次进入超阈值或方向切换后仍超阈值，会在事务提交后发布服务内 `TradingHedgeAlertEvent` stub，并同步写入 `t_hedge_log`

成功响应示例：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "positionId": 39201747692032000,
    "positionStatus": "CLOSED",
    "closePrice": 10150.00000000,
    "closeReason": "MANUAL",
    "realizedPnl": 150.00000000,
    "closedAt": "2026-04-19T12:13:45.000+08:00",
    "tradeId": 39201747700420608,
    "account": {
      "accountId": 39201747620696064,
      "userId": 31005,
      "currency": "USDT",
      "balance": 2145.00000000,
      "frozen": 0.00000000,
      "marginUsed": 0.00000000,
      "available": 2145.00000000,
      "openPositions": []
    }
  },
  "timestamp": "2026-04-19T12:13:45.000+08:00",
  "traceId": "d2a6c8e4f9f5482dbf3a5f9c2fcb2c01"
}
```

#### 3.7.4 关键日志点

- `trading.http.position.close.received`
- `trading.position.exit.completed`
- `trading.risk.hedge.alert / recovered`（仅在 FX-026 阈值状态变化时出现）
- `trading.http.request.failed`

#### 3.7.5 测试结论

- 开发人员：Codex
- 测试日期：`2026-04-19`
- 测试环境：本地 `SpringBootTest + MockMvc + MySQL + Redis`
- 测试结果：通过
- 备注：已验证 `BUY/SELL` 手动平仓成功、节假日全休时新开仓返回 `40008` 但既有持仓仍允许平仓、报价 stale 返回 `30002`、报价缺失返回 `30003`、持仓存在但不属于当前用户时返回 `40004`、重复平仓返回 `40007`、平仓不新增 `t_order` 且会写入 `t_outbox.event_type=trading.position.closed`，以及“同一用户仍有另一笔 OPEN 持仓时，平仓成功响应里的 `account.openPositions` 会回显剩余持仓”

### 3.8 trading-core-service - 修改持仓 TP/SL

#### 3.8.1 接口基础信息

- 所属服务：`falconx-gateway -> falconx-trading-core-service`
- 接口名称：修改持仓 TP/SL
- 接口说明：修改当前用户自己 `OPEN` 持仓的止盈/止损触发价；未传字段保持原值，显式传 `null` 表示清空
- 接口类型：`REST`
- 请求路径或主题：`/api/v1/trading/positions/{positionId}`
- 请求方法：`PATCH`
- 认证要求：需要 `Bearer Access Token`
- 幂等要求：同一持仓相同请求体重复提交应返回相同结果
- 当前实现状态：`已实现`

#### 3.8.2 请求信息

- 请求头：
  - `Authorization: Bearer <accessToken>`
  - `Content-Type: application/json`
- Path 参数：
  - `positionId`：持仓 ID
- Query 参数：无
- 请求体：
  - `takeProfitPrice`：可选，持仓级止盈触发价；显式传 `null` 表示清空
  - `stopLossPrice`：可选，持仓级止损触发价；显式传 `null` 表示清空
  - 约束：
    - 请求体必须是 JSON 对象
    - 至少显式提供 `takeProfitPrice` 或 `stopLossPrice` 之一
    - 非数值、`0`、负数统一视为非法请求体
    - 成功更新只修改 `t_position.take_profit_price / stop_loss_price` 并在事务提交后刷新 `OpenPositionSnapshotStore`

请求示例：

```json
{
  "takeProfitPrice": 10250.0,
  "stopLossPrice": 9850.0
}
```

#### 3.8.3 响应信息

- 成功业务码：`0`
- 失败业务码：`90004`、`40004`、`40007`
- 响应说明：
  - 成功时返回更新后的持仓风险控制快照
  - 若 `takeProfitPrice` 或 `stopLossPrice` 未传，则保持原值
  - 若显式传 `null`，则清空对应字段
  - 若持仓不存在或不属于当前用户，返回 HTTP `200` + `40004 / Position Not Found`
  - 若持仓已进入终态，返回 HTTP `200` + `40007 / Position Already Closed`
  - 非对象 JSON、空请求体、空对象、两个字段都未提供、非数值、`0`、负数，统一返回 HTTP `400` + `90004 / invalid request payload`

成功响应示例：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "positionId": 39201747692032000,
    "symbol": "BTCUSDT",
    "status": "OPEN",
    "takeProfitPrice": 10250.00000000,
    "stopLossPrice": 9850.00000000
  },
  "timestamp": "2026-04-19T17:10:00.000+08:00",
  "traceId": "4c9d90a8c5f64a5890e90e9cccb6cb3d"
}
```

#### 3.8.4 测试结论

- 开发人员：Codex
- 测试日期：`2026-04-19`
- 测试环境：本地 `SpringBootTest + MockMvc + MySQL + Redis`
- 测试结果：通过
- 关键日志点：
  - `trading.http.position.patch.received`
  - `trading.http.request.invalid`
  - `trading.position.risk-controls.updated`
  - `trading.position.risk-controls.noop`
  - `trading.http.request.failed`
- 备注：已验证双字段修改、只改单字段、显式 `null` 清空、非本人持仓返回 `40004`、终态持仓返回 `40007`、非法请求体稳定返回 HTTP `400 + 90004`，以及“PATCH 与自动触发交叉时，正式 owner 写路径会基于最新 DB 持仓重新复核触发条件，不会按旧 TP/SL 误平仓”

#### 3.8.5 代表性 E2E 结论

- 当前已通过代表性 E2E 覆盖 `gateway + identity-service + market-service + trading-core-service + wallet-service + Kafka` 的受控真运行时主链路：
  - `TC-E2E-001`：`注册 -> 入金 -> 激活 -> 登录 -> 开仓`
  - `TC-E2E-010`：`注册 -> 入金 -> 激活 -> 登录 -> 开仓 -> TP 自动平仓 -> 账户视图收敛`
  - `TC-E2E-011`：`注册 -> 入金 -> 激活 -> 登录 -> 开仓 -> 强平 -> 账户视图收敛`
- 已通过事实：
  - gateway 北向注册、登录、下单、账户查询链路可稳定复现
  - `market-service` 真运行时已参与 owner ingestion、Redis 最新价与北向报价查询链路
  - `wallet-service` 真运行时已参与地址分配、原始入金事实与 outbox 投递链路
  - Kafka 事件 `falconx.wallet.deposit.confirmed`、`falconx.market.price.tick` 可驱动 `trading-core-service` 与 `identity-service` 完成 owner 状态推进
  - `TP` 自动平仓后，gateway 账户视图会收敛到 `openPositions=[]`、`marginUsed=0`，且 `balance` 高于开仓后基线
  - `TP` 场景 owner 终态已验证：`t_position(status=2, close_reason=2)`、`t_trade(trade_type=2)`、`t_ledger(biz_type=8)`、`t_outbox(event_type=trading.position.closed)`、`t_risk_exposure.net_exposure=0`
  - 强平后，gateway 账户视图会收敛到 `openPositions=[]`、`marginUsed=0`、`balance>=0`
  - 强平场景 owner 终态已验证：`t_position(status=3, close_reason=4)`、`t_trade(trade_type=3)`、`t_ledger(biz_type=9)`、`t_liquidation_log`、`t_outbox(event_type=trading.liquidation.executed)`、`t_risk_exposure.net_exposure=0`
- 边界说明：
  - 以上 E2E 仍不等于 Tiingo 外部真源与外部链节点真扫块已经进入同一自动化用例
  - 当前文档只证明 `market-service` 已投递 `market.kline.update`；`trading-core-service` 对该事件的正式消费仍属于 `Stage 6A` 当前待收口项，尚未计入“已开发且已验证”结论
  - 当前结论只能证明 `Stage 6A` 当前主链路与部分交易闭环在受控真运行时中可复现，不等于 `Stage 6A` 已整体收口，也不等于 `Stage 7` 已整体验收完成

### 3.9 trading-core-service - B-book 对冲告警桩事件

#### 3.9.1 接口基础信息

- 所属服务：`falconx-trading-core-service`
- 接口名称：B-book 对冲告警桩事件
- 接口说明：当 `net_exposure_usd` 首次超过 `hedge_threshold_usd`，或方向切换后仍保持超阈值状态时，服务在事务提交后发布内部 Spring Event `TradingHedgeAlertEvent`；该事件只作为后续真实告警/对冲出口的 stub，不是北向 REST，也不是 Kafka topic
- 接口类型：`Internal`
- 请求路径或主题：`Spring Event: com.falconx.trading.event.TradingHedgeAlertEvent`
- 请求方法：`publish`
- 认证要求：无，仅限服务内监听
- 幂等要求：不保证 exactly-once；当前只在 `ALERT_ONLY` 分支发布，同方向持续超阈值不会重复发送

#### 3.9.2 请求信息

- 请求头：无
- Path 参数：无
- Query 参数：无
- 请求体：
  - `occurredAt`：触发观测的业务时间
  - `symbol`：交易品种
  - `netExposureUsd`：当前净美元敞口
  - `hedgeThresholdUsd`：当前阈值
  - `positionId`：触发本次变化的持仓 ID；纯行情刷新时允许为空
  - `triggerSource`：`OPEN_POSITION / MANUAL_CLOSE / TAKE_PROFIT / STOP_LOSS / LIQUIDATION / PRICE_TICK`
  - `markPrice`：本次估值使用的有效标记价；净多头使用 `bid`，净空头使用 `ask`
  - `quoteTs`：本次估值使用的行情时间
  - `priceSource`：行情来源
  - `hedgeLogId`：已落库的 `t_hedge_log.id`

请求示例：

```json
{
  "occurredAt": "2026-04-19T18:43:52.487+08:00",
  "symbol": "BTCUSDT",
  "netExposureUsd": 19980.00000000,
  "hedgeThresholdUsd": 15000.00000000,
  "positionId": 39299925120520192,
  "triggerSource": "OPEN_POSITION",
  "markPrice": 9990.00000000,
  "quoteTs": "2026-04-19T18:43:52.487+08:00",
  "priceSource": "risk-observability-unit-test",
  "hedgeLogId": 99
}
```

#### 3.9.3 响应信息

- 成功业务码：无同步响应
- 失败业务码：无同步响应
- 响应说明：
  - 该接口是服务内 Spring Event，无 HTTP / Kafka 同步响应
  - 监听器异常会被发布端捕获并记录错误日志，不回滚已经提交的交易主事务
  - 恢复到阈值内只写 `t_hedge_log(action_status=RECOVERED)` 与 `trading.risk.hedge.recovered` 日志，不发布本事件

成功响应示例：

```json
{}
```

失败响应示例：

```json
{}
```

#### 3.9.4 日志与链路要求

- 关键日志点：
  - `trading.risk.hedge.alert`
  - `trading.risk.hedge.recovered`
  - `trading.risk.hedge.event.publish.failed`
- 是否要求写审计日志：是；必须先写 `t_hedge_log`
- 是否要求透传 `traceId`：是；若来源调用链已有 `traceId`，事件监听器日志沿当前 MDC 透传

#### 3.9.5 测试结论

- 开发人员：Codex
- 测试日期：`2026-04-19`
- 测试环境：本地 `JUnit 5 + Mockito`，并结合 `SpringBootTest + MySQL + Redis`
- 测试结果：通过
- 备注：`SpringTradingHedgeAlertEventPublisherTests` 已验证 `afterCommit` 发布时间点与监听器异常隔离；`DefaultTradingRiskObservabilityServiceTests` 已验证首次超阈值发布事件、恢复时不发布事件；`TradingRiskObservabilityIntegrationTests` 已验证 `t_hedge_log` 与告警 / 恢复日志仍保持原有闭环

### 3.10 identity-service - 吊销当前 Access Token

#### 3.10.1 接口基础信息

- 所属服务：`falconx-gateway -> falconx-identity-service`
- 接口名称：吊销当前 Access Token
- 接口说明：对当前 Bearer Access Token 执行登出，只把当前 Access Token 的 `jti` 写入黑名单；不引入新的 Refresh Token 主动撤销语义
- 接口类型：`REST`
- 请求路径或主题：`/api/v1/auth/logout`
- 请求方法：`POST`
- 认证要求：需要 `Bearer Access Token`
- 幂等要求：对同一仍有效 Access Token 的重复请求应返回相同成功结果；Access Token 一旦进入黑名单，后续受保护请求会被 gateway 拒绝

#### 3.10.2 请求信息

- 请求头：
  - `Authorization: Bearer <accessToken>`
- Path 参数：无
- Query 参数：无
- 请求体：无

请求示例：

```http
POST /api/v1/auth/logout
Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
```

#### 3.10.3 响应信息

- 成功业务码：`0`
- 失败业务码：`10001`、`10013`
- 响应说明：
  - 成功时返回统一 `ApiResponse` 成功体，`data=null`
  - 缺失 `Authorization`、Bearer Token 非法、签名不通过、已过期或类型不正确时，返回 HTTP `401` + `10001 / Unauthorized`
  - 同一 IP 1 分钟内第 201 次访问任意 `/api/v1/**` 时，gateway 会先返回 HTTP `429` + `10013 / Global IP Rate Limited`
  - 成功后当前 Access Token 的 `jti` 会按剩余 TTL 写入 Redis 黑名单；同一 Access Token 再访问任意受保护接口会被 gateway 拒绝并返回 `10001`

成功响应示例：

```json
{
  "code": "0",
  "message": "success",
  "data": null,
  "timestamp": "2026-04-19T20:20:00.000+08:00",
  "traceId": "9df0df7db0d94291b5aa1f084c618638"
}
```

失败响应示例：

```json
{
  "code": "10001",
  "message": "Unauthorized",
  "data": null,
  "timestamp": "2026-04-19T20:20:03.000+08:00",
  "traceId": "08b460f5d3bf4ac1b3d1ab9b92c4c0fe"
}
```

#### 3.10.4 日志与链路要求

- 关键日志点：
  - `gateway.request.received`
  - `gateway.auth.accepted`
  - `identity.http.logout.received`
  - `identity.logout.request`
  - `identity.logout.completed`
- 是否要求写审计日志：否
- 是否要求透传 `traceId`：是，由 gateway 生成并向 identity-service 透传

#### 3.10.5 测试结论

- 开发人员：Codex
- 测试日期：`2026-04-19`
- 测试环境：本地 `SpringBootTest + MockMvc + WebTestClient + Redis`
- 测试结果：通过
- 备注：
  - `AuthControllerIntegrationTests.shouldBlacklistCurrentAccessTokenWhenLogoutSucceeds` 已验证黑名单 key 与剩余 TTL 写入语义
  - `AuthControllerIntegrationTests.shouldRejectLogoutWhenAuthorizationHeaderMissingOrInvalid` 已验证缺失或非法 `Authorization` 返回 `10001`
  - `GatewayRoutingIntegrationTests.shouldRejectSameAccessTokenAfterLogoutViaGateway` 已验证 `logout -> blacklist -> gateway reject` 闭环
