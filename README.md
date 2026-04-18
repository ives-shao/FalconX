# FalconX

## 项目定位

FalconX 是面向 CFD 场景的 `v1` 后端系统，一期采用 `GODSA`（`Gateway-Orchestrated Domain Core Services Architecture`）架构。

当前文档口径以“真实已落地能力”和“尚未完成范围”为准，不再保留阶段历史流水。

## 当前真实状态

- `Stage 1-5` 已完成：多模块工程、owner 服务边界、MySQL/Flyway、MyBatis + XML、Redis、Redisson、ClickHouse、Kafka、Gateway 基础鉴权与路由已落地。
- `Stage 6A` 部分完成：`market-service` 已接入 Tiingo 真连接与真实 K 线聚合，`wallet-service` 已进入 EVM 原生币最小真实扫块链路。
- `Stage 6B / 6C / 7 / 7A` 未完成：安全完整化、Jackson 3 专项迁移、端到端业务闭环、逐仓模式改造尚未进入验收完成状态。
- 当前系统仍不能表述为“生产可用”。

## 已落地能力

- `identity-service`：已提供注册、登录、刷新 Token 和基于业务入金事件的用户激活。
- `market-service`：已具备 Tiingo 实时报价接入、最新价缓存、ClickHouse `quote_tick / kline` 写入，以及 `GET /api/v1/market/quotes/{symbol}` 查询。
- `wallet-service`：已具备钱包地址分配、EVM 原生币最小扫块识别、链上回滚观察、`walletTxId` 稳定主键输出。
- `trading-core-service`：已具备业务入金入账、账户查询、市价开仓、行情快照消费与浮盈亏动态计算的基础闭环。
- `gateway`：已具备最小路由、访问 Token 校验、`traceId` 自动生成与透传。

## 未完成范围

- `Stage 6A`：真实业务 Topic 联调与失败重试验证、`wallet-service` 更深的链上解析与确认推进、Swap/隔夜利息结算等仍未完成。
- `Stage 6B`：RSA 密钥外部化、Token 黑名单、注册/登录限流、生产态安全配置仍未完成。
- `Stage 6C`：Jackson `2.21.2 -> 3.x` 迁移仍是独立专项，尚未启动实施。
- `Stage 7`：手动平仓、TP/SL 自动触发、强平执行、负净值保护、完整端到端业务闭环仍未完成。
- `Stage 7A`：逐仓模式 schema、平仓终态字段、追加保证金、`trade_type` 等仍未进入实现完成状态。

## 关键文档

- [架构方案](docs/architecture/falconx一期网关-服务-数据库架构方案.md)
- [开发启动手册](docs/setup/开发启动手册.md)
- [当前开发计划](docs/setup/当前开发计划.md)
