# falconx-wallet-service

## 1. 模块职责

`falconx-wallet-service` 负责链上原始事实。

后续应在本服务中补充：

- 钱包地址分配
- EVM / Tron / Solana 监听器
- 原始链上入金记录落库
- 确认数推进
- `falconx.wallet.deposit.detected` / `confirmed` / `reversed` 事件发布

Stage 6A 当前冻结的 Ethereum 前期测试节点为：

- `Ethereum Mainnet`：`wss://eth-mainnet.g.alchemy.com/v2/j6-5C7HK6dXPSSAyrZZqq`
- `Ethereum Sepolia`：`wss://eth-sepolia.g.alchemy.com/v2/j6-5C7HK6dXPSSAyrZZqq`
- `Ethereum Hoodi`：`wss://eth-hoodi.g.alchemy.com/v2/j6-5C7HK6dXPSSAyrZZqq`

当前 `wallet-service` 仍只维护一个 `ETH` 监听入口；
运行时通过 `falconx.wallet.chains.eth.rpc-url` 在上述测试端点中择一切换，
不扩展新的链类型或新的配置结构。

本地联调示例：

```bash
java -jar falconx-wallet-service.jar \
  --falconx.wallet.chains.eth.rpc-url=wss://eth-sepolia.g.alchemy.com/v2/j6-5C7HK6dXPSSAyrZZqq
```

当前已落地的最小真实链路：

- `ETH / BSC` 监听器会通过真实 EVM 节点读取最新区块高度
- 最新链头会回写到 `t_wallet_chain_cursor`
- 监听器会按 owner 游标回扫最近确认窗口区块
- 每轮扫描会先批量预载平台地址快照，避免按交易逐条查库
- 命中平台地址的原生币转账会生成 `ObservedDepositTransaction`
- 已确认原生币入金若在重扫窗口中从当前 canonical block 集合里消失，会生成 reversal 观察结果
- `amount` 口径已冻结为“按 token decimals 归一化后的业务金额，统一保留 8 位小数”；后续 ERC20 / SPL 解析必须先显式换算再落库和发事件
- 节点短时不可用时记录告警并等待下一轮轮询，不直接终止整个服务

当前仍未落地：

- ERC20 日志解析与代币精度处理
- 更高阶确认推进

## 2. Owner 数据

- `falconx_wallet.t_wallet_address`
- `falconx_wallet.t_wallet_deposit_tx`
- `falconx_wallet.t_wallet_chain_cursor`
- `falconx_wallet.t_outbox`

## 3. 包结构

- `controller`
- `application`
- `service`
- `listener`
- `repository`
- `producer`
- `entity`
- `dto`
- `config`

## 4. 主调用链

`Chain Listener -> Wallet Service -> Repository -> DB -> Kafka Producer`

## 5. 当前状态

- Stage 1 可启动骨架已建立
- Stage 2B 钱包事件底座骨架已建立
- Flyway migration 目录骨架已建立
- 已建立地址分配骨架、链监听骨架、确认推进骨架、Kafka 发布骨架
- 已具备真实链 SDK 客户端骨架与 owner 数据库存储
- 已补齐 EVM `http / https / ws / wss` 连接能力
- 已完成 EVM 原生币入金的最小扫块识别链路
- 已完成原生币回滚识别的最小链路
- 尚未完成 ERC20 解析与更高阶确认推进
