# falconx-wallet-service

## 文档边界

本文件只记录 wallet 模块职责、当前已落地能力与当前边界。

- 项目阶段状态与验收结论以 `docs/setup/当前开发计划.md` 为准。
- 钱包链路、数据库 owner、事件契约、链节点接入规则以专题规范为准。
- 本 README 不再单独声明“`Stage 6A` 部分完成”之类阶段判断。

## 模块职责

`falconx-wallet-service` 负责链上原始事实 owner 能力：

- 钱包地址分配
- 链上原始入金识别与持久化
- 确认推进与回滚观察
- `falconx.wallet.deposit.*` 事件输出
- 链游标与外部节点监听治理

## 当前已落地能力

### owner 原始事实链路

- 已落地钱包地址分配与 owner 持久化。
- 已通过 `t_wallet_chain_cursor` 驱动扫块游标。
- 已落地 EVM 原生币与 ERC20 最小扫块识别路径。
- 已支持确认窗口重扫、reversal 观察与 `walletTxId` 稳定主键输出。

### 事件链路

- 已形成 `falconx.wallet.deposit.detected`。
- 已形成 `falconx.wallet.deposit.confirmed`。
- 已形成 `falconx.wallet.deposit.reversed`。
- 这些事件继续驱动 `trading-core-service` 的业务入金与回滚链路。

### 外部节点与自动化入口

- 运行时已具备真实节点连接能力。
- 外部 ETH 节点成功 / 失败路径门禁用例已具备。
- 当前验证口径以 `ETH` 配置限制为主，不把“当前验证只走 ETH”误写成“代码已单链化”。

## 模块联动

- `gateway -> wallet-service`：地址申请与相关北向能力由 gateway 透传。
- `wallet-service -> trading-core-service`：通过 `falconx.wallet.deposit.confirmed / reversed` 驱动业务入金与回滚。
- `wallet-service -> MySQL`：owner 持久化地址、游标、链上原始事实。

## 当前边界与不应误写的内容

- 本轮正式验证按 `ETH` 配置限制执行，但当前代码仍保留多链装配事实。
- 外部链节点真扫块自动化入口虽已具备，但可归档真跑证据仍依赖显式 `ETH RPC / JVM trust store` 条件。
- 本 README 不承担项目阶段验收结论职责；不得再写“当前处于 `Stage 6A` 部分完成状态”之类过时表述。
- 本 README 不将更完整代币治理、多链全量能力或生产化节点治理写成当前已完成能力。

## 相关文档

- `docs/event/Kafka事件规范.md`
- `docs/database/falconx一期数据库设计.md`
- `docs/api/FalconX统一接口文档.md`
- `docs/setup/当前开发计划.md`
