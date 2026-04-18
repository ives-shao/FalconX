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
- 失败业务码：`10008`、`90004`
- 响应说明：
  - 成功时返回 `userId`、`uid`、`email`、`status`
  - 当前新用户状态固定为 `PENDING_DEPOSIT`
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
- 测试日期：`2026-04-17`
- 测试环境：本地 `SpringBootTest + MockMvc`
- 测试结果：通过
- 备注：已验证重复邮箱注册返回 `10008`

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
- 失败业务码：`10002`、`10005`、`10007`、`10011`、`90004`
- 响应说明：
  - 成功时返回 Access Token、Refresh Token、两个过期秒数和当前用户状态
  - `PENDING_DEPOSIT` 用户登录会返回 `10011`
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
- 测试日期：`2026-04-17`
- 测试环境：本地 `SpringBootTest + MockMvc`
- 测试结果：通过
- 备注：已验证 `PENDING_DEPOSIT` 登录返回 `10011`，旧 Refresh Token 第二次使用返回 `10006`
- 备注：登录成功后已验证可继续走 Refresh Token 轮换链路

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
- 失败业务码：`10001`
- 响应说明：
  - 返回当前用户账户的 `balance / frozen / marginUsed / available`
  - `openPositions` 返回当前 `OPEN` 持仓视图
  - `unrealizedPnl` 不持久化，查询时基于 Redis 最新 `markPrice` 动态计算
  - 若账户不存在，服务会按默认结算币种自动初始化一个空账户

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
        "markPrice": 9995.00000000,
        "unrealizedPnl": -5.00000000,
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
  - 风控拒绝时返回业务码 `40002`，同时保留拒单原因和订单骨架
  - 非交易时段或节假日休市时返回业务码 `40008`，拒单原因固定为 `SYMBOL_TRADING_SUSPENDED`
  - 写操作场景下，若用户状态为 `FROZEN`，gateway 会直接返回 `10007`

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
    "requestPrice": 9995.00000000,
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
    "requestPrice": 1995.00000000,
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
- 是否要求写审计日志：否
- 是否要求透传 `traceId`：是，由 gateway 生成并透传

#### 3.6.5 测试结论

- 开发人员：Codex
- 测试日期：`2026-04-17`
- 测试环境：本地 `SpringBootTest + MockMvc`
- 测试结果：通过
- 备注：已验证成功成交、持仓级 TP/SL 字段落库回显、`MARKET_QUOTE_STALE` 拒单，以及节假日休市返回 `40008 / SYMBOL_TRADING_SUSPENDED`

### 3.7 trading-core-service - 修改持仓 TP/SL（预留接口，未实现）

#### 3.7.1 接口基础信息

- 所属服务：`falconx-gateway -> falconx-trading-core-service`
- 接口名称：修改持仓 TP/SL
- 接口说明：修改指定 `OPEN` 持仓的止盈/止损触发价；当前仅冻结接口契约，尚未进入实现与测试
- 接口类型：`REST`
- 请求路径或主题：`/api/v1/trading/positions/{positionId}`
- 请求方法：`PATCH`
- 认证要求：需要 `Bearer Access Token`
- 幂等要求：同一持仓相同请求体重复提交应返回相同结果
- 当前实现状态：`未实现`

#### 3.7.2 请求信息

- 请求头：
  - `Authorization: Bearer <accessToken>`
  - `Content-Type: application/json`
- Path 参数：
  - `positionId`：持仓 ID
- Query 参数：无
- 请求体：
  - `takeProfitPrice`：可选，持仓级止盈触发价；显式传 `null` 表示清空
  - `stopLossPrice`：可选，持仓级止损触发价；显式传 `null` 表示清空

请求示例：

```json
{
  "takeProfitPrice": 10250.0,
  "stopLossPrice": 9850.0
}
```

#### 3.7.3 响应信息

- 成功业务码：`0`
- 失败业务码：`40003`、`40005`、`40008`
- 响应说明：
  - 成功时返回更新后的持仓快照
  - 若持仓不存在、非本人持仓或持仓状态非 `OPEN`，返回对应业务错误
  - 非交易时段下的修改限制若后续启用，应返回 `40008`

预期成功响应示例：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "positionId": 1,
    "symbol": "BTCUSDT",
    "status": "OPEN",
    "takeProfitPrice": 10250.00000000,
    "stopLossPrice": 9850.00000000
  },
  "timestamp": "2026-04-17T16:40:00.000+08:00",
  "traceId": "4c9d90a8c5f64a5890e90e9cccb6cb3d"
}
```

#### 3.7.4 测试结论

- 开发人员：未开始
- 测试日期：未开始
- 测试环境：未开始
- 测试结果：未测试
- 备注：本条目用于冻结 TP/SL 修改接口契约，待 `Stage 7` 实现后再转为正式接口
