# falconx-market-service

## 模块职责

`falconx-market-service` 负责市场数据 owner 能力：

- 外部报价接入与标准化
- Redis 最新价缓存
- ClickHouse `quote_tick / kline` 写入
- `falconx.market.price.tick` 与 `falconx.market.kline.update` 事件输出
- 北向市场查询与后续实时订阅能力

## 当前真实状态

- 当前处于 `Stage 6A` 部分完成状态，不再是“只有骨架、暂无真实实现”。
- 外部报价源已切到 Tiingo JDK WebSocket 真连接。
- 平台放行范围来自 owner `t_symbol.status=1`，并支持定时热刷新。
- 当前已落地的北向能力以 `GET /api/v1/market/quotes/{symbol}` 为主；北向 WebSocket 实时订阅仍未完成。

## 已落地能力

- Tiingo 真连接、重连和协议解析已落地。
- 报价标准化、最新价 Redis 缓存、`quote_tick` 写入已落地。
- K 线收盘聚合与 ClickHouse `kline` 写入已落地。
- `market.kline.update` 已通过 owner `t_outbox` 投递；高频 `price.tick` 走直接 Kafka 发布链路。
- 已具备 Tiingo crypto symbol 发现导入能力，新增品种会以 `status=2 suspended` 写入 owner 库。
- `market-service` 真运行时已纳入 gateway 代表性 E2E，并通过 owner ingestion 路径参与 `TC-E2E-001 / 010 / 011`；其中 `GET /api/v1/market/quotes/{symbol}` 已在受控 E2E 中返回测试注入的 owner 最新价。

## 未完成范围

- Tiingo 外部真源尚未纳入同一自动化用例。
- 北向 WebSocket 行情订阅链路尚未完成。
- 真实业务 Topic 联调、失败重试与跨服务连通性验证仍需继续收口。
- 更完整的市场订阅、回放和对外分发能力仍未进入完成状态。
