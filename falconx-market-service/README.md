# falconx-market-service

## 1. 模块职责

`falconx-market-service` 负责市场数据域能力。

后续应在本服务中补充：

- Tiingo 外汇 WebSocket 接入
- 报价标准化
- Redis 最新价缓存
- ClickHouse `quote_tick` 写入
- ClickHouse `kline` 写入
- `falconx.market.price.tick` / `falconx.market.kline.update` 事件发布

## 2. Owner 数据

- `falconx_market.t_symbol`
- `falconx_market.t_outbox`
- `falconx_market_analytics.quote_tick`
- `falconx_market_analytics.kline`

## 3. 包结构

- `controller`
- `application`
- `service`
- `provider`
- `cache`
- `analytics`
- `repository`
- `consumer`
- `producer`
- `entity`
- `dto`
- `config`

## 4. 主调用链

`Tiingo/WebSocket -> Provider -> Quote Standardizer -> Redis/ClickHouse -> Kafka -> Query API`

## 5. 当前状态

- Stage 1 可启动骨架已建立
- Stage 2A 市场数据接入骨架已建立
- Stage 4 最新报价查询接口已建立
- Flyway migration 目录骨架已建立
- 已建立 Tiingo Provider、标准化服务、Redis 写缓存骨架、ClickHouse 写入骨架、Kafka 发布骨架
- 已建立 `/api/v1/market/quotes/{symbol}` 北向查询接口
- 已建立 `traceId` 过滤器与统一异常处理
- 暂无真实外部客户端实现
