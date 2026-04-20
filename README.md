# FalconX

## 项目定位

FalconX 是面向 CFD 场景的 `v1` 后端系统，一期采用 `GODSA`（`Gateway-Orchestrated Domain Core Services Architecture`）架构。

当前文档口径以“真实已落地能力”和“尚未完成范围”为准，不再保留阶段历史流水。

## 当前真实状态

- `Stage 1-5` 已完成：多模块工程、owner 服务边界、MySQL/Flyway、MyBatis + XML、Redis、Redisson、ClickHouse、Kafka、Gateway 基础鉴权与路由已落地。
- 当前正式执行阶段：`Stage 6A 收口专项`。`market-service` 已接入 Tiingo 真连接代码路径、真实 K 线聚合与 `market.kline.update` 生产侧链路，并补齐 `connected + subscription.confirmed + 35s 持续收流` 直接证据；`wallet-service` 已进入 EVM 原生币最小真实扫块链路，`trading-core-service` 已补齐 `market.price.tick` Kafka 入口失败重试与 `market.kline.update` 正式消费留痕，`TC-E2E-001 / 010 / 011` 已纳入 `gateway + identity-service + market-service + trading-core-service + wallet-service + Kafka` 的受控真运行时联调。当前阶段正式结论为：`Stage 6A` 主链路已收口；`Swap` 产品规则已冻结，但实现与验收仍未完成。
- `Stage 6B / 6C / 7 / 7A` 当前均未进入验收完成状态：用户侧实时与运营完整性、Jackson 3 专项迁移、端到端交易闭环、逐仓增强仍处于冻结或待收口状态。即使 `main` 上存在部分超前代码或接口事实，也不改变当前正式阶段仍为 `Stage 6A` 的口径。
- 当前系统仍不能表述为“生产可用”。

## 已落地能力

- `identity-service`：已提供注册、登录、刷新 Token 和基于业务入金事件的用户激活。
- `market-service`：已具备 Tiingo 实时报价接入、最新价缓存、ClickHouse `quote_tick / kline` 写入，以及 `GET /api/v1/market/quotes/{symbol}` 查询。
- `wallet-service`：已具备钱包地址分配、EVM 原生币最小扫块识别、链上回滚观察、`walletTxId` 稳定主键输出。
- `trading-core-service`：已具备业务入金入账、账户查询、市价开仓、行情快照消费与浮盈亏动态计算的基础闭环。
- `gateway`：已具备最小路由、访问 Token 校验、`traceId` 自动生成与透传。

## 未完成范围

- `Stage 6A`：Tiingo 外部真源自动化验证、外部链节点真扫块自动化验证、以及 `Swap / 隔夜利息` 的实现仍未完成；但 `Swap` 规则已完成冻结，当前阶段主链路已按正式口径收口。
- `Stage 6B`：北向 WebSocket、用户视角查询、运营观测与更多对外体验能力仍未启动本轮实施。
- `Stage 6C`：Jackson `2.21.2 -> 3.x` 迁移仍是独立专项，尚未启动实施。
- `Stage 7`：即使仓库存在部分超前实现，当前仍不把端到端交易闭环表述为阶段已验收完成。
- `Stage 7A`：逐仓增强能力仍未进入实现完成状态。

补充说明：

- `SPEC-TRD-001` 已冻结交易域正式产品规则，包括净持仓模型、稳定 `positionId`、双边手续费、保证金折算、`Swap`、负余额保护和穿仓处理
- 该冻结只代表产品规则已拍板，不代表当前仓库已完成代码实现、测试覆盖和文档验收切换

## 关键文档

- [架构方案](docs/architecture/falconx一期网关-服务-数据库架构方案.md)
- [开发启动手册](docs/setup/开发启动手册.md)
- [当前开发计划](docs/setup/当前开发计划.md)
