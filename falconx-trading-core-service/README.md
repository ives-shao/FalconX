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
- 当前已形成“入金入账 + 账户查询 + 市价开仓”的基础闭环。
- `QuoteDrivenEngine` 当前主要负责最新行情快照、stale 检测和持仓扫描，不代表 TP/SL、强平、平仓执行已经完成。
- 当前对外接口只有：
  - `GET /api/v1/trading/accounts/me`
  - `POST /api/v1/trading/orders/market`

## 已落地能力

- 已消费 `wallet.deposit.confirmed / wallet.deposit.reversed`，并按 `walletTxId` 完成业务幂等。
- 已落地业务入金、账户快照、账本流水、订单/持仓/成交的 owner 持久化主链路。
- 已消费 `market.price.tick`，账户查询会按最新行情动态计算 `unrealizedPnl` 并返回 `quoteStale`。
- 市价下单已支持保证金、手续费、强平价计算，并可持久化 `takeProfitPrice / stopLossPrice` 字段。

## 未完成范围

- 手动平仓接口与平仓终态持久化尚未完成。
- TP/SL 自动触发、强平执行、平仓/强平后的净敞口回补尚未完成。
- Swap/隔夜利息结算、负净值保护尚未完成。
- `Stage 7A` 需要的 `margin_mode`、持仓终态字段、`trade_type`、追加逐仓保证金链路尚未完成。
