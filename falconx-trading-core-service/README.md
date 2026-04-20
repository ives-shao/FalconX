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
- 当前已形成“入金入账 + 账户查询 + 市价开仓 + 手动平仓 + TP/SL / 强平执行”的最小闭环；这属于 `Stage 6A` 当前运行事实核对，不等于 `Stage 7 / 7A` 已验收。
- `QuoteDrivenEngine` 当前负责最新行情快照、stale 检测、TP/SL / 强平扫描，以及 B-book 风险敞口美元换算的价格驱动刷新。
- `market.kline.update` 已在 `trading-core-service` 侧形成正式低频消费，并在 `t_inbox` 留下 owner 审计事实。
- 当前对外接口只有：
  - `GET /api/v1/trading/accounts/me`
  - `POST /api/v1/trading/orders/market`
  - `POST /api/v1/trading/positions/{positionId}/close`
  - `PATCH /api/v1/trading/positions/{positionId}`

## 已落地能力

- 已消费 `wallet.deposit.confirmed / wallet.deposit.reversed`，并按 `walletTxId` 完成业务幂等。
- 已落地业务入金、账户快照、账本流水、订单/持仓/成交的 owner 持久化主链路。
- 已消费 `market.price.tick`，账户查询会按最新行情动态计算 `unrealizedPnl` 并返回 `quoteStale`；该高频链路当前保持直连 Kafka，不写 `t_inbox`。
- 市价下单已支持保证金、手续费、强平价计算，并可持久化 `takeProfitPrice / stopLossPrice` 字段。
- 已落地手动平仓、TP/SL 自动触发、强平执行、负净值保护，以及 `trading.position.closed / trading.liquidation.executed` outbox 事件。
- 已落地 `net_exposure_usd`、`hedge_threshold_usd`、`t_hedge_log`、阈值告警 / 恢复日志，以及超阈值后的服务内 Spring Event stub。

## Stage 6A 收口边界

- 当前 `Stage 6A` 在 trading 域必须收口的内容是：`wallet.deposit.confirmed / reversed`、`market.price.tick` 高频消费、`market.kline.update` 低频消费证据，以及账户 / 持仓 / 风险计算链路的现有运行口径核对。
- 手动平仓、TP/SL、强平当前已经存在真实实现与测试证据，但这里只把它们当作 `Stage 6A` 的交易链路核对事实，不把它们表述为 `Stage 7 / 7A` 已整体验收。
- 当前不把 `TradingHedgeAlertEvent`、真实 A-book 对冲、追加逐仓保证金、强平价重算写成已完成能力。

## 模块联动与接口关联

- `gateway -> trading-core-service`：通过 `GET /api/v1/trading/accounts/me`、`POST /api/v1/trading/orders/market`、`POST /api/v1/trading/positions/{positionId}/close`、`PATCH /api/v1/trading/positions/{positionId}` 驱动账户查询、开仓、手动平仓和持仓风险参数修改。
- `market-service -> trading-core-service`：`falconx.market.price.tick` 驱动最新价、浮盈亏、TP/SL 与强平扫描；`falconx.market.kline.update` 当前只形成低频正式消费与 `t_inbox` 审计事实，不额外派生交易域状态。
- `wallet-service -> trading-core-service`：`falconx.wallet.deposit.confirmed / reversed` 驱动业务入金与回滚；成功入账后由 trading 继续通过 `trading.deposit.credited` 联动 `identity-service`。
- 手动平仓、TP/SL、强平共享 `TradingPositionCloseApplicationService` 的 owner 写路径：统一更新账户、账本、持仓、成交、风险暴露与 outbox，强平额外写 `t_liquidation_log`。

## 未完成范围

- 真实 A-book 对冲接口尚未接入，当前只提供 `t_hedge_log` 审计事实、结构化日志和服务内 Spring Event stub。
- Swap / 隔夜利息结算尚未完成。
- `Stage 7A` 需要的追加逐仓保证金、强平价重算和更完整 `ISOLATED` 增强尚未完成。
