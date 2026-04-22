# FalconX v1 REST 接口规范

## 1. 目标

统一 FalconX v1 所有 HTTP API 的路径、请求、响应、错误和幂等规则。

适用服务：

- `falconx-gateway`
- `falconx-identity-service`
- `falconx-market-service`
- `falconx-trading-core-service`
- `falconx-wallet-service`

## 2. 路径规范

外部接口统一使用：

- `/api/v1/**`

示例：

- `/api/v1/auth/register`
- `/api/v1/auth/login`
- `/api/v1/auth/logout`
- `/api/v1/market/symbols`
- `/api/v1/market/quotes/{symbol}`
- `/api/v1/trading/orders`
- `/api/v1/trading/positions`
- `/api/v1/wallet/addresses`

内部接口规则：

- 一期默认不对业务协作开放常规 `/internal/**` 接口
- 若后续确实需要内部 HTTP 调用，再按服务 owner 单独增加

## 3. 方法规范

- `GET`：查询
- `POST`：创建或触发业务动作
- `PUT`：整体更新
- `PATCH`：局部更新

一期尽量少用：

- `DELETE`

说明：

- 金融业务大多使用状态变更而不是物理删除

## 4. 请求头规范

推荐统一支持：

- `Authorization: Bearer <token>`
- `X-Request-Id`
- `X-Idempotency-Key`

规则：

- `X-Request-Id`：单次请求标识
- `X-Idempotency-Key`：写接口幂等键

补充规则：

- 前端和外部调用方不支持主动传入 `traceId`
- 一期默认忽略外部请求中的 `X-Trace-Id`
- `traceId` 由 `gateway` 在请求进入系统时统一生成
- 生成后的 `traceId` 只在系统内部透传，并通过响应头回传给调用方

推荐响应头：

- `X-Trace-Id`

## 4.1 TraceId 规则

`traceId` 是一次系统内部业务链路的全局追踪号。

固定规则：

- 格式统一为 `32` 位小写十六进制
- 由 `gateway` 统一生成
- 所有下游服务必须透传
- 所有响应都应回传 `X-Trace-Id`
- 所有 Kafka 事件都应携带 `traceId`

禁止事项：

- 不允许前端指定 `traceId`
- 不允许沿用客户端传入的固定 `traceId`
- 不允许把 `traceId` 当作业务幂等键

## 5. 请求体规范

规则：

- 请求 DTO 使用明确字段名，不使用单字符缩写
- 金额、价格、数量统一使用字符串传输
- 时间统一使用 ISO-8601 字符串或明确的 UTC 时间戳
- 交易下单接口若支持持仓级 TP/SL，字段名固定为：
  - `takeProfitPrice`
  - `stopLossPrice`

原因：

- 避免前端 JSON 浮点精度问题

## 6. 响应体规范

统一响应格式：

```json
{
  "code": "0",
  "message": "success",
  "data": {},
  "timestamp": "2026-04-16T16:00:00Z",
  "traceId": "9fbcf7c6f0d1467b"
}
```

字段规则：

- `code`：业务码，成功固定为 `0`
- `message`：简要说明
- `data`：业务数据
- `timestamp`：响应时间
- `traceId`：链路标识

## 7. 错误码规范

建议分段：

- `1xxxx`：身份与认证
- `2xxxx`：钱包与入金
- `3xxxx`：市场数据
- `4xxxx`：交易
- `5xxxx`：风险
- `9xxxx`：系统错误

HTTP 状态码与业务码并存：

- HTTP 用于表达协议层结果
- `code` 用于表达业务层结果

### 7.1 一期保留错误码清单

#### `1xxxx` 身份与认证

- `10001`：Unauthorized
- `10002`：User Banned
- `10003`：Login Rate Limited
- `10004`：Register Rate Limited
- `10005`：Invalid Credentials
- `10006`：Refresh Token Invalid
- `10007`：User Frozen
- `10008`：User Already Exists
- `10009`：Email Format Invalid
- `10010`：Password Too Weak
- `10011`：User Not Activated
- `10012`：Trading Rate Limited
- `10013`：Global IP Rate Limited

#### `2xxxx` 钱包与入金

- `20001`：Wallet Address Not Found
- `20002`：Deposit Transaction Duplicated
- `20003`：Deposit Still Confirming
- `20004`：Unsupported Chain
- `20005`：Deposit Reversed
- `20006`：Wallet Address Allocation Failed

#### `3xxxx` 市场数据

- `30001`：Symbol Not Found
- `30002`：Price Source Stale Or Disconnected
- `30003`：Quote Not Available
- `30004`：Kline Not Ready
- `30005`：Market Data Write Failed

#### `4xxxx` 交易

- `40001`：Insufficient Margin
- `40002`：Order Rejected
- `40003`：Order Not Found
- `40004`：Position Not Found
- `40005`：Duplicate Client Order Id
- `40006`：Invalid Order State
- `40007`：Position Already Closed
- `40008`：Symbol Trading Suspended
- `40010`：Margin Mode Not Supported

#### `5xxxx` 风险

- `50001`：Leverage Exceeded
- `50002`：Position Limit Exceeded
- `50003`：Liquidation Triggered
- `50004`：Risk Config Missing
- `50005`：Risk Engine Busy

#### `9xxxx` 系统

- `90001`：Internal Error
- `90002`：Dependency Timeout
- `90003`：Event Publish Failed
- `90004`：Invalid Request Payload

规则：

- 一期新增错误码必须先补到本清单
- WebSocket 错误码沿用同一套业务码，不再单独发明编号

## 8. 分页规范

分页查询统一使用参数：

- `page`
- `pageSize`
- `sortBy`
- `sortOrder`

默认约束：

- `page` 默认 `1`
- `pageSize` 默认 `20`
- `pageSize` 上限 `100`

分页响应建议包含：

- `items`
- `page`
- `pageSize`
- `total`

## 9. 幂等规范

下面的外部写接口必须支持幂等：

- 注册
- 下单
- 平仓
- 地址申请

规则：

- 优先使用 `X-Idempotency-Key`
- 交易接口可结合 `clientOrderId`
- 幂等冲突时返回相同结果或明确错误

## 9.1 交易查询补充约定

`GET /api/v1/trading/accounts/me` 的查询语义固定如下：

- 账户余额字段读取 `t_account`
- 当前 OPEN 持仓可来自内存快照
- `unrealizedPnl` 必须基于当前 `markPrice` 动态计算
- 禁止把 `unrealizedPnl` 作为高频字段持久化到 MySQL

`GET /api/v1/trading/orders` 的查询语义固定如下：

- 只返回当前登录用户自己的订单
- 数据 owner 固定读取 `t_order`
- 首版只开放 `page / pageSize` 分页参数，不开放筛选条件和排序字段
- 返回结果按 `created_at DESC, id DESC` 排序
- 订单级费用通过 `fee` 字段返回；首版不单独新增 `/fees` 接口

`GET /api/v1/trading/trades` 的查询语义固定如下：

- 只返回当前登录用户自己的成交
- 数据 owner 固定读取 `t_trade`
- 首版只开放 `page / pageSize` 分页参数，不开放筛选条件和排序字段
- 返回结果按 `traded_at DESC, id DESC` 排序
- 成交级费用通过 `fee` 字段返回；成交类型固定为 `OPEN / CLOSE / LIQUIDATION`

`GET /api/v1/trading/positions` 的查询语义固定如下：

- 只返回当前登录用户自己的持仓历史
- 数据 owner 固定读取 `t_position`
- `accounts/me` 继续只负责账户快照与当前 `OPEN` 持仓；完整历史列表统一走 `/positions`
- 首版只开放 `page / pageSize` 分页参数，不开放筛选条件和排序字段
- 返回结果按 `updated_at DESC, id DESC` 排序
- 对于 `OPEN` 持仓，允许在查询时基于 Redis 最新报价动态补充 `markPrice / unrealizedPnl / quoteStale / quoteTs / quoteSource`
- 对于 `CLOSED / LIQUIDATED` 终态持仓，不得伪造新的 `unrealizedPnl`

`GET /api/v1/trading/ledger` 的查询语义固定如下：

- 只返回当前登录用户自己的账本流水
- 数据 owner 固定读取 `t_ledger`
- 首版只开放 `page / pageSize` 分页参数，不开放筛选条件和排序字段
- 返回结果按 `created_at DESC, id DESC` 排序
- 首版费用查询通过 `ORDER_FEE_CHARGED / SWAP_* / LIQUIDATION_PNL / REALIZED_PNL` 等账本流水体现，不单独新增 `/fees`

`GET /api/v1/trading/liquidations` 的查询语义固定如下：

- 只返回当前登录用户自己的强平记录
- 数据 owner 固定读取 `t_liquidation_log`
- 首版只开放 `page / pageSize` 分页参数，不开放筛选条件和排序字段
- 返回结果按 `created_at DESC, id DESC` 排序
- 返回字段至少覆盖强平价、触发价、真实亏损、手续费、释放保证金和平台兜底金额

`POST /api/v1/trading/orders/market` 的请求语义固定如下：

- `takeProfitPrice / stopLossPrice` 为可选字段
- `marginMode` 为可选字段；未传时应用层默认按 `ISOLATED` 执行
- 一期当前只接受 `marginMode=ISOLATED`
- 若客户端显式传入 `marginMode=CROSS`，返回 `40010: Margin Mode Not Supported`
- 下单前必须执行交易时间校验
- 交易时间校验只允许依赖 `market-service` 写入 Redis 的交易时间快照
- 若当前时刻不在可交易时段内，返回 `40008: Symbol Trading Suspended`
- `40008` 的触发场景至少包括：非交易时段、节假日全休、人工例外停盘

### 交易域冻结规则说明（SPEC-TRD-001）

- 正式产品规则已冻结为“单用户单 `symbol` 单净持仓 + `One-Way + Isolated`”
- 净持仓模型下保留“每用户每 `symbol` 一个稳定 `positionId`”
- 同向下单视为加仓，反向下单视为减仓；若反向数量超过当前净仓，则先减到 `0`，剩余部分翻为反向净仓
- `POST /api/v1/trading/orders/market` 的后续正式实现必须以净持仓模型为准，不得再默认“一次开仓生成一条独立 `OPEN` 持仓”
- 有效最大杠杆固定为 `min(t_symbol.max_leverage, t_risk_config.max_leverage)`
- 手续费正式口径为双边收费；一期先以 `t_symbol.taker_fee_rate` 作为统一动态费率
- 在正式折算链路落地前，只允许 `quote_currency ∈ {USD, USDT, USDC}` 的品种进入交易放行
- 产品配置 owner 固定为 `market-service`；`trading-core-service` 后续必须消费 Kafka 下发的正式产品配置结果，不得继续以本地默认值或本地白名单作为正式产品口径
- 本节是规则冻结说明，不代表当前仓库已完成实现切换

`POST /api/v1/trading/positions/{positionId}/close` 的请求语义固定如下：

- 该接口用于手动平掉当前用户自己的 `OPEN` 持仓
- 本轮不引入请求体字段；平仓价固定读取 Redis 最新 `markPrice`
- 非交易时段、节假日全休或人工例外停盘**不**阻塞手动平仓
- 若 Redis 无可用最新价，返回 `30003: Quote Not Available`
- 若最新价已 stale，返回 `30002: Price Source Stale Or Disconnected`
- 若持仓不存在或不属于当前用户，返回 `40004: Position Not Found`
- 若持仓已处于 `CLOSED / LIQUIDATED` 终态，返回 `40007: Position Already Closed`
- 平仓成功后必须在同一本地事务内完成账户结算、`t_ledger.biz_type=8`、持仓终态写入、`t_trade.trade_type=2`、`t_risk_exposure` 回补以及 `t_outbox.event_type=trading.position.closed` 写入
- 成功响应中的 `account.openPositions` 必须回显当前用户剩余的 `OPEN` 持仓视图，不能固定返回空数组
- 手动平仓不创建新的 `t_order` 记录
- 事务提交后必须移除 `OpenPositionSnapshotStore` 中的对应 OPEN 持仓快照

`POST /api/v1/trading/positions/{positionId}/margin` 的请求语义固定如下：

- 该接口用于为当前用户自己的 `OPEN` 持仓追加逐仓保证金
- 请求体固定为 `{ "amount": decimal }`
- 仅允许追加正数金额；请求体验证失败统一返回 HTTP `400 + 90004`
- 一期所有持仓当前都按 `ISOLATED` 运行，因此本接口不再额外引入 `marginMode` 入参
- 若可用余额不足，返回 `40001: Insufficient Margin`
- 若持仓不存在或不属于当前用户，返回 `40004: Position Not Found`
- 若持仓已处于 `CLOSED / LIQUIDATED` 终态，返回 `40007: Position Already Closed`
- 成功后账户语义固定为：
  - `balance` 不变
  - `frozen` 不变
  - `margin_used += amount`
- 成功后持仓保持 `OPEN`，但必须重算并持久化最新 `liquidationPrice`
- 成功后必须写入 `t_ledger.biz_type=10(isolated_margin_supplement)`，并保留完整账务快照
- 事务提交后必须执行 `OpenPositionSnapshotStore.upsert(position)`，保证报价驱动风控读取到最新 `margin / liquidationPrice`
- 本阶段不新增 Kafka topic / payload，不写 Outbox 业务事件

`PATCH /api/v1/trading/positions/{positionId}` 的契约语义固定如下：

- 该接口用于修改已有 `OPEN` 持仓的 `takeProfitPrice / stopLossPrice`
- 请求体允许只传一个字段；未传字段保持原值
- 若两者都显式传 `null`，表示清空 TP/SL
- 仅允许持仓 owner 修改自己的 `OPEN` 持仓
- 本阶段先冻结契约；真正实现与测试留到 `Stage 7`

## 10. 契约变更规范

- 对外接口变更必须先更新文档
- 破坏性变更必须升级版本或新增字段兼容
- 新增字段优先兼容式增加，不直接删除旧字段

## 11. 统一接口文档输出要求

每次 REST 接口开发并测试通过后，必须同步更新：

- [FalconX统一接口文档](./FalconX统一接口文档.md)

要求：

- 由接口实现者负责更新
- 文档内容必须与实际测试结果一致
- 未更新该文档，不视为接口任务完成
