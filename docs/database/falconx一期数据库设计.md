# FalconX v1 数据库设计

## 1. 定位

本文件服务于 FalconX v1 的最终架构：

`GODSA (Gateway-Orchestrated Domain Core Services Architecture)`

数据库策略固定为：

- `Database per Service`
- 一期先使用 `1` 个 MySQL 实例
- `1` 个 ClickHouse 实例
- 每个服务拥有独立 MySQL schema

## 2. 一期物理布局

推荐在同一 MySQL 实例内创建 4 个 schema：

- `falconx_identity`
- `falconx_market`
- `falconx_trading`
- `falconx_wallet`

同时为 `market-service` 创建 ClickHouse database：

- `falconx_market_analytics`

说明：

- `gateway` 不持有业务数据库
- `identity / market / trading-core / wallet` 各自只写自己的 schema
- 服务之间不共享业务表
- K 线和报价历史由 `market-service` 写 ClickHouse，不写 MySQL

## 3. 设计原则

- 一期只支持真实盘
- 一期不做分表
- 所有金额字段统一使用 `DECIMAL(24,8)`
- 高频 tick 不直接落主交易库
- 资金流水必须可重放
- 不依赖物理外键维持跨服务一致性
- 跨服务一致性依赖事件、幂等和状态机

## 4. schema 与 owner

### 4.1 `falconx_identity`

owner 服务：

- `falconx-identity-service`

核心表：

- `t_user`
- `t_refresh_token_session`
- `t_inbox`

职责：

- 邮箱密码账号
- 密码哈希
- Refresh Token 一次性会话
- 用户状态
- 激活时间

### 4.2 `falconx_market`

owner 服务：

- `falconx-market-service`

核心表：

- `t_symbol`
- `t_trading_hours`
- `t_trading_hours_exception`
- `t_trading_holiday`
- `t_swap_rate`
- `t_outbox`

职责：

- 品种元数据
- 交易时间周规则
- 交易时间例外规则
- 交易节假日规则
- 隔夜利息费率
- 市场事件发件箱

ClickHouse 核心表：

- `quote_tick`
- `kline`

说明：

- 最新价格以 Redis 为准
- 交易时间快照由 `market-service` 写 Redis，`trading-core-service` 只读 Redis 校验交易时段
- ClickHouse 保存报价历史和 K 线历史
- MySQL 只保存低频元数据和 Outbox

### 4.3 `falconx_trading`

owner 服务：

- `falconx-trading-core-service`

核心表：

- `t_account`
- `t_ledger`
- `t_deposit`
- `t_order`
- `t_position`
- `t_trade`
- `t_risk_exposure`
- `t_risk_config`
- `t_hedge_log`
- `t_liquidation_log`
- `t_outbox`
- `t_inbox`

职责：

- 账户
- 账本
- 入账后的业务入金事实
- 订单
- 持仓
- 成交
- B-book 净敞口
- 风控参数
- B-book 风险观测日志
- 强平日志

说明：

- 一期把 `trade + fund + risk` 放在同一事务边界内
- `wallet` 不直接写这些表

### 4.4 `falconx_wallet`

owner 服务：

- `falconx-wallet-service`

核心表：

- `t_wallet_address`
- `t_wallet_deposit_tx`
- `t_wallet_chain_cursor`

职责：

- 地址分配
- 原始链上交易记录
- 监听游标与确认推进

说明：

- `wallet` 管原始链事实
- `trading-core` 管最终入账事实

跨服务唯一标识冻结：

- `falconx_wallet.t_wallet_deposit_tx.id` 是 wallet owner 产出的稳定原始交易主键
- 该主键在跨服务协作中统一命名为 `walletTxId`
- `falconx_trading.t_deposit.wallet_tx_id` 作为 wallet -> trading 的唯一业务幂等键
- `chain + txHash` 仅保留为链上检索字段，不再作为跨服务唯一业务主键
- 原始链事实若存在“一笔交易多条 transfer”的场景，wallet owner 必须通过 `log_index` 拆分事实，再由 `walletTxId` 对外输出稳定标识

金额口径冻结：

- `falconx_wallet.t_wallet_deposit_tx.amount` 存储的是**已按 token decimals 归一化后的业务金额**，不是链上最小单位 raw amount
- `falconx_trading.t_deposit.amount` 同样存储归一化后的业务金额，并与 `wallet-service` 事件 payload 中的 `amount` 保持同一语义
- 一期金额统一保留到 `DECIMAL(24,8)` / `DECIMAL(20,8)` 对应的 `8` 位小数，不在 wallet 层额外裁剪成 `2` 位
- 链上最小单位（如 `wei`、ERC20 的 `10^decimals`、SPL token 最小单位）只允许存在于监听器 / SDK 解析阶段，进入 owner 持久化、事件 payload 和业务入账前必须先完成显式换算
- 若 token decimals 未知、token metadata 未冻结或无法可靠换算，listener 必须 fail-closed：不生成 `ObservedDepositTransaction`，只记录告警日志，等待后续补齐 metadata 后再接入

原始入金事实冻结：

- `falconx_wallet.t_wallet_deposit_tx` 必须预留：
  - `token_contract_address`
  - `log_index`
- 原始链事实唯一键冻结为：
  - `(chain, tx_hash, log_index)`
- 原生币转账统一使用：
  - `token_contract_address = null`
  - `log_index = 0`
- ERC20 / SPL / TRC20 等日志型转账必须以真实日志索引作为 `log_index`

业务入账事实冻结：

- `falconx_trading.t_deposit` 的业务幂等键统一切换为：
  - `wallet_tx_id`
- `chain + tx_hash` 仍保留为检索字段和审计字段，不再承担唯一约束语义
- `trading-core-service` 必须按 `walletTxId` 做 confirmed / reversed 的业务入账与回滚幂等

## 5. 核心数据边界

### 5.1 用户激活

`identity-service` 只维护用户状态。

激活动作来源于：

- `wallet-service` 发出入金确认事件
- `trading-core-service` 完成入账
- 再由 `identity-service` 根据业务规则更新 `t_user.activated_at` 和 `status`

如果一期要进一步简化，也可以由 `gateway` 发起显式激活 API，但不允许跨库直写。

### 5.2 入金边界

原始链交易归 `wallet`：

- 地址
- 交易哈希
- 区块高度
- 确认数
- 原始状态

业务入账归 `trading-core`：

- `t_account`
- `t_ledger`
- `t_deposit`

这两层不要混在一起。

### 5.3 风控边界

一期不再有独立 `risk-service` 数据库。

风险参数和强平日志都归：

- `falconx_trading`

原因：

- 风控与订单、持仓、保证金是强一致链路
- 一期没必要先拆成独立服务和独立库

## 6. 核心账户语义

`falconx_trading.t_account` 语义冻结如下：

- `balance`：账户总现金余额
- `frozen`：已预留未确认金额
- `margin_used`：已占用保证金
- `available = balance - frozen - margin_used`

与之配套的 `falconx_trading.t_ledger` 必须同时记录：

- `balance_before / balance_after`
- `frozen_before / frozen_after`
- `margin_used_before / margin_used_after`

这样账本才能支持完整回放与审计，避免只回放余额而无法解释冻结和保证金占用变化。

动作规则：

- 预留保证金：`frozen += margin`
- 成交确认：`frozen -= margin`，`margin_used += margin`
- 扣手续费：`balance -= fee`
- 平仓结算：`margin_used -= margin`，`balance += pnl`
- 取消订单：`frozen -= margin`

禁止再次出现：

- 同时扣 `balance` 又增加 `frozen` 的双扣模型

## 6.1 持仓语义补充

`falconx_trading.t_position` 的正式产品规则已在 `SPEC-TRD-001` 冻结为“净持仓主事实表”，口径如下：

- 单用户单 `symbol` 任一时刻最多只允许存在一条 `OPEN` 净持仓
- 不允许多张同方向独立逐仓仓位
- 不允许同时持有相反方向仓位
- 净持仓模型下保留“每用户每 `symbol` 一个稳定 `positionId`”
- 同向下单视为加仓
- 反向下单且数量小于当前净仓时，视为减仓
- 反向下单且数量等于当前净仓时，当前净仓减为 `0` 并进入终态
- 反向下单且数量大于当前净仓时，先把当前净仓减为 `0`，剩余数量翻为反向净仓
- 后续正式实现中，加仓、减仓、穿零翻仓都在同一稳定 `positionId` 上演进，不再额外创建新的独立持仓主键
- `unrealized_pnl` 不持久化，不写 MySQL
- 未实现盈亏由查询层基于 `entry_price + 当前 mark_price` 动态计算
- `take_profit_price / stop_loss_price` 作为净持仓级触发价持久化到 `t_position`

字段语义：

- `t_position` 表达“当前净持仓状态”，承载当前方向、净数量、均价、保证金、杠杆、TP/SL、强平价和状态
- `t_trade` 表达每次开仓、减仓、平仓、强平的离散成交事实，不能被 `t_position` 替代
- `t_ledger` 表达保证金、手续费、已实现盈亏、强平损益、Swap 等账务事实，不能被 `t_position` 替代

说明：

- 上述规则是正式产品冻结口径，不等于当前仓库已完成实现切换
- 当前仓库仍存在按独立 `positionId` 管理 `OPEN` 持仓的历史实现事实；后续若进入实现阶段，必须同步调整接口契约、状态机迁移、风控计算和快照索引语义

## 6.2 净敞口语义补充

`falconx_trading.t_risk_exposure` 代表 B-book 平台对每个品种的实时净暴露：

- `total_long_qty`
- `total_short_qty`
- `net_exposure = total_long_qty - total_short_qty`
- `net_exposure_usd = net_exposure * mark_price`

要求：

- 开仓、平仓、强平必须与订单/持仓写入处于同一本地事务内更新该表
- 报价刷新到 fresh tick 时，只重算 `net_exposure_usd`，不改动数量口径净敞口
- 阈值判断读取 `falconx_trading.t_risk_config.hedge_threshold_usd`
- 超阈值与恢复到阈值内都要写入 `falconx_trading.t_hedge_log`
- 超阈值时额外发布服务内 Spring Event stub，供后续告警或真实对冲出口接入；`t_hedge_log` 仍是 owner 审计事实
- 当前阶段只落地“Spring Event stub + 告警日志 + 审计留痕”的可观测性基础，不自动执行 A-book 对冲，也不新增 Kafka topic 契约
- 该表用于实时风控视图，不替代订单与持仓明细事实

## 6.3 负净值保护

`falconx_trading.t_liquidation_log` 增加：

- `platform_covered_loss`

含义：

- 强平后若亏损超过账户可承受范围，账户余额归零
- 超出的兜底金额记入 `platform_covered_loss`
- 一期不允许把用户账户打成负余额

## 6.4 交易时间管理方案 B

交易时间管理固定采用方案 B，并补齐节假日规则。

固定结构：

- `t_symbol.market_code`
- `t_trading_hours`
- `t_trading_hours_exception`
- `t_trading_holiday`

结构语义：

- `category`：固定为 `1=CRYPTO,2=FX,3=METAL,4=INDEX,5=ENERGY`
- `market_code`：把品种映射到更细粒度的市场维度，固定为 `CRYPTO / FX / METAL / US_INDEX / EU_INDEX / UK_INDEX / HK_INDEX / USD_INDEX / ENERGY`
- `t_trading_hours`：定义按星期重复生效的基础交易时段，可支持多段 session
- `t_trading_hours_exception`：面向单个 `symbol` 的人工覆盖规则，优先级最高
- `t_trading_holiday`：面向 `market_code` 的节假日规则，用于休市、提前收盘和晚开盘

运行时优先级固定为：

1. `t_trading_hours_exception`
2. `t_trading_holiday`
3. `t_trading_hours`

运行时约束：

- `market-service` owner 持久化这些规则
- `market-service` 的 `t_symbol` 初始化数据已扩展为基于 Tiingo `fx` 源 `10s` 实际采样得到的 `128` 个可见 symbol，再叠加平台现有 `BTCUSDT / ETHUSDT`
- `2026-04-18` 新增一次性 `Tiingo crypto` symbol 补录：在 `10s` 采样窗口内过滤得到 `392` 个候选，
  其中 `390` 个为新增，统一写入 `t_symbol.status=2 suspended`；这些记录只作为品种元数据储备，
  不进入当前 `status=1` 的运行时交易白名单
- `market-service` 启动时把每个 `symbol` 的交易时间快照写入 Redis
- `market-service` 运行时白名单只依赖 `t_symbol.status=1` 的 owner 数据，不再按 `market_code=FX` 做额外硬编码过滤
- `trading-core-service` 下单只读 Redis 快照，不跨服务读取 `falconx_market`
- 新开仓在非交易时段返回 `40008: Symbol Trading Suspended`
- 已存在持仓的手动平仓不受交易时间校验阻塞，但仍必须读取 Redis 最新价并执行 stale / 缺价校验

## 6.5 Stage 6A 已存在的平仓终态字段与逐仓预留字段

`2026-04-19` 通过 `V5__manual_close_and_margin_mode.sql` 已真实落地下列字段：

- `t_position.margin_mode`
- `t_position.close_price`
- `t_position.close_reason`
- `t_position.realized_pnl`
- `t_position.closed_at`
- `t_trade.trade_type`

当前真实写入语义：

- `margin_mode` 当前只写 `2=isolated`
- 手动平仓成功时写 `close_price / close_reason=1(manual) / realized_pnl / closed_at`
- TP/SL 自动触发成功时写 `close_price / close_reason=2(take_profit) 或 3(stop_loss) / realized_pnl / closed_at`
- 强平成功时写 `close_price / close_reason=4(liquidation) / realized_pnl / closed_at`，并额外写 `t_liquidation_log`
- 开仓成交写 `trade_type=1(open)`；手动平仓与 TP/SL 写 `trade_type=2(close)`；强平写 `trade_type=3(liquidation)`
- 手动平仓与 TP/SL 成功时同事务写 `t_outbox.event_type=trading.position.closed`；强平成功时写 `t_outbox.event_type=trading.liquidation.executed`
- 上述事实用于当前 `Stage 6A` 交易链路核对，不等于 `Stage 7 / 7A` 已进入验收完成
- 后续 `SPEC-TRD-001` 又冻结了“单用户单 `symbol` 单净持仓 + 稳定 `positionId`”规则；因此本节描述的是当前仓库已存在字段与历史写入事实，不代表净持仓模型已经实现落地

`2026-04-22` 已在 `Stage 7A` 首批逐仓增强子范围内真实补齐：

- `t_ledger.biz_type=10 isolated_margin_supplement`
- `POST /api/v1/trading/positions/{positionId}/margin` 对应的 owner 账务事实
- 追加保证金后的 `t_position.margin / liquidation_price` 同事务更新

这批字段和账务语义的定位如下：

- 一期当前运行时仍按“只支持 `ISOLATED`，已存在手动平仓、TP/SL、强平终态持久化事实，并已完成首批追加保证金与强平价重算能力，但未进入 `CROSS` 模式”执行
- `margin_mode` 只作为后续逐仓完善与全仓预留的扩展入口
- `close_price / close_reason / realized_pnl / closed_at` 用于把手动平仓、TP/SL、强平的终态信息持久化到 `t_position`
- `trade_type` 用于区分 `OPEN / CLOSE / LIQUIDATION`
- `biz_type=10 isolated_margin_supplement` 用于追加保证金账本记录
- 追加保证金成功时：
  - `t_account.balance / frozen` 不变
  - `t_account.margin_used += amount`
  - `t_position.margin += amount`
  - `t_position.liquidation_price` 按最新 `margin` 重算
  - 不新增 Kafka topic / payload，也不写 Outbox 业务事件

文档约束：

- 不得把上述事实夸大为“`Stage 7A` 已整体验收完成”或“`CROSS` 已实现”
- 不得把当前手动平仓、TP/SL、强平的实现事实直接表述为 `Stage 7 / 7A` 已验收
- 后续继续扩展时，必须沿现有 Flyway 版本继续新增 migration，不能改历史 migration

## 7. 索引原则

索引只围绕一期真实查询路径建立：

- `identity`：邮箱登录、状态筛选
- `market`：symbol 查询，历史报价和 K 线按 ClickHouse 时间范围查询
- `trading`：账户查询、账本时间线、订单幂等、持仓扫描、强平审计
- `wallet`：按地址查用户、按链和确认状态轮询交易、按游标续扫

## 8. ClickHouse 写入策略

一期固定采用下面的市场数据持久化策略：

- 每个 tick 写 Redis 最新价
- 每个 tick 异步批量写 ClickHouse `quote_tick`
- 当前未收盘 K 线只保留在内存或 Redis 聚合态
- K 线收盘时写一条最终 K 线到 ClickHouse `kline`

这样做的目的：

- 把高频写压力从 MySQL 转移到 ClickHouse
- 保持查询历史报价和 K 线的能力
- 避免 MySQL 承担每 tick 更新 K 线的写压力

## 9. SQL 文档说明

当前仓库中的 SQL 文档位于：

- [V1__init_schema.sql](../sql/V1__init_schema.sql)
- [V2__seed_symbols.sql](../sql/V2__seed_symbols.sql)
- [V3__seed_tiingo_crypto_symbols.sql](../sql/V3__seed_tiingo_crypto_symbols.sql)
- [CH_V1__market_analytics.sql](../sql/CH_V1__market_analytics.sql)

它们现在表示的是：

- FalconX v1 的数据库蓝图文档
- 与当前 owner 服务中的运行时 migration 保持一致

当前运行时 migration 目录：

- `falconx-identity-service/src/main/resources/db/migration/`
- `falconx-market-service/src/main/resources/db/migration/`
- `falconx-trading-core-service/src/main/resources/db/migration/`
- `falconx-wallet-service/src/main/resources/db/migration/`

## 10. Flyway 接入约定

Flyway 已在 `Stage 5` 正式接入 owner 服务。

当前规则：

- 每个 owner 服务只管理自己的 migration
- migration 只允许变更本服务 owner schema
- 文档 SQL 蓝图与运行时 migration 必须保持一致

目录约定：

- `falconx-identity-service/src/main/resources/db/migration/`
- `falconx-market-service/src/main/resources/db/migration/`
- `falconx-trading-core-service/src/main/resources/db/migration/`
- `falconx-wallet-service/src/main/resources/db/migration/`

命名规则：

- 与当前蓝图保持一致，使用 `V{版本号}__{描述}.sql`
- MySQL 与 ClickHouse migration 分开维护，不混在同一目录
- ClickHouse migration 由 `market-service` 自己管理
## 11. 当前结论

FalconX v1 的数据库设计已经从“单库里按模块 owner 划分表”升级为：

- `MySQL + ClickHouse`
- `MySQL 多 schema`
- `每服务独立 owner`

其中最关键的结构性决定是：

- `market` 拥有报价历史与 `K线`
- `wallet` 拥有原始链交易
- `trading-core` 拥有账户、账本、订单、持仓、风控和最终入账事实
- `identity` 拥有用户主表、收件箱以及 Refresh Token 一次性会话持久化
