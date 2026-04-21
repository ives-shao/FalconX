# falconx-market-service

## 模块职责

`falconx-market-service` 负责市场数据 owner 能力：

- 外部报价接入与标准化
- Redis 最新价缓存
- ClickHouse `quote_tick / kline` 写入
- `falconx.market.price.tick` 与 `falconx.market.kline.update` 事件输出
- 北向市场查询与后续实时订阅能力

## 当前真实状态

- 当前模块在 `Stage 6A` 范围内已完成主链路收口，不再是“只有骨架、暂无真实实现”。
- 外部报价源已切到 Tiingo JDK WebSocket 真连接。
- 已补齐 Tiingo 真连接 `connected + subscription.confirmed + 35s 持续收流` 直接证据；若本地网络存在 TLS inspection，需要额外配置 `falconx.market.tiingo.trust-store-*`。
- 平台放行范围来自 owner `t_symbol.status=1`，并支持定时热刷新。
- 当前已落地的北向能力以 `GET /api/v1/market/quotes/{symbol}` 为主；北向 WebSocket 实时订阅仍未完成。

## 已落地能力

- Tiingo 真连接、重连和协议解析已落地。
- 报价标准化、最新价 Redis 缓存、`quote_tick` 写入已落地。
- K 线收盘聚合与 ClickHouse `kline` 写入已落地。
- `market.kline.update` 已通过 owner `t_outbox` 投递；高频 `price.tick` 走直接 Kafka 发布链路。
- `t_swap_rate` owner 读取、按 `symbol` 聚合的 Redis 共享快照、启动预热与每日 `UTC 00:00` 刷新已落地；首版共享 key 为 `falconx:market:swap-rate:{symbol}`，供 `trading-core-service` 读取，不新增 `Swap` Kafka 费率事件。
- 已补齐受控 Tiingo 外部真源自动化入口：
  - `MarketTiingoExternalSourceAutomationIntegrationTests` 覆盖 `connected / subscription.confirmed / 持续收流 / stale / Redis / ClickHouse / Kafka / 日志证据`
  - `JdkTiingoQuoteProviderExternalFailureIntegrationTests` 覆盖真实 Tiingo 端点下的错误认证失败路径与重连日志
  - 上述用例只在显式设置 `FALCONX_MARKET_TIINGO_EXTERNAL_TEST_ENABLED=true` 且提供 `FALCONX_MARKET_TIINGO_API_KEY` 时执行；若本地网络存在 TLS inspection，还需配置 `falconx.market.tiingo.trust-store-*`
- 已具备 Tiingo crypto symbol 发现导入能力，新增品种会以 `status=2 suspended` 写入 owner 库。
- `market-service` 真运行时已纳入 gateway 代表性 E2E，并通过 owner ingestion 路径参与 `TC-E2E-001 / 010 / 011`；其中 `GET /api/v1/market/quotes/{symbol}` 已在受控 E2E 中返回测试注入的 owner 最新价。

## 未完成范围

- Tiingo 外部真源自动化入口已补齐，但若当前环境未显式提供 API key / trust store 运行条件，仍不会形成可归档的真源运行证据。
- 北向 WebSocket 行情订阅链路尚未完成。
- 更完整的市场订阅、回放和对外分发能力仍未进入完成状态。
- `Swap rate` 共享快照已落地，但这不代表 `Stage 6B` 的 `Swap` 查询展示、业务事件和整体验收已经完成。
