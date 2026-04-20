# FalconX v1 Kafka 事件规范

## 1. 目标

统一 FalconX v1 的事件命名、消息信封、分区键、重试和幂等策略。

## 2. 基本原则

- 一期消息语义采用 `at-least-once`
- 消费者必须幂等
- 跨服务业务协作优先走事件
- 数据库状态变更与事件发布必须通过 `Outbox` 或等价可靠发布方案衔接

## 3. Topic 命名规范

统一格式：

- `falconx.<context>.<entity>.<event>`

示例：

- `falconx.market.price.tick`
- `falconx.market.kline.update`
- `falconx.wallet.deposit.detected`
- `falconx.wallet.deposit.confirmed`
- `falconx.wallet.deposit.reversed`
- `falconx.trading.order.created`
- `falconx.trading.order.filled`
- `falconx.trading.position.closed`
- `falconx.trading.liquidation.executed`

DLQ 统一格式：

- `<original-topic>.dlq`

## 4. 消息承载规范

一期当前运行时统一采用“Kafka headers 承载事件元数据，消息体只放业务 payload JSON”的传输口径：

- Kafka message body：只放业务 payload JSON
- Kafka message key：作为运行时 `partitionKey`
- Kafka headers：
  - `X-Event-Id`
  - `X-Event-Type`
  - `X-Event-Source`
  - `X-Trace-Id`

说明：

- 当前 Stage 6A 运行时没有把 `schemaVersion / occurredAt` 再重复塞进消息体；若后续需要补充，必须以加性方式扩展，不能破坏现有消费者对“body=payload JSON”的兼容假设
- 消费端默认从 Kafka headers 读取 `eventId / eventType / source / traceId`，再把消息体按目标 payload 契约直接反序列化
- 若业务需要显式记录分区键，应以 Kafka key 为准，不再在消息体里重复一份 `partitionKey`

运行时示例：

- Kafka key：`ETH:0xabc123`
- Kafka headers：
  - `X-Event-Id: evt-wallet-confirmed-0001`
  - `X-Event-Type: wallet.deposit.confirmed`
  - `X-Event-Source: falconx-wallet-service`
  - `X-Trace-Id: 9fbcf7c6f0d1467b`
- Kafka body：

```json
{
  "walletTxId": 3000001,
  "userId": 2000001,
  "chain": "ETH",
  "token": "USDT",
  "txHash": "0xabc123",
  "fromAddress": "0xsource",
  "toAddress": "0xplatform",
  "amount": "1500.00000000",
  "confirmations": 12,
  "requiredConfirmations": 12,
  "confirmedAt": "2026-04-16T16:05:00Z"
}
```

## 5. 分区键规范

按业务特性选 key，不强求全系统统一一个规则。

建议：

- 市场价格事件：按 `symbol`
- 钱包入金事件：按 `chain:txHash`
- 订单与持仓事件：按 `userId` 或 `orderId`

原则：

- 同一业务实体相关事件应尽量进入同一分区

## 6. 生产者规范

- 事件必须来自明确 owner 服务
- 业务表写入成功后再发布事件
- 禁止在业务逻辑里直接无保护地“先发消息后写库”
- 涉及关键状态变更时必须使用 `Outbox`

## 7. 消费者规范

消费者必须具备：

- 幂等处理
- 去重能力
- 重试能力
- 死信转移能力

最低要求：

- 使用 `eventId` 去重
- 业务唯一键二次保护
- 失败达到阈值后进入 DLQ

## 8. 重试规范

建议策略：

- 短暂错误：有限次重试
- 业务不可恢复错误：直接进入 DLQ

典型不可恢复错误：

- payload 缺字段
- schemaVersion 不支持
- 业务实体不存在且无法补偿

## 9. 事件版本规范

- 事件增加字段时优先向后兼容
- 破坏性变更必须升级 `schemaVersion`
- 消费者必须显式处理版本

## 10. 消费组命名规范

统一格式：

- `falconx.<service-name>.<context>-consumer-group`

示例：

- `falconx.trading-core-service.price-tick-consumer-group`
- `falconx.trading-core-service.deposit-confirmed-consumer-group`
- `falconx.identity-service.deposit-credited-consumer-group`

规则：

- 每个消费者逻辑功能使用独立消费组名称
- 不同实例同一功能共享同一消费组名称
- 消费组名称不随代码变更而随意修改（变更意味着重置 offset）

## 11. 当前一期关键主题

一期固定保留：

- `falconx.market.price.tick`（market-service 发布，trading-core-service 消费；Kafka 入口允许容器级失败重试）
- `falconx.market.kline.update`（market-service 发布，trading-core-service 消费并在 `t_inbox` 留痕）
- `falconx.wallet.deposit.detected`（wallet-service 发布）
- `falconx.wallet.deposit.confirmed`（wallet-service 发布，trading-core-service 消费）
- `falconx.wallet.deposit.reversed`（wallet-service 发布，trading-core-service 消费）
- `falconx.trading.deposit.credited`（trading-core-service 发布，identity-service 消费）

说明：

- `falconx.trading.deposit.credited` 是用户激活链路的关键事件
- trading-core-service 完成入账后必须发布此事件
- identity-service 消费此事件，将用户状态从 `PENDING_DEPOSIT` 迁移到 `ACTIVE`

后续由 `trading-core-service` 发布：

- `falconx.trading.order.created`
- `falconx.trading.order.filled`
- `falconx.trading.position.closed`
- `falconx.trading.liquidation.executed`
- `falconx.trading.swap.charged`

## 12. 关键事件 Payload 约定

### 12.1 `falconx.wallet.deposit.detected`

用途：

- `wallet-service` 在识别到归属平台地址的原始链入金时发布
- 该事件代表“已发现链事实，但尚未最终确认”

推荐 payload：

```json
{
  "walletTxId": 3000001,
  "userId": 2000001,
  "chain": "ETH",
  "token": "USDT",
  "txHash": "0xabc123",
  "fromAddress": "0xsource",
  "toAddress": "0xplatform",
  "amount": "1500.00000000",
  "confirmations": 3,
  "requiredConfirmations": 12,
  "detectedAt": "2026-04-16T16:00:00Z"
}
```

字段要求：

- `walletTxId`：wallet owner 产出的稳定原始交易主键，跨服务幂等统一依据该字段
- `userId`：归属用户 ID
- `chain`：链标识
- `token`：入金币种
- `txHash`：链上哈希
- `fromAddress`：来源地址
- `toAddress`：目标地址
- `amount`：已按 token decimals 归一化后的业务金额，统一保留 `8` 位小数；禁止直接传 raw amount
- `confirmations`：当前确认数
- `requiredConfirmations`：所需确认数
- `detectedAt`：首次检测时间

分区键建议：

- `chain:txHash`

### 12.2 `falconx.wallet.deposit.confirmed`

用途：

- `wallet-service` 在原始链入金满足最终确认条件后发布
- `trading-core-service` 消费后执行业务入账

推荐 payload：

```json
{
  "walletTxId": 3000001,
  "userId": 2000001,
  "chain": "ETH",
  "token": "USDT",
  "txHash": "0xabc123",
  "fromAddress": "0xsource",
  "toAddress": "0xplatform",
  "amount": "1500.00000000",
  "confirmations": 12,
  "requiredConfirmations": 12,
  "confirmedAt": "2026-04-16T16:05:00Z"
}
```

字段要求：

- `walletTxId`：wallet owner 产出的稳定原始交易主键，trading-core-service 必须按该字段做业务入账幂等
- `userId`：归属用户 ID
- `chain`：链标识
- `token`：入金币种
- `txHash`：链上哈希
- `fromAddress`：来源地址
- `toAddress`：目标地址
- `amount`：已按 token decimals 归一化后的业务金额，统一保留 `8` 位小数；禁止直接传 raw amount
- `confirmations`：当前确认数
- `requiredConfirmations`：所需确认数
- `confirmedAt`：最终确认时间

分区键建议：

- `chain:txHash`

### 12.3 `falconx.wallet.deposit.reversed`

用途：

- `wallet-service` 在链回滚或原始入金失效时发布
- `trading-core-service` 可基于该事件触发回滚或人工补偿流程

推荐 payload：

```json
{
  "walletTxId": 3000001,
  "userId": 2000001,
  "chain": "ETH",
  "token": "USDT",
  "txHash": "0xabc123",
  "fromAddress": "0xsource",
  "toAddress": "0xplatform",
  "amount": "1500.00000000",
  "confirmations": 0,
  "requiredConfirmations": 12,
  "reversedAt": "2026-04-16T16:06:00Z"
}
```

字段要求：

- `walletTxId`：wallet owner 产出的稳定原始交易主键，trading-core-service 必须按该字段做回滚幂等
- `userId`：归属用户 ID
- `chain`：链标识
- `token`：入金币种
- `txHash`：链上哈希
- `fromAddress`：来源地址
- `toAddress`：目标地址
- `amount`：已按 token decimals 归一化后的业务金额，统一保留 `8` 位小数；禁止直接传 raw amount
- `confirmations`：当前确认数
- `requiredConfirmations`：所需确认数
- `reversedAt`：回滚时间

分区键建议：

- `chain:txHash`

### 12.4 `falconx.trading.deposit.credited`

用途：

- `trading-core-service` 在完成业务入账后发布
- `identity-service` 消费后完成用户激活

推荐 payload：

```json
{
  "depositId": 1000001,
  "userId": 2000001,
  "accountId": 3000001,
  "chain": "ETH",
  "token": "USDT",
  "txHash": "0xabc123",
  "amount": "1500.00000000",
  "creditedAt": "2026-04-16T16:00:00Z"
}
```

字段要求：

- `depositId`：业务入金事实 ID
- `userId`：激活目标用户 ID
- `accountId`：入账账户 ID
- `chain`：链标识
- `token`：入金代币
- `txHash`：链上哈希
- `amount`：入账金额
- `creditedAt`：入账时间

分区键建议：

- `userId`

### 12.5 `falconx.market.price.tick`

用途：

- `market-service` 在标准化一条实时报价后发布
- `trading-core-service` 消费后驱动高频报价链路

运行时 headers：

- Kafka key：`symbol`
- `X-Event-Id`：事件唯一标识
- `X-Event-Type`：固定为 `market.price.tick`
- `X-Event-Source`：固定为 `falconx-market-service`
- `X-Trace-Id`：沿当前链路透传

运行时 body：

```json
{
  "symbol": "EURUSD",
  "bid": "1.08321",
  "ask": "1.08331",
  "mid": "1.08326",
  "mark": "1.08326",
  "ts": 1776676530.123,
  "source": "TIINGO_FOREX",
  "stale": false
}
```

字段要求：

- `symbol`：平台内部标准品种
- `bid`：卖出参考价
- `ask`：买入参考价
- `mid`：`(bid + ask) / 2`
- `mark`：兼容标记价字段；交易侧做逐仓估值、TP/SL、强平与账户浮盈亏时，仍应按方向从 `bid / ask` 解析有效标记价
- `ts`：当前运行时按 Jackson 数值时间输出，语义为 Unix epoch seconds，可带小数秒；消费方不得把它强绑成字符串格式
- `source`：当前固定为 `TIINGO_FOREX`
- `stale`：报价是否已超时

分区键建议：

- `symbol`
