# Tiingo 报价与实时市场数据契约

## 1. 目标

FalconX v1 将 `Tiingo Forex WebSocket` 作为外汇报价主来源。

同时，`market-service` 新增 `Tiingo Crypto WebSocket` 的 symbol 发现能力，
用于在 owner 侧补录 crypto 品种元数据；它**不是**当前标准报价主来源。

官方文档：

- `https://www.tiingo.com/documentation/websockets/forex`
- `https://www.tiingo.com/documentation/websockets/crypto`

当前项目内冻结的实际接入样例：

订阅地址：

- `wss://api.tiingo.com/fx`

全品种订阅报文：

```json
{
  "eventName": "subscribe",
  "eventData": {
    "authToken": "YOUR_TIINGO_API_KEY"
  }
}
```

样例返回：

```json
{
  "service": "fx",
  "messageType": "A",
  "data": [
    "Q",
    "xptusd",
    "2026-04-17T16:35:36.534000+00:00",
    1000000.0,
    2132.35,
    2135.25,
    1000000.0,
    2138.15
  ]
}
```

当前项目对该数组的解释冻结为：

- `data[1]`：外部 ticker，例如 `xptusd`
- `data[2]`：报价时间戳
- `data[4]`：`bid`
- `data[7]`：`ask`
- `data[3] / data[6]`：当前视为盘口量字段
- `data[5]`：当前视为中间附加字段，不直接进入标准报价对象

`Tiingo crypto` 当前项目内冻结的 symbol 采样样例：

订阅地址：

- `wss://api.tiingo.com/crypto`

全品种订阅报文：

```json
{
  "eventName": "subscribe",
  "eventData": {
    "authToken": "YOUR_TIINGO_API_KEY"
  }
}
```

样例返回：

```json
{
  "service": "crypto_data",
  "messageType": "A",
  "data": [
    "T",
    "solusdt",
    "2026-04-18T03:27:39.737000+00:00",
    "binance",
    8.39,
    126.968
  ]
}
```

当前项目对该数组的解释冻结为：

- `data[0]`：`T=trade`
- `data[1]`：外部 ticker，例如 `solusdt`
- `data[2]`：成交时间戳
- `data[3]`：来源交易所
- `data[4]`：成交数量
- `data[5]`：成交价格

接入 owner 固定为：

- `falconx-market-service`

补充说明：

- Tiingo 通用 WebSocket 文档截图确认：订阅成功后会先返回一类 `messageType=I` 的信息帧
- 该信息帧中通常可见 `data.subscriptionId`，后续若需要做更细粒度的订阅管理，可用它作为连接级订阅标识
- 文档截图同时确认，服务端会周期性发送 `messageType=H` 的 HeartBeat 帧，当前说明为约 `30s` 一次
- 当前项目对 `I / H / E` 这类非报价帧的处理策略是：不进入标准报价对象，只用于连接存活与问题排查

Tiingo 通用 WebSocket 文档中定义的 `messageType` 含义如下：

- `A`：新增数据
- `U`：更新已有数据
- `D`：删除已有数据
- `I`：信息/元数据
- `E`：错误消息
- `H`：心跳消息

当前 `market-service` 的实现约束：

- 只有能解析出 `symbol + bid + ask + ts` 的消息才进入标准报价对象
- `I / H / E` 或其他无法构成完整报价的消息直接忽略
- `I` 帧当前会输出订阅确认日志，若携带 `subscriptionId`，将其作为重连后订阅恢复的确认信息
- `H` 帧当前只输出调试级心跳日志，不触发业务动作
- `E` 帧当前输出告警日志，并把最近一次服务端错误上下文缓存为后续重连原因的一部分
- 当 WebSocket 随后触发 `onClose / onError` 时，provider 会把：
  - close code 分类
  - close frame 原始 reason
  - 最近一次 `E` 帧中的 `service / code / message`
  统一拼成 `reconnectReason` 输出日志并用于重连调度
- `E` 帧本身暂不直接触发主动重连，避免仅因一条服务端错误提示就提前打断仍然存活的连接
- 若后续 Tiingo 在 `A / U` 报文中调整数组字段顺序，需要同步更新协议支持组件与单元测试
- `crypto` 端点当前返回的主流量是 `trade` 帧，不满足平台标准报价对象对 `bid / ask` 的要求；
  因此当前实现只把它用于 symbol 发现与 `t_symbol` 补录，不写 Redis 最新价、不发 `market.price.tick`
- `crypto` symbol 补录固定采用：
  - `10s` 采样窗口
  - 交易所白名单：`binance / bitfinex / bitstamp / bybit / cryptodotcom / gdax / gemini / huobi / kraken / kucoin / mexc / okex / poloniex`
  - 允许 quote：`USDT / USDC / USD / EUR`
  - base 长度范围：`2..12`
  - 新增 symbol 默认写成 `status=2 suspended`
- `2026-04-18` 的首次真实采样结果是：过滤后得到 `392` 个 crypto symbol 候选，
  其中 `390` 个为新增补录；`BTCUSDT / ETHUSDT` 已存在，因此未覆盖原始配置

截图还补充确认了两点与当前实现直接相关的约束：

- `thresholdLevel` 是 Crypto / IEX WebSocket 文档中的概念，不属于当前 Forex 接入的必要参数
- Forex 文档强调的是 Top-of-Book（Bid/Ask）与 Intraday OHLC 能力，当前项目只把实时 Bid/Ask WebSocket 接入作为主链路，K 线由服务内聚合完成

## 2. 边界冻结

只有 `market-service` 允许直接：

- 连接 Tiingo WebSocket
- 使用 Tiingo API Key
- 标准化原始报价

其他服务禁止：

- 直接连接 Tiingo
- 直接持有 Tiingo 密钥
- 自行拼装外部行情对象

## 3. 一期职责拆分

### `market-service`

负责：

- 接外部报价
- 标准化 tick
- 计算 `bid / ask / mid / mark`
- 写 ClickHouse 报价历史
- 计算并落盘 K 线
- 写 Redis 最新价
- 发布价格与 K 线事件

### `trading-core-service`

负责：

- 消费价格事件
- 计算挂单触发
- 计算持仓浮盈浮亏
- 计算保证金率
- 触发止盈止损
- 触发强平
- 逐仓有效标记价固定按 `BUY / 多头 -> bid`、`SELL / 空头 -> ask`

结论：

- `K线` 在 `market-service`
- `订单实时计算` 在 `trading-core-service`

补充说明：

- Tiingo 当前接入按“全品种订阅 + 服务内本地过滤”实现
- `market-service` 启动时加载 owner 中全部已启用交易品种，并按固定周期热刷新本地白名单
- provider 收到全量报价后，只把当前服务关心的 symbol 交给应用层
- 当前真实采样已经确认，Tiingo `fx` 源不只包含传统外汇对，还会出现贵金属、指数与能源符号；
  因此本地过滤不再按 `market_code=FX` 限制，而是以平台 `t_symbol` 白名单为准
- 运行时若数据库临时不可用，保留上一轮白名单，不因一次刷新失败清空当前行情接入范围
- Forex 文档截图补充说明：Forex 数据当前处于 beta，市场时间为 `Sunday 8pm EST -> Friday 5pm EST`
- `Tiingo crypto` 的当前实现不进入这里的运行时白名单过滤链路，因为它导入到 `t_symbol`
  的记录默认是 `status=2 suspended`；只有后续人工启用后，才会进入平台的活跃 symbol 白名单

## 4. 标准报价对象

一期统一报价对象至少包含：

- `symbol`
- `bid`
- `ask`
- `mid`
- `mark`
- `ts`
- `source`
- `stale`

字段说明：

- `symbol`：平台内部标准 symbol，例如 `EURUSD`
- `bid`：卖出成交参考价
- `ask`：买入成交参考价
- `mid`：`(bid + ask) / 2`
- `mark`：市场层兼容标记价字段；`trading-core-service` 做逐仓估值、强平和账户浮盈亏时，必须按持仓方向从 `bid / ask` 解析有效标记价，不得直接使用该单值字段
- `ts`：报价时间戳
- `source`：报价来源，例如 `TIINGO_FOREX`
- `stale`：是否超时

## 5. Redis 约定

最新价 Key：

- `falconx:market:price:{symbol}`

字段：

- `bid`
- `ask`
- `mid`
- `mark`
- `ts`
- `source`
- `stale`

**TTL 策略：**

- Key TTL 设置为 `10s`
- market-service 每次写入报价时刷新 TTL
- Key 过期即视为行情不可用，`stale` 自动成立
- 读取方若 Key 不存在，必须按 `stale=true` 处理，不得使用缓存默认值

K 线 Key：

- `falconx:market:kline:{symbol}:{interval}`

说明：

- `market-service` 是 Redis 行情 key 的唯一写 owner
- `trading-core-service` 只读，不写

## 5.1 ClickHouse 约定

一期由 `market-service` 负责写入：

- `falconx_market_analytics.quote_tick`
- `falconx_market_analytics.kline`

固定写入策略：

- 每个 tick 写 Redis
- 每个 tick 异步批量写 `quote_tick`
- 当前未收盘 K 线只在 Redis 或内存中维护
- 只有 K 线收盘时写入最终 `kline`

说明：

- 这样可以避免每个 tick 都更新 MySQL 或频繁覆盖 K 线
- 读取实时行情仍优先走 Redis
- 读取历史报价和 K 线走 ClickHouse

## 6. Kafka 约定

一期至少冻结下面两个主题：

- `falconx.market.price.tick`
- `falconx.market.kline.update`

投递策略固定为：

- `falconx.market.price.tick`：直接发 Kafka，best-effort，不走 Outbox
- `falconx.market.kline.update`：走 Outbox，保证已收盘 K 线事件可重试投递

运行时传输形态（与 Kafka 事件规范保持完全一致）：

- Kafka key：
  - `falconx.market.price.tick`：`symbol`
  - `falconx.market.kline.update`：`symbol:interval`
- Kafka headers：
  - `X-Event-Id`
  - `X-Event-Type`
  - `X-Event-Source`
  - `X-Trace-Id`
- Kafka body：只放业务 payload JSON

`falconx.market.price.tick` 运行时示例：

```json
{
  "symbol": "EURUSD",
  "bid": "1.08321",
  "ask": "1.08331",
  "mid": "1.08326",
  "mark": "1.08326",
  "ts": 1776676530.123,
  "source": "TIINGO_FOREX",
  "stale": false
}
```

说明：

- `price.tick` 供 `trading-core-service` 的 `quote-driven-engine` 消费
- `payload.mark` 当前保留兼容字段语义；`trading-core-service` 触发 TP/SL、强平和 `net_exposure_usd` 估值时，统一按 `bid / ask` 解析有效标记价
- `kline.update` 供前端推送、后台统计和二期功能复用
- `price.tick` 属于高频事件，不为此写 MySQL `t_outbox`
- `price.tick.ts` 当前运行时按 Jackson 数值时间输出，语义为 Unix epoch seconds，可带小数秒；消费方应按数值时间处理，不得强绑字符串格式

## 7. stale 策略

统一规则：

- 若当前时间减去 `ts` 超过 `5s`，则视为 stale
- stale 必须按读取时的“当前时间 vs ts”动态计算，不能只依赖写入缓存时的布尔值

业务影响：

- 开仓：默认拒绝
- 平仓：允许继续，但要记录 `price_source` 和 `price_ts`
- 强平：允许继续，但要记录 `price_source` 和 `price_ts`

## 8. 重连策略

一期先冻结行为，不提前做复杂实现：

- 首次连接失败：固定间隔重试
- 连接断开：自动重连
- 重连期间不伪造新价格
- stale 必须可判定

建议初版参数：

- 重连间隔：`5s`
- 连续失败：日志级别逐级提升

## 8.1 K 线聚合状态约束

当前 `market-service` 的 `KlineBucketState` 为进程内聚合状态，不做跨重启恢复。

冻结结论：

- 服务重启时，最多会造成当前聚合窗口的一段 K 线断口
- 当前阶段接受这一限制，并已作为已知运行时约束写入正式文档
- 若后续需要无损恢复，应在后续阶段从 ClickHouse 或其他持久化介质恢复最近窗口状态

## 9. 本地测试价

为了避免开发强依赖外部网络，`dev/test` 环境允许本地测试价注入。

约束：

- 只能在 `dev/test`
- 报价对象结构必须与正式对象一致
- `source` 必须标记为 `LOCAL_TEST`

## 10. 当前结论

FalconX v1 的市场数据边界已经冻结为：

- `market-service` 负责报价、K 线与 ClickHouse 市场历史存储
- `trading-core-service` 负责基于报价的订单与风险实时计算
- 两者通过 `Redis + Kafka` 协作，而不是服务间直接互调
