# FalconX v1 WebSocket 接口规范

## 1. 目标

统一 FalconX v1 实时行情推送的 WebSocket 协议、消息格式、认证方式和连接管理规则。

适用服务：

- `falconx-gateway`（接入层）
- `falconx-market-service`（推送数据源）

## 2. 连接端点

外部客户端统一通过 gateway 连接：

```
ws://{host}/ws/v1/market
```

gateway 将 WebSocket 连接代理到 `market-service`。

说明：

- 一期 `market-service` 是唯一推送源
- 后续如需推送账户变更、订单状态，再另行定义端点

## 3. 认证方式

WebSocket 握手时通过 Query Parameter 传入 Access Token：

```
ws://{host}/ws/v1/market?token=<accessToken>
```

规则：

- `token` 缺失或无效：握手阶段返回 `HTTP 401`，拒绝升级连接
- `token` 过期或在黑名单：同上处理
- token 验证逻辑与 REST 接口一致（gateway 层统一校验）
- 连接建立后不再重复验证 token（直到连接断开重连）

## 4. 订阅协议

### 4.1 订阅请求

客户端发送 JSON 文本帧：

```json
{
  "type": "subscribe",
  "requestId": "req-001",
  "channels": ["price.tick", "kline.1m"],
  "symbols": ["EURUSD", "BTCUSDT"]
}
```

字段说明：

- `type`：固定为 `subscribe`
- `requestId`：客户端自定义请求 ID，用于关联响应
- `channels`：订阅的数据类型，支持 `price.tick` 和 `kline.{interval}`
- `symbols`：订阅的品种列表，传空数组表示订阅所有可用品种（一期不推荐，按需订阅）

`kline.{interval}` 支持的周期：

- `kline.1m`、`kline.5m`、`kline.15m`、`kline.1h`、`kline.4h`、`kline.1d`

### 4.2 订阅响应

服务端返回确认帧：

```json
{
  "type": "subscribed",
  "requestId": "req-001",
  "channels": ["price.tick", "kline.1m"],
  "symbols": ["EURUSD", "BTCUSDT"]
}
```

若订阅请求有误（如 symbol 不存在）：

```json
{
  "type": "error",
  "requestId": "req-001",
  "code": "30001",
  "message": "symbol not found: INVALID"
}
```

### 4.3 取消订阅

```json
{
  "type": "unsubscribe",
  "requestId": "req-002",
  "channels": ["kline.1m"],
  "symbols": ["EURUSD"]
}
```

服务端返回：

```json
{
  "type": "unsubscribed",
  "requestId": "req-002",
  "channels": ["kline.1m"],
  "symbols": ["EURUSD"]
}
```

## 5. 推送消息格式

### 5.1 价格 tick 推送

```json
{
  "type": "price.tick",
  "symbol": "EURUSD",
  "bid": "1.08321",
  "ask": "1.08331",
  "mid": "1.08326",
  "mark": "1.08326",
  "ts": "2026-04-16T16:00:00.123Z",
  "source": "TIINGO_FOREX",
  "stale": false
}
```

### 5.2 K 线推送

```json
{
  "type": "kline.1m",
  "symbol": "EURUSD",
  "interval": "1m",
  "open": "1.08300",
  "high": "1.08350",
  "low": "1.08290",
  "close": "1.08326",
  "volume": "0",
  "openTime": "2026-04-16T16:00:00Z",
  "closeTime": "2026-04-16T16:00:59Z",
  "isFinal": false
}
```

说明：

- `isFinal`：当前 K 线是否已收盘。`false` 表示当前 K 线仍在更新中，`true` 表示 K 线已完结
- 收盘时会额外推送一次 `isFinal: true` 的消息
- `mark` 当前仍是 `market-service` 的兼容字段；`trading-core-service` 做逐仓估值、TP/SL、强平和账户浮盈亏时，统一按方向从 `bid / ask` 解析有效标记价，不直接使用该单值字段

### 5.3 错误推送

服务端主动推送的错误通知：

```json
{
  "type": "error",
  "code": "30002",
  "message": "price source disconnected, data may be stale"
}
```

## 6. 心跳机制

- 服务端每 `30s` 发送 WebSocket Ping 帧（协议层 ping，非应用层 JSON）
- 客户端必须在 `10s` 内回复 Pong 帧
- 超时未回复：服务端主动关闭连接（`1001 Going Away`）

客户端也可主动发送应用层心跳：

```json
{
  "type": "ping",
  "ts": "2026-04-16T16:00:00Z"
}
```

服务端回复：

```json
{
  "type": "pong",
  "ts": "2026-04-16T16:00:00Z"
}
```

## 7. 连接关闭码

| Code | 含义 |
|---|---|
| `1000` | 正常关闭 |
| `1001` | 服务端主动关闭（心跳超时、维护） |
| `1008` | 认证失败（token 失效或被踢下线） |
| `1011` | 服务端内部错误 |

## 8. 客户端重连策略

- 连接断开后，客户端应使用指数退避重连：`1s, 2s, 4s, 8s, 16s`，最大间隔 `30s`
- 重连成功后需重新发送订阅请求（服务端不保存断线前的订阅状态）
- 重连期间如收到 `1008`（认证失败），需先刷新 token 再重连

## 9. 并发连接限制

- 同一用户最多允许 `5` 个并发 WebSocket 连接（一期值，后续按需调整）
- 超过限制时，新连接握手阶段返回 `HTTP 429`

## 10. 实现要求

- gateway 层负责 token 验证和连接数限制
- market-service 负责行情推送逻辑
- 行情推送频率不超过原始 tick 频率，不人工限速（交由 Tiingo 源决定频率）
- stale 行情：市场价格 key 过期后，服务端应向所有相关订阅客户端推送一次 stale 通知

```json
{
  "type": "price.tick",
  "symbol": "EURUSD",
  "stale": true,
  "ts": "2026-04-16T16:00:00Z"
}
```

## 11. 统一接口文档输出要求

每次 WebSocket 接口开发并测试通过后，必须同步更新：

- [FalconX统一接口文档](./FalconX统一接口文档.md)

要求：

- 由接口实现者负责更新
- 订阅请求、推送消息、错误消息、认证要求、重连约束都必须写入统一接口文档
- 未更新该文档，不视为接口任务完成
