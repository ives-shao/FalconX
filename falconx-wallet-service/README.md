# falconx-wallet-service

## 模块职责

`falconx-wallet-service` 负责链上原始事实 owner 能力：

- 钱包地址分配
- 链上原始入金识别与持久化
- 确认推进与回滚观察
- `falconx.wallet.deposit.*` 事件输出

## 当前真实状态

- 当前处于 `Stage 6A` 部分完成状态，已从 stub/骨架进入 EVM 原生币 + ERC20 最小真实链路。
- 运行时已具备真实节点连接能力，并通过 `t_wallet_chain_cursor` 驱动扫块游标。
- 当前实现聚焦“EVM 最小正式原始事实链路”，尚未扩展到更完整代币治理和多链全量能力。

## 已落地能力

- 钱包地址分配与 owner MySQL 持久化已落地。
- 已支持 EVM `http / https / ws / wss` 连接方式。
- 已具备按游标扫块、预载平台地址快照、识别平台地址原生币与 ERC20 `Transfer` 入金的最小主链路。
- 已显式完成 `token decimals -> 业务金额` 换算，统一保留 `8` 位小数。
- 已可通过确认窗口重扫稳定推进 `DETECTED / CONFIRMING / CONFIRMED` 主链路。
- 已支持重扫窗口中的 reversal 观察。
- 跨服务稳定原始交易主键已统一为 `walletTxId`，便于下游幂等消费。
- 低频关键事件已切到 owner `t_outbox` 投递链路。
- `wallet-service` 真运行时已纳入 gateway 代表性 E2E，并通过地址分配、原始入金事实、outbox 投递参与 `TC-E2E-001 / 010 / 011`。

## 未完成范围

- 外部链节点真扫块尚未纳入同一自动化用例。
- 更完整的链重组处理与生产级 token metadata 治理尚未完成。
- 其他链类型与更完整的钱包域能力尚未进入完成状态。
