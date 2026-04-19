# falconx-trading-core-service

## 模块职责

`falconx-trading-core-service` 是一期 CFD 交易 owner 服务，负责：

- 账户与账本
- 业务入金入账
- 订单、持仓、成交
- 风控与行情驱动处理
- 交易域 Outbox / Inbox

## 当前真实状态

- owner MySQL、Flyway、`MyBatis + XML Mapper`、Outbox / Inbox 已落地，不再是“内存 owner 骨架”阶段。
- 当前已形成“入金入账 + 账户查询 + 市价开仓 + 手动平仓 + TP/SL / 强平执行”的最小闭环。
- `QuoteDrivenEngine` 当前负责最新行情快照、stale 检测、TP/SL / 强平扫描，以及 B-book 风险敞口美元换算的价格驱动刷新。
- 当前对外接口只有：
  - `GET /api/v1/trading/accounts/me`
  - `POST /api/v1/trading/orders/market`
  - `POST /api/v1/trading/positions/{positionId}/close`
  - `PATCH /api/v1/trading/positions/{positionId}`

## 已落地能力

- 已消费 `wallet.deposit.confirmed / wallet.deposit.reversed`，并按 `walletTxId` 完成业务幂等。
- 已落地业务入金、账户快照、账本流水、订单/持仓/成交的 owner 持久化主链路。
- 已消费 `market.price.tick`，账户查询会按最新行情动态计算 `unrealizedPnl` 并返回 `quoteStale`。
- 市价下单已支持保证金、手续费、强平价计算，并可持久化 `takeProfitPrice / stopLossPrice` 字段。
- 已落地手动平仓、TP/SL 自动触发、强平执行、负净值保护，以及 `trading.position.closed / trading.liquidation.executed` outbox 事件。
- 已落地 `net_exposure_usd`、`hedge_threshold_usd`、`t_hedge_log`、阈值告警 / 恢复日志，以及超阈值后的服务内 Spring Event stub。

## 未完成范围

- 真实 A-book 对冲接口尚未接入，当前只提供 `t_hedge_log` 审计事实、结构化日志和服务内 Spring Event stub。
- Swap / 隔夜利息结算尚未完成。
- `Stage 7A` 需要的追加逐仓保证金、强平价重算和更完整 `ISOLATED` 增强尚未完成。
