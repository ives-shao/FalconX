# falconx-trading-core-service

## 1. 模块职责

`falconx-trading-core-service` 是一期最重要的同步事务边界服务。

后续应在本服务中补充：

- 账户
- 账本
- 入金入账
- 订单
- 持仓
- 成交
- 风控
- 强平
- `quote-driven-engine`

## 2. Owner 数据

- `falconx_trading.t_account`
- `falconx_trading.t_ledger`
- `falconx_trading.t_deposit`
- `falconx_trading.t_order`
- `falconx_trading.t_position`
- `falconx_trading.t_trade`
- `falconx_trading.t_risk_config`
- `falconx_trading.t_liquidation_log`
- `falconx_trading.t_outbox`
- `falconx_trading.t_inbox`

## 3. 包结构

- `controller`
- `application`
- `service`
- `engine`
- `repository`
- `producer`
- `consumer`
- `entity`
- `dto`
- `command`
- `query`
- `calculator`
- `config`

## 4. 主调用链

### 同步下单

`Controller -> Application -> Account/Order/Risk Service -> Repository -> DB -> Producer`

### 实时触发

`Kafka Price Tick -> Consumer -> Quote-Driven Engine -> Calculator/Service -> Repository -> Producer`

### 入金入账

`Kafka Deposit Confirmed -> Consumer -> Account/Ledger Service -> Repository -> DB -> Producer`

## 5. 当前状态

- Stage 1 可启动骨架已建立
- Flyway migration 目录骨架已建立
- Stage 3B 交易核心底座已建立，已补：
  - 账户、账本、业务入金、订单、持仓、成交的内存 owner 骨架
  - 钱包确认入金和回滚事件消费骨架
  - 行情 tick 消费与内部行情快照骨架
  - 保证金、手续费、强平价的最小计算器和风控骨架
  - Outbox / Inbox 内存骨架
- Stage 4 最小北向接口已建立：
  - `GET /api/v1/trading/accounts/me`
  - `POST /api/v1/trading/orders/market`
- 已建立 `traceId` 过滤器与统一异常处理
- 已完成账户查询、成功下单、风控拒单三条 HTTP 集成测试
