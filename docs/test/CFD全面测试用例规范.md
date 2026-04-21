# FalconX v1 CFD 全面测试用例规范

> 版本：v1.0  
> 创建日期：2026-04-18  
> 文档状态：正式规范  
> 适用阶段：Stage 5 已完成基础，Stage 6A/6B/7 验收基准  
> 规范来源：架构方案、数据库设计、状态机规范、事务与幂等规范、安全规范、Kafka 事件规范、接口文档

---

## 1. 概览

### 1.1 文档目的

本文件是 FalconX v1 的统一测试用例规范，作为：

- 开发阶段的测试编写标准
- 阶段验收的审计凭证
- 后续回归测试的检查清单

所有标记为「验收必须」的用例，在对应阶段交付时必须全部通过，不允许以"后续补齐"为由跳过。

### 1.2 CFD 业务范围

FalconX v1 以差价合约（CFD）为核心产品。CFD 涉及：

- **杠杆交易**：以保证金控制更大名义价值，放大盈亏
- **双向持仓**：支持做多（BUY）和做空（SELL）
- **保证金管理**：开仓冻结保证金，强平线保护平台
- **浮盈浮亏（unrealizedPnl）**：随行情实时变化，不持久化
- **止盈止损（TP/SL）**：持仓级自动触发平仓
- **强制平仓（Liquidation）**：亏损达到强平线时系统强制平仓
- **隔夜利息（Swap）**：持仓跨过 rollover 时间点收取或支付费用
- **净敞口（Risk Exposure）**：平台 B-book 对冲视图

### 1.3 测试分层

| 层级 | 简称 | 说明 |
|------|------|------|
| 单元测试 | UT | 纯内存，不依赖外部服务 |
| 集成测试 | IT | 依赖真实 MySQL/Redis/Kafka，使用 Testcontainers |
| 端到端测试 | E2E | 全服务链路，通过 gateway 入口触发 |
| 安全专项 | SEC | 认证、鉴权、边界攻击 |
| 幂等/事务专项 | TXN | 重复请求、并发竞争、事务回滚 |
| 性能边界 | PERF | 限流阈值、降级阈值 |

### 1.4 通用约定

- 所有测试必须使用隔离测试库，不允许污染共享数据
- 金额字段精度统一为 `DECIMAL(24,8)`，测试数据必须符合此约束
- 所有错误码必须对照规范，不允许自行扩展
- 测试结论中写的"通过"必须对应真实验证，不允许只写命令名

---

## 2. 身份认证服务测试（identity-service）

### 2.1 用户注册

#### TC-AUTH-001 正常注册

- **类型**：IT
- **所属模块**：`falconx-identity-service`
- **验收阶段**：Stage 3A / Stage 7
- **前置条件**：数据库已初始化，邮箱不存在

**输入**：
```json
POST /api/v1/auth/register
{
  "email": "test-001@example.com",
  "password": "Passw0rd!"
}
```

**预期结果**：
- HTTP 200，业务码 `0`
- 返回 `userId`、`uid`（格式 `U\d{8}`）、`email`、`status=PENDING_DEPOSIT`
- 响应头包含 `X-Trace-Id`
- `t_user` 新增一条记录，密码字段为 bcrypt 哈希值（不含明文）
- `t_user.status = PENDING_DEPOSIT`

**验证点**：
- [ ] HTTP 状态码 200
- [ ] `code = "0"`
- [ ] `data.status = "PENDING_DEPOSIT"`
- [ ] `data.uid` 符合 `U\d{8}` 格式
- [ ] 响应头有 `X-Trace-Id`
- [ ] 数据库 `t_user.password` 不等于明文密码
- [ ] 数据库 `t_user.password` 可通过 bcrypt 校验

---

#### TC-AUTH-002 重复邮箱注册

- **类型**：IT
- **验收阶段**：Stage 3A / Stage 7

**输入**：同一邮箱发送两次注册请求

**预期结果**：
- 第一次：业务码 `0`，成功注册
- 第二次：业务码 `10008`，`message = "User Already Exists"`
- `t_user` 只有一条对应记录

**验证点**：
- [ ] 第二次返回 `10008`
- [ ] 数据库中邮箱记录唯一

---

#### TC-AUTH-003 密码不符合长度规范

- **类型**：UT
- **验收阶段**：Stage 3A

**输入**：
- `password = "abc"` （不足 8 字符）
- `password = 'a' * 65` （超过 64 字符）

**预期结果**：两种情况均返回参数校验失败，业务码 `90004`

**验证点**：
- [ ] 短密码返回 `90004`
- [ ] 长密码返回 `90004`
- [ ] 数据库无新增记录

---

#### TC-AUTH-004 邮箱格式非法

- **类型**：UT
- **验收阶段**：Stage 3A

**输入**：`email = "not-an-email"`

**预期结果**：业务码 `90004`

---

### 2.2 用户登录

#### TC-AUTH-010 ACTIVE 用户正常登录

- **类型**：IT
- **验收阶段**：Stage 3A / Stage 7

**前置条件**：存在一个 `ACTIVE` 状态的用户

**输入**：
```json
POST /api/v1/auth/login
{
  "email": "alice@example.com",
  "password": "Passw0rd!"
}
```

**预期结果**：
- 业务码 `0`
- 返回 `accessToken`（RSA JWT，算法 RS256）
- 返回 `refreshToken`（UUID）
- `accessTokenExpiresIn = 900`
- `refreshTokenExpiresIn = 259200`
- `userStatus = "ACTIVE"`
- `t_refresh_token_session` 有新记录

**验证点**：
- [ ] `code = "0"`
- [ ] `accessToken` 可用公钥验证签名
- [ ] JWT payload 包含 `sub / uid / email / status / iat / exp / jti`
- [ ] `exp - iat = 900`
- [ ] Refresh Token 落库
- [ ] Refresh Token 不在响应日志中明文输出

---

#### TC-AUTH-011 PENDING_DEPOSIT 用户登录被拒

- **类型**：IT
- **验收阶段**：Stage 3A

**前置条件**：用户状态为 `PENDING_DEPOSIT`

**预期结果**：业务码 `10011`，`message = "User Not Activated"`

**验证点**：
- [ ] 返回 `10011`
- [ ] 无 Token 签发

---

#### TC-AUTH-012 FROZEN 用户登录被拒

- **类型**：IT
- **验收阶段**：Stage 3A

**预期结果**：业务码 `10007`，`message = "User Frozen"`

---

#### TC-AUTH-013 BANNED 用户登录被拒

- **类型**：IT
- **验收阶段**：Stage 3A

**预期结果**：业务码 `10002`，`message = "User Banned"`

---

#### TC-AUTH-014 密码错误登录

- **类型**：IT
- **验收阶段**：Stage 3A

**预期结果**：业务码 `10005`，无 Token 签发

---

#### TC-AUTH-015 连续 5 次密码错误触发锁定

- **类型**：IT
- **验收阶段**：Stage 6B

**操作**：同一 IP 连续发送 5 次密码错误请求，再发第 6 次

**预期结果**：
- 前 5 次返回密码错误
- 第 6 次返回 `10003`，触发锁定（15 分钟）

**验证点**：
- [ ] Redis 中存在 `falconx:auth:login:fail:{ip}` 计数键
- [ ] 第 6 次调用返回 `10003`

---

### 2.3 Token 刷新

#### TC-AUTH-020 正常刷新

- **类型**：IT
- **验收阶段**：Stage 3A

**前置条件**：已登录，持有有效 `refreshToken`

**预期结果**：
- 业务码 `0`
- 返回新的 `accessToken` 和新的 `refreshToken`
- 旧 `refreshToken` 从 `t_refresh_token_session` 中标记失效
- `t_refresh_token_session` 新增一条新会话记录

**验证点**：
- [ ] 新旧 `refreshToken` 不同
- [ ] 旧 `refreshToken` 再次刷新返回 `10006`
- [ ] 新 `accessToken` 有效期重置为 900s

---

#### TC-AUTH-021 旧 Refresh Token 二次使用

- **类型**：IT
- **验收阶段**：Stage 3A

**操作**：使用已消费的 `refreshToken` 再次刷新

**预期结果**：业务码 `10006`，`message = "Refresh Token Invalid"`

---

#### TC-AUTH-022 已过期 Refresh Token

- **类型**：IT
- **验收阶段**：Stage 3A

**前置条件**：`refreshToken` 过期时间早于当前时间

**预期结果**：业务码 `10006`

---

### 2.4 用户状态机

#### TC-AUTH-030 状态迁移：PENDING_DEPOSIT → ACTIVE

- **类型**：IT（含 Kafka 消费）
- **验收阶段**：Stage 5 / Stage 7

**操作**：`identity-service` 消费 `falconx.trading.deposit.credited` 事件

**前置条件**：
- 用户状态为 `PENDING_DEPOSIT`
- 事件包含有效 `userId` 和 `eventId`

**预期结果**：
- `t_user.status` 变更为 `ACTIVE`
- `t_user.activated_at` 写入当前时间
- `t_inbox` 写入 `eventId` 去重记录

**验证点**：
- [ ] `t_user.status = "ACTIVE"`
- [ ] `t_user.activated_at != null`
- [ ] `t_inbox` 有对应 `eventId` 记录

---

#### TC-AUTH-031 同一事件重复消费（幂等）

- **类型**：IT
- **验收阶段**：Stage 5

**操作**：相同 `eventId` 的 `deposit.credited` 事件发送两次

**预期结果**：
- 第一次：正常激活，`t_user.status = ACTIVE`
- 第二次：幂等跳过，`t_user.status` 不变，不报错
- `t_inbox` 只有一条记录

**验证点**：
- [ ] 无重复激活
- [ ] 无异常抛出
- [ ] `t_inbox` 记录唯一

---

#### TC-AUTH-032 FROZEN/BANNED 用户收到激活事件不迁移

- **类型**：UT
- **验收阶段**：Stage 3A

**前置条件**：用户状态分别为 `FROZEN` / `BANNED`

**预期结果**：状态不变，不报错

---

---

## 3. 市场数据服务测试（market-service）

### 3.1 行情接入与标准化

#### TC-MKT-001 Tiingo 报价正常接入并标准化

- **类型**：IT（Stub Provider）
- **验收阶段**：Stage 2A / Stage 6A

**操作**：向 market-service 注入一条模拟 Tiingo 报文

**输入数据**（模拟 Tiingo `fx` WebSocket 报文）：
```json
{
  "service": "fx",
  "messageType": "A",
  "data": ["Q", "eurusd", "2026-04-18T10:00:00.000000+00:00",
           1000000.0, 1.08000, 1.08005, 1000000.0, 1.08010]
}
```

**预期结果**：
- 标准化 symbol 为 `EURUSD`（大写）
- `bid = 1.08000`，`ask = 1.08010`
- `mid = (bid + ask) / 2 = 1.08005`
- `mark` 当前仍作为市场层兼容字段与 `mid` 对齐；交易侧有效标记价必须按 `BUY -> bid / SELL -> ask`
- Redis key `falconx:market:price:EURUSD` 被写入
- ClickHouse `quote_tick` 有新记录
- `falconx.market.price.tick` Kafka 事件被发布

**验证点**：
- [ ] Redis 中 `bid / ask / mid / mark / ts / source / stale` 字段齐全
- [ ] Redis key TTL ≤ 10s
- [ ] ClickHouse 有对应记录
- [ ] Kafka 事件 `payload.symbol = "EURUSD"`

---

#### TC-MKT-002 无效报文不进入标准报价链路

- **类型**：UT
- **验收阶段**：Stage 2A

**输入**：
- `messageType = "H"`（心跳帧）
- `messageType = "I"`（信息帧）
- `messageType = "E"`（错误帧）
- 缺少 `bid / ask` 字段的 `messageType = "A"` 帧

**预期结果**：
- 上述所有情况均不写 Redis，不写 ClickHouse，不发 Kafka
- 不抛异常，只输出对应调试日志

**验证点**：
- [ ] Redis 无写入
- [ ] 无 Kafka 消息投递
- [ ] 无未捕获异常

---

#### TC-MKT-003 品种不在 t_symbol 白名单内时过滤

- **类型**：IT
- **验收阶段**：Stage 6A

**前置条件**：`t_symbol` 表中该 symbol 状态为 `status=0`（不存在）或 `status=2`（suspended）

**输入**：注入该品种的 Tiingo 报文

**预期结果**：
- 不写 Redis
- 不写 ClickHouse
- 不发 Kafka

**验证点**：
- [ ] Redis 无该品种 key
- [ ] ClickHouse 无该品种记录

---

#### TC-MKT-004 白名单支持运行时热刷新

- **类型**：IT
- **验收阶段**：Stage 6A

**操作**：
1. 确认某 symbol 当前 `status=2`，注入报文 → 无 Redis 写入
2. 将该 symbol 的 `t_symbol.status` 改为 `1`，等待热刷新周期
3. 再次注入报文

**预期结果**：步骤 3 后 Redis 有写入，无需重启服务

**验证点**：
- [ ] 数据库更新后无需重启
- [ ] 刷新周期内生效

---

#### TC-MKT-005 Tiingo 外部真源自动化验证

- **类型**：IT（External Real Source）
- **验收阶段**：Stage 6A

**前置条件**：
- 本地 `mysql / redis / kafka / clickhouse` 已启动
- 显式设置 `FALCONX_MARKET_TIINGO_EXTERNAL_TEST_ENABLED=true`
- 显式设置有效 `FALCONX_MARKET_TIINGO_API_KEY`
- 若所在网络存在 TLS inspection / 自签根证书，已配置 `falconx.market.tiingo.trust-store-location / password / type`

**操作**：执行 `MarketTiingoExternalSourceAutomationIntegrationTests`

**预期结果**：
- 日志出现 `market.tiingo.provider.connected`
- 日志出现 `market.tiingo.provider.subscription.confirmed`
- 在 `EURUSD / USDJPY / XAUUSD` 等跟踪品种上观察到持续收流
- 至少一条真源报价进入 Redis、ClickHouse 与 `falconx.market.price.tick`
- 把某个已收流 symbol 从 `status=1` 切到 `status=2` 并触发白名单刷新后，该 symbol 在读取时最终变为 `stale=true`

**验证点**：
- [ ] `connected / subscription.confirmed` 日志存在
- [ ] Kafka 中收到真源 `market.price.tick`
- [ ] Redis / ClickHouse 中可查到对应真源报价
- [ ] `stale` 按读取时动态转为 `true`

---

#### TC-MKT-006 Tiingo 错误认证失败路径

- **类型**：IT（External Real Source）
- **验收阶段**：Stage 6A

**前置条件**：显式设置 `FALCONX_MARKET_TIINGO_EXTERNAL_TEST_ENABLED=true`

**操作**：执行 `JdkTiingoQuoteProviderExternalFailureIntegrationTests`

**预期结果**：
- 使用错误认证连接真实 Tiingo 端点后，不进入正常报价链路
- 日志中出现服务端拒绝、连接关闭或本地错误证据
- 日志中出现 `market.tiingo.provider.reconnect.scheduled`

**验证点**：
- [ ] 无真源报价进入消费回调
- [ ] 失败日志存在
- [ ] 已观察到重连调度日志

---

### 3.2 行情时效（Stale）判定

#### TC-MKT-010 新鲜行情（stale=false）

- **类型**：UT
- **验收阶段**：Stage 2A

**输入**：`quote.ts` 距当前时间不超过 5 秒

**预期结果**：`stale = false`

---

#### TC-MKT-011 过时行情（stale=true）

- **类型**：UT
- **验收阶段**：Stage 2A

**输入**：`quote.ts` 距当前时间超过 5 秒

**预期结果**：`stale = true`

**关键约束**：`stale` 必须按读取时动态计算，不能依赖写入时缓存的布尔值

---

#### TC-MKT-012 Redis key 过期后视为不可用

- **类型**：IT
- **验收阶段**：Stage 2A

**操作**：写入 Redis 后等待 10 秒（TTL 到期），再查询

**预期结果**：Redis key 不存在，读取方应按 `stale=true` 处理

---

### 3.3 K 线聚合

#### TC-MKT-020 K 线收盘写入 ClickHouse

- **类型**：IT
- **验收阶段**：Stage 6A

**操作**：在 1 分钟内连续注入多条报价，等待 1m K 线窗口关闭

**预期结果**：
- ClickHouse `kline` 表新增一条 `interval=1m` 的收盘 K 线
- K 线字段：`symbol / interval / open / high / low / close / volume / open_time / close_time`
- Kafka 发布 `falconx.market.kline.update` 事件

**验证点**：
- [ ] `open = 窗口首条 bid`
- [ ] `high = 窗口内最高 mark`
- [ ] `low = 窗口内最低 mark`
- [ ] `close = 窗口末条 mark`
- [ ] ClickHouse 有对应记录
- [ ] Kafka 事件已发布（通过 Outbox）

---

#### TC-MKT-021 多周期 K 线同步聚合

- **类型**：IT
- **验收阶段**：Stage 6A

**配置**：默认 `1m / 5m / 15m / 1h / 4h / 1d`

**操作**：注入跨多个周期的报价数据

**预期结果**：各周期收盘时分别写入 ClickHouse，互不干扰

---

### 3.4 交易时间管理

#### TC-MKT-030 交易时段内查询快照返回 OPEN

- **类型**：IT
- **验收阶段**：Stage 5

**前置条件**：`t_trading_hours` 配置周一至周五 00:00-24:00 UTC

**操作**：在配置时段内查询 Redis 交易时间快照

**预期结果**：当前时刻处于交易时段，`isOpen = true`

---

#### TC-MKT-031 节假日规则优先于周规则

- **类型**：IT
- **验收阶段**：Stage 5

**前置条件**：
- `t_trading_hours` 配置周五可交易
- `t_trading_holiday` 配置该日期全天休市

**预期结果**：节假日规则优先，`isOpen = false`

**验证点**：
- [ ] Redis 快照反映节假日状态
- [ ] `trading-core-service` 下单返回 `40008`

---

#### TC-MKT-032 例外规则优先于节假日规则

- **类型**：IT
- **验收阶段**：Stage 5

**前置条件**：
- `t_trading_holiday` 某日全天休市
- `t_trading_hours_exception` 对同一日期同一品种配置 10:00-12:00 可交易

**预期结果**：例外规则优先，10:00-12:00 内 `isOpen = true`

---

#### TC-MKT-033 跨午夜 session 判定

- **类型**：UT
- **验收阶段**：Stage 5

**输入**：session 为当天 22:00 到次日 06:00 UTC

**测试时间点**：
- `23:00` → `isOpen = true`
- `03:00 UTC 次日` → `isOpen = true`
- `07:00 UTC 次日` → `isOpen = false`

---

#### TC-MKT-034 交易时间快照 Redis TTL 为 25h

- **类型**：IT
- **验收阶段**：Stage 5

**验证点**：
- [ ] Redis 中交易时间快照 key 的 TTL 在 [88200, 90000] 秒范围内（25h ± 30min）
- [ ] 每日 UTC 00:00 触发全量刷新后 TTL 重置

---

### 3.5 报价查询接口

#### TC-MKT-040 查询已有报价

- **类型**：IT / E2E
- **验收阶段**：Stage 4

**输入**：
```http
GET /api/v1/market/quotes/EURUSD
Authorization: Bearer <validToken>
```

**预期结果**：
- 业务码 `0`
- 返回 `symbol / bid / ask / mid / mark / ts / source / stale`

---

#### TC-MKT-041 查询无报价品种

- **类型**：IT
- **验收阶段**：Stage 4

**前置条件**：Redis 中无该 symbol 的报价 key

**预期结果**：业务码 `30003`，`message = "Quote Not Available"`

---

#### TC-MKT-042 无 Token 查询报价被拒

- **类型**：E2E
- **验收阶段**：Stage 4

**预期结果**：业务码 `10001`，HTTP 401

---

### 3.5 北向行情 WebSocket

#### TC-MKT-043 订阅 `price.tick` 与 `kline` 后收到行情推送

- **类型**：IT
- **验收阶段**：Stage 6B

**前置条件**：
- 通过 `ws://{host}/ws/v1/market?token=<accessToken>` 完成握手
- 已订阅 `channels=["price.tick","kline.1m"]`、`symbols=["EURUSD"]`

**操作**：向 owner ingestion 路径连续写入同一 `symbol` 的 3 条标准报价，跨过同一个 `1m` K 线的收盘边界

**预期结果**：
- 先收到 `type=subscribed`
- 至少收到 1 条 `price.tick`
- 至少收到 1 条 `kline.1m isFinal=false`
- 收盘时额外收到 1 条 `kline.1m isFinal=true`

---

#### TC-MKT-044 WebSocket 订阅不存在的 symbol 返回错误帧

- **类型**：IT
- **验收阶段**：Stage 6B

**操作**：发送 `{"type":"subscribe","channels":["price.tick"],"symbols":["INVALID"]}`

**预期结果**：
- 返回 `type=error`
- `code = "30001"`
- `message` 包含 `symbol not found: INVALID`

---

#### TC-MKT-045 行情过期后只推送一次 stale 通知

- **类型**：IT
- **验收阶段**：Stage 6B

**前置条件**：
- 已订阅 `channels=["price.tick"]`、`symbols=["EURUSD"]`
- `falconx.market.stale.max-age` 和 `falconx.market.web-socket.stale-scan-interval` 已配置

**操作**：写入一条新鲜报价并等待其过期

**预期结果**：
- 收到一条 `type=price.tick` 且 `stale=true` 的通知
- stale 帧只针对同一条过期报价推送一次
- stale 帧不携带 `bid / ask / mid / mark`

---

#### TC-MKT-046 取消订阅后停止推送对应行情

- **类型**：IT
- **验收阶段**：Stage 6B

**前置条件**：
- 已完成 `channels=["price.tick"]`、`symbols=["EURUSD"]` 的订阅

**操作**：
1. 发送 `unsubscribe` 请求，取消 `EURUSD` 的 `price.tick`
2. 等待 `type=unsubscribed`
3. 再向 owner ingestion 路径写入一条 `EURUSD` 新鲜报价

**预期结果**：
- 服务端返回 `type=unsubscribed`
- 取消订阅后的观察窗口内，不再收到该 `symbol` 的 `price.tick` 推送

---

#### TC-MKT-047 WebSocket 应用层 ping/pong 与协议层心跳

- **类型**：IT
- **验收阶段**：Stage 6B

**前置条件**：
- 已建立 `ws://{host}/ws/v1/market?token=<accessToken>` 连接

**操作**：
1. 发送应用层 JSON 心跳 `{"type":"ping","ts":"..."}`
2. 等待服务端返回 `type=pong`
3. 在测试窗口内等待服务端协议层 Ping 帧

**预期结果**：
- 收到 `type=pong`，且 `ts` 与请求一致
- 收到至少 1 次服务端协议层 Ping 帧

---

#### TC-MKT-048 重连成功后必须重新订阅

- **类型**：IT
- **验收阶段**：Stage 6B

**前置条件**：
- 首次连接已完成 `channels=["price.tick"]`、`symbols=["EURUSD"]` 订阅

**操作**：
1. 关闭当前连接并重新建立新连接
2. 不发送 `subscribe`，先写入一条 `EURUSD` 报价
3. 验证未收到推送后，再重新发送 `subscribe`
4. 再写入一条 `EURUSD` 报价

**预期结果**：
- 新连接未重新订阅前，不会收到旧连接残留的 `price.tick`
- 新连接重新订阅后，可再次收到 `price.tick`

---

---

## 4. 交易核心服务测试（trading-core-service）

### 4.1 账户管理

#### TC-TRD-001 查询账户（自动初始化空账户）

- **类型**：IT / E2E
- **验收阶段**：Stage 3B

**前置条件**：用户已激活，无历史账户

**操作**：
```http
GET /api/v1/trading/accounts/me
Authorization: Bearer <validToken>
```

**预期结果**：
- 业务码 `0`
- `balance = 0`，`frozen = 0`，`marginUsed = 0`，`available = 0`
- `openPositions = []`

**验证点**：
- [ ] `available = balance - frozen - marginUsed`（精度约束）
- [ ] `t_account` 有该用户记录

---

#### TC-TRD-002 账户语义一致性验证

- **类型**：UT
- **验收阶段**：Stage 3B

**规则验证**：

| 动作 | 预期效果 |
|------|---------|
| 入金 1000 | `balance += 1000` |
| 开仓，保证金 100 | `frozen += 100`（下单时预留），成交后 `frozen -= 100`，`margin_used += 100` |
| 扣手续费 5 | `balance -= 5` |
| 平仓，盈利 50 | `margin_used -= 100`，`balance += 50` |
| 取消订单 | `frozen -= 100` |

**验证点**：
- [ ] 不允许双扣（同时 `balance` 减少又 `frozen` 增加）
- [ ] `available = balance - frozen - marginUsed` 始终成立

---

### 4.2 入金链路

#### TC-TRD-010 消费 wallet.deposit.confirmed 完成入账

- **类型**：IT
- **验收阶段**：Stage 5

**操作**：向 `falconx.wallet.deposit.confirmed` 发送入金确认事件

**事件 Payload**：
```json
{
  "userId": 10001,
  "chain": "ETH",
  "token": "USDT",
  "txHash": "0xabc123",
  "fromAddress": "0xsource",
  "toAddress": "0xplatform",
  "amount": "1500.00000000",
  "confirmations": 12,
  "requiredConfirmations": 12,
  "confirmedAt": "2026-04-18T10:00:00Z"
}
```

**预期结果**：
- `t_account.balance += 1500`
- `t_deposit` 新增一条 `CREDITED` 记录
- `t_ledger` 新增一条 `biz_type=1` 的账本记录，快照字段齐全
- `t_outbox` 写入 `falconx.trading.deposit.credited` 事件

**验证点**：
- [ ] `t_account.balance = 1500`
- [ ] `t_deposit.status = "CREDITED"`
- [ ] `t_ledger.balance_before` 和 `t_ledger.balance_after` 差值等于 1500
- [ ] `t_outbox` 有对应事件记录
- [ ] `t_inbox` 写入 `eventId` 去重记录

---

#### TC-TRD-011 重复消费 confirmed 事件（幂等）

- **类型**：IT
- **验收阶段**：Stage 5

**操作**：相同 `eventId` 的入金确认事件发送两次

**预期结果**：
- 第一次：正常入账
- 第二次：幂等跳过，`balance` 不变，不重复入账

**验证点**：
- [ ] `t_deposit` 只有一条记录
- [ ] `t_account.balance` 不翻倍

---

#### TC-TRD-012 消费 wallet.deposit.reversed 触发入金撤回

- **类型**：IT
- **验收阶段**：Stage 5 / Stage 7

**前置条件**：已存在对应 `txHash` 的 `CREDITED` 入金记录

**预期结果**：
- `t_deposit.status` 变更为 `REVERSED`
- 若未开仓，`t_account.balance` 回滚入金金额
- `t_ledger` 新增撤回账本记录

**验证点**：
- [ ] `t_deposit.status = "REVERSED"`
- [ ] `balance` 回滚正确

---

### 4.3 市价单下单（CFD 核心）

#### TC-TRD-020 正常市价开仓（多头）

- **类型**：IT / E2E
- **验收阶段**：Stage 4 / Stage 7
- **前置条件**：用户已激活，`balance = 2000 USDT`，BTCUSDT 行情新鲜，当前价 10000

**输入**：
```json
POST /api/v1/trading/orders/market
{
  "symbol": "BTCUSDT",
  "side": "BUY",
  "quantity": 1.0,
  "leverage": 10,
  "takeProfitPrice": 10500.0,
  "stopLossPrice": 9500.0,
  "clientOrderId": "test-order-001"
}
```

**预期结果**（假设成交价 = mark_price = 10000）：
- `orderStatus = "FILLED"`
- `margin = quantity * filledPrice / leverage = 1 * 10000 / 10 = 1000`
- `fee = quantity * filledPrice * feeRate`（取规范费率）
- `positionStatus = "OPEN"`
- `takeProfitPrice = 10500`，`stopLossPrice = 9500` 落库
- `t_account.margin_used = 1000`
- `t_account.balance = 2000 - fee`
- `t_ledger` 包含保证金确认和手续费扣除两条记录
- `t_risk_exposure.total_long_qty += 1`（同一事务内）

**验证点**：
- [ ] `orderStatus = "FILLED"`
- [ ] `positionStatus = "OPEN"`
- [ ] `margin = 1000`（10x 杠杆）
- [ ] `t_position.take_profit_price = 10500`
- [ ] `t_position.stop_loss_price = 9500`
- [ ] `t_account.margin_used = 1000`
- [ ] `t_risk_exposure` 已更新（与持仓同事务）
- [ ] `t_ledger` 有保证金快照（before / after）

---

#### TC-TRD-021 正常市价开仓（空头）

- **类型**：IT
- **验收阶段**：Stage 4

**输入**：`side = "SELL"`，其余相同

**预期结果**：
- `positionSide = "SELL"`
- `t_risk_exposure.total_short_qty += 1`

---

#### TC-TRD-022 余额不足被风控拒绝

- **类型**：IT
- **验收阶段**：Stage 3B

**前置条件**：`balance = 100 USDT`，下单需保证金 1000

**预期结果**：
- `orderStatus = "REJECTED"`
- `rejectionReason = "INSUFFICIENT_MARGIN"`（或等价错误码）
- 业务码 `40002`
- `t_account.balance` 不变

**验证点**：
- [ ] 账户无变化
- [ ] `t_order.status = "REJECTED"`
- [ ] `t_ledger` 无新增记录

---

#### TC-TRD-023 行情过时（STALE）被拒绝开仓

- **类型**：IT
- **验收阶段**：Stage 3B

**前置条件**：Redis 中该 symbol 报价时间戳早于当前 5 秒以上

**预期结果**：
- `orderStatus = "REJECTED"`
- `rejectionReason = "MARKET_QUOTE_STALE"`
- 业务码 `40002`

---

#### TC-TRD-024 非交易时段被拒绝开仓

- **类型**：IT
- **验收阶段**：Stage 5

**前置条件**：当前时间处于该 symbol 的休市时段（节假日或非交易时段）

**预期结果**：
- 业务码 `40008`
- `rejectionReason = "SYMBOL_TRADING_SUSPENDED"`
- `orderStatus = "REJECTED"`

---

#### TC-TRD-025 clientOrderId 幂等（重复下单）

- **类型**：IT
- **验收阶段**：Stage 4

**操作**：相同 `clientOrderId` 连续提交两次

**预期结果**：
- 第一次：正常成交
- 第二次：返回相同订单结果，`duplicate = true`
- 持仓只开一次

**验证点**：
- [ ] 第二次 `duplicate = true`
- [ ] `t_order` 只有一条记录
- [ ] `t_position` 只有一条 OPEN 记录

---

#### TC-TRD-026 杠杆倍数边界验证

- **类型**：UT
- **验收阶段**：Stage 3B

**输入**：`leverage = 0`，`leverage = 1001`（超出规范上限）

**预期结果**：参数校验失败，业务码 `90004` 或 `40001`

---

#### TC-TRD-027 FROZEN 用户下单被网关拒绝

- **类型**：E2E
- **验收阶段**：Stage 4

**前置条件**：用户状态为 `FROZEN`

**预期结果**：Gateway 拦截，业务码 `10007`，trading-core-service 不收到请求

---

### 4.4 保证金计算（CFD 核心）

#### TC-TRD-030 保证金计算公式验证

- **类型**：UT
- **验收阶段**：Stage 3B

**公式**：`margin = quantity × filledPrice / leverage`

**测试数据组**：

| quantity | filledPrice | leverage | 预期 margin |
|----------|-------------|----------|------------|
| 1.0 | 10000 | 10 | 1000.00000000 |
| 0.5 | 50000 | 100 | 250.00000000 |
| 2.0 | 1.08 | 20 | 0.10800000 |
| 0.001 | 10000 | 5 | 2.00000000 |

**验证点**：
- [ ] 所有结果精度为 8 位小数
- [ ] 使用 `BigDecimal` 计算，不允许浮点运算

---

#### TC-TRD-031 手续费计算

- **类型**：UT
- **验收阶段**：Stage 3B

**公式**：`fee = quantity × filledPrice × feeRate`

**验证点**：
- [ ] 费率从 `t_risk_config` 读取，不硬编码
- [ ] `fee` 扣减体现在 `t_ledger` 中

---

#### TC-TRD-032 强平价格计算（多头）

- **类型**：UT
- **验收阶段**：Stage 3B

**多头强平价公式**：
```
liquidationPrice = entryPrice × (1 - 1/leverage + maintenanceMarginRate)
```

**空头强平价公式**：
```
liquidationPrice = entryPrice × (1 + 1/leverage - maintenanceMarginRate)
```

**测试数据**：

| side | entryPrice | leverage | maintenanceRate | 预期强平价 |
|------|-----------|----------|----------------|-----------|
| BUY | 10000 | 10 | 0.005 | 9050.00 |
| SELL | 10000 | 10 | 0.005 | 10950.00 |

**验证点**：
- [ ] 结果精度 8 位小数
- [ ] 多头强平价 < 入场价
- [ ] 空头强平价 > 入场价

---

### 4.5 浮盈浮亏（unrealizedPnl）

#### TC-TRD-040 多头浮盈计算

- **类型**：UT
- **验收阶段**：Stage 3B / Stage 7

**公式**：`unrealizedPnl = (effectiveMarkPrice - entryPrice) × quantity`（多头）

说明：`effectiveMarkPrice` 为交易侧有效标记价，`BUY -> bid`，`SELL -> ask`

**测试数据**：

| side | entryPrice | markPrice | quantity | 预期 PnL |
|------|-----------|-----------|----------|---------|
| BUY | 10000 | 10500 | 1.0 | 500.00 |
| BUY | 10000 | 9500 | 1.0 | -500.00 |
| SELL | 10000 | 9500 | 1.0 | 500.00 |
| SELL | 10000 | 10500 | 1.0 | -500.00 |

**验证点**：
- [ ] `unrealizedPnl` 不写 `t_position`（数据库无该字段）
- [ ] 查询接口动态计算返回
- [ ] 使用 Redis `bid / ask` 解析有效标记价，不查 MySQL

---

#### TC-TRD-041 unrealizedPnl 不持久化验证

- **类型**：IT
- **验收阶段**：Stage 5

**操作**：开仓后查询 `t_position` 表

**预期结果**：`t_position` 无 `unrealized_pnl` 字段（已从 schema 移除）

---

#### TC-TRD-042 行情 stale 时 unrealizedPnl 的处理

- **类型**：IT
- **验收阶段**：Stage 7

**前置条件**：Redis 行情已过期（stale = true）

**操作**：查询持仓账户

**预期结果**：
- 响应中 `quoteStale = true`
- `unrealizedPnl` 使用最后一次有效价格计算（或标记为 null）
- 不返回 500 错误

---

### 4.5A 手动平仓（Manual Close）

#### TC-TRD-043 BUY 手动平仓成功

- **类型**：IT
- **验收阶段**：Stage 7

**前置条件**：
- 已存在 `OPEN` 多头持仓
- Redis 中存在新鲜 `bid / ask` 报价

**操作**：调用 `POST /api/v1/trading/positions/{positionId}/close`

**预期结果**：
- HTTP 200，业务码 `0`
- `t_position.status = CLOSED`
- `t_position.close_reason = 1`（manual）
- `t_trade` 新增一条 `trade_type = CLOSE`
- `t_account.margin_used -= margin`
- `t_account.balance += realizedPnl`
- `t_risk_exposure` 同事务回补
- `t_outbox.event_type = "trading.position.closed"`
- **不新增**新的 `t_order`

**验证点**：
- [ ] `t_position.status = "CLOSED"`
- [ ] `t_position.close_reason = 1`
- [ ] `t_trade.trade_type = CLOSE`
- [ ] `t_outbox.event_type = "trading.position.closed"`
- [ ] `t_order` 条数不因平仓增加

---

#### TC-TRD-044 SELL 手动平仓成功，realizedPnl 为负时账户语义正确

- **类型**：IT
- **验收阶段**：Stage 7

**前置条件**：
- 已存在 `OPEN` 空头持仓
- `markPrice > entryPrice`，使 `realizedPnl < 0`

**预期结果**：
- HTTP 200，业务码 `0`
- `t_position.realized_pnl < 0`
- `t_account.balance` 仅按 `realizedPnl` 变化
- `t_account.margin_used -= margin`
- `t_account.frozen` 不变
- `available = balance - frozen - margin_used`

**验证点**：
- [ ] `realizedPnl` 为负
- [ ] `balance / frozen / margin_used` 语义符合账户冻结规则
- [ ] `available` 计算恒成立

---

#### TC-TRD-045 节假日全休时开仓被拒绝、手动平仓允许

- **类型**：IT
- **验收阶段**：Stage 7

**前置条件**：
- 已存在 `OPEN` 持仓
- `t_trading_holiday` 对应品种当日为 `FULL_CLOSE`

**操作**：
1. 尝试新的开仓请求
2. 对既有持仓发起手动平仓

**预期结果**：
- 步骤 1：返回 `40008`
- 步骤 2：返回 `0`
- 手动平仓不受交易时间校验阻塞

---

#### TC-TRD-046 手动平仓遇到 stale 报价返回 30002

- **类型**：IT
- **验收阶段**：Stage 7

**前置条件**：Redis 中报价存在，但 `当前时间 - quote.ts > 5s`

**预期结果**：
- 业务码 `30002`
- 持仓保持 `OPEN`
- 不写 `t_trade / t_ledger / t_outbox`

---

#### TC-TRD-047 手动平仓缺少报价返回 30003

- **类型**：IT
- **验收阶段**：Stage 7

**前置条件**：Redis 中无该 symbol 最新价

**预期结果**：
- 业务码 `30003`
- 持仓保持 `OPEN`
- 不写 `t_trade / t_ledger / t_outbox`

---

#### TC-TRD-048 手动平仓时持仓不存在或不属于当前用户返回 40004

- **类型**：IT
- **验收阶段**：Stage 7

**预期结果**：
- 业务码 `40004`
- 原持仓状态不变
- 不写新的平仓事实

---

#### TC-TRD-049 重复平仓返回 40007

- **类型**：IT
- **验收阶段**：Stage 7

**操作**：对同一 `positionId` 连续调用两次手动平仓

**预期结果**：
- 第一次返回 `0`
- 第二次返回 `40007`
- 不重复写 `t_trade / t_ledger / t_outbox`

---

### 4.6 止盈止损自动触发

#### TC-TRD-050 多头持仓触发止盈

- **类型**：IT（含行情消费）
- **验收阶段**：Stage 7

**前置条件**：
- 持仓 `side = BUY`，`entryPrice = 10000`，`takeProfitPrice = 10500`
- 内存快照已加载该持仓

**操作**：注入使交易侧 `effectiveMarkPrice = 10500` 的价格事件（`≥ takeProfitPrice`，多头取 `bid`）

**预期结果**：
- 持仓自动平仓，`t_position.status = CLOSED`
- `t_position.close_reason = 2`（tp）
- `t_trade` 新增平仓成交记录，`trade_type = CLOSE`
- `t_account.margin_used -= margin`，`balance += pnl`
- `t_ledger` 有平仓账本记录
- 内存快照移除该持仓
- `t_risk_exposure.total_long_qty -= 1`（同事务）

**验证点**：
- [ ] `t_position.status = "CLOSED"`
- [ ] `t_position.close_reason = 2`
- [ ] `t_position.close_price = 10500`
- [ ] `t_risk_exposure` 已更新
- [ ] `t_ledger` 平仓记录完整

---

#### TC-TRD-051 多头持仓触发止损

- **类型**：IT
- **验收阶段**：Stage 7

**前置条件**：`takeProfitPrice = 10500`，`stopLossPrice = 9500`

**操作**：注入 `markPrice = 9500` 的价格事件（`≤ stopLossPrice`）

**预期结果**：
- `t_position.close_reason = 3`（sl）
- 其余同 TC-TRD-050

---

#### TC-TRD-052 空头持仓 TP/SL 触发方向相反

- **类型**：UT
- **验收阶段**：Stage 7

**规则验证**：

| side | 条件 | 触发 |
|------|------|------|
| SELL | `effectiveMarkPrice <= takeProfitPrice` | 止盈 |
| SELL | `markPrice >= stopLossPrice` | 止损 |

---

#### TC-TRD-053 同一价格事件不重复触发 TP/SL

- **类型**：IT
- **验收阶段**：Stage 7

**操作**：同一 `price.tick` 事件触发两次（重复投递模拟）

**预期结果**：仅触发一次平仓，幂等保护

---

### 4.7 强制平仓（Liquidation）

#### TC-TRD-060 保证金率不足触发强平

- **类型**：IT
- **验收阶段**：Stage 7

**前置条件**：
- 持仓 `side = BUY`，`entryPrice = 10000`，`quantity = 1`，`leverage = 10`
- `margin = 1000`，`maintenanceMarginRate = 0.5%`
- 强平价 = 9050

**操作**：注入 `markPrice = 9050` 的价格事件（`≤ 强平价`）

**预期结果**：
- `t_position.status = LIQUIDATED`
- `t_liquidation_log` 新增一条记录
- `t_account.margin_used -= margin`
- `t_account.balance += 实际清算结果`（可能为 0）
- 内存快照移除该持仓

**验证点**：
- [ ] `t_position.status = "LIQUIDATED"`
- [ ] `t_liquidation_log` 有记录
- [ ] `t_risk_exposure` 同事务更新

---

#### TC-TRD-061 负净值保护（账户余额归零不打负）

- **类型**：IT
- **验收阶段**：Stage 7

**前置条件**：极端行情，强平亏损超过账户余额

**预期结果**：
- `t_account.balance = 0`（不为负数）
- `t_liquidation_log.platform_covered_loss > 0`（记录平台兜底金额）

**验证点**：
- [ ] `balance >= 0` 始终成立
- [ ] `platform_covered_loss = |超出亏损|`

---

#### TC-TRD-062 强平不受交易时间限制

- **类型**：UT
- **验收阶段**：Stage 7

**规则**：非交易时段只拒绝新的开仓，强平和平仓不受交易时间校验阻塞

**验证点**：
- [ ] 代码层面强平路径跳过交易时间校验

---

#### TC-TRD-063 t_risk_exposure 在强平时同事务更新

- **类型**：IT
- **验收阶段**：Stage 7

**操作**：触发强平，然后故意模拟 `t_risk_exposure` 写入失败（回滚）

**预期结果**：整个事务回滚，持仓状态未变

---

### 4.8 隔夜利息（Swap）

#### TC-TRD-070 隔夜利息收取（多头持仓过 rollover）

- **类型**：IT
- **验收阶段**：Stage 6B

**前置条件**：
- `t_swap_rate` 中 `BTCUSDT` 的 `rate_long = -0.00010000`（多头每天扣）
- 持仓 `side = BUY`，`quantity = 1`，成交价 10000

**操作**：到达 `rollover_time`，Swap 结算定时任务触发

**预期结果**：
- `swap_amount = quantity * price * |rate_long| = 1 * 10000 * 0.0001 = 1`
- `t_ledger` 新增一条 `biz_type = 6`（swap_charge）记录
- `t_account.balance -= 1`

**验证点**：
- [ ] `t_ledger.biz_type = 6`
- [ ] 金额精度正确

---

#### TC-TRD-071 隔夜利息收入（空头持仓）

- **类型**：IT
- **验收阶段**：Stage 6B

**前置条件**：`rate_short = 0.00005000`（空头每天获得）

**预期结果**：
- `t_ledger.biz_type = 7`（swap_income）
- `t_account.balance += swap_amount`

---

#### TC-TRD-072 Swap 结算幂等

- **类型**：IT
- **验收阶段**：Stage 6B

**操作**：同一 rollover 时间点的 Swap 任务触发两次

**预期结果**：账本只有一条 Swap 记录，不重复扣款

---

#### TC-TRD-073 Swap 明细查询接口

- **类型**：IT
- **验收阶段**：Stage 6B

**前置条件**：
- 当前用户已存在至少一条 `Swap` 账本记录
- `t_position` 中仍可查询到对应 `positionId / symbol / side`

**操作**：调用 `GET /api/v1/trading/swap-settlements?page=1&pageSize=20`

**预期结果**：
- 返回当前用户自己的 `Swap` 明细分页
- `items[*]` 至少包含 `ledgerId / positionId / symbol / side / settlementType / amount / balanceAfter / rolloverAt / settledAt / referenceNo`
- `referenceNo` 与账本 `swap:{positionId}:{rolloverAt}` 保持一致

---

#### TC-TRD-074 Swap 结算业务事件出站

- **类型**：IT
- **验收阶段**：Stage 6B

**前置条件**：
- 满足 `TC-TRD-070` 或 `TC-TRD-071` 的结算前置条件

**操作**：触发一次成功的 `Swap` 结算

**预期结果**：
- `t_outbox` 新增一条 `event_type = trading.swap.settled`
- Kafka 主题 `falconx.trading.swap.settled` 收到一条事件
- payload 至少包含 `ledgerId / userId / positionId / symbol / side / settlementType / amount / rate / effectivePrice / rolloverAt / quoteTs / settledAt`

---

#### TC-TRD-075 订单列表查询接口

- **类型**：IT
- **验收阶段**：Stage 6B

**前置条件**：
- 当前用户已存在至少两笔订单
- 存在其他用户订单作为隔离对照

**操作**：调用 `GET /api/v1/trading/orders?page=1&pageSize=20`

**预期结果**：
- 只返回当前用户自己的订单分页
- `items[*]` 至少包含 `orderId / orderNo / symbol / side / orderType / quantity / requestedPrice / filledPrice / leverage / margin / fee / clientOrderId / status / createdAt / updatedAt`
- 分页总数不包含其他用户订单

---

#### TC-TRD-076 成交列表查询接口

- **类型**：IT
- **验收阶段**：Stage 6B

**前置条件**：
- 当前用户已存在开仓成交和对应的平仓或强平成交

**操作**：调用 `GET /api/v1/trading/trades?page=1&pageSize=20`

**预期结果**：
- 只返回当前用户自己的成交分页
- `items[*]` 至少包含 `tradeId / orderId / positionId / symbol / side / tradeType / quantity / price / fee / realizedPnl / tradedAt`
- 分页结果按最新成交优先返回

---

#### TC-TRD-077 持仓列表查询接口

- **类型**：IT
- **验收阶段**：Stage 6B

**前置条件**：
- 当前用户同时存在 `OPEN` 持仓和终态持仓

**操作**：调用 `GET /api/v1/trading/positions?page=1&pageSize=20`

**预期结果**：
- 只返回当前用户自己的持仓历史分页
- `items[*]` 至少包含 `positionId / openingOrderId / symbol / side / quantity / entryPrice / leverage / margin / liquidationPrice / takeProfitPrice / stopLossPrice / closePrice / closeReason / realizedPnl / status / openedAt / closedAt / updatedAt`
- 对于 `OPEN` 持仓，动态回填 `markPrice / unrealizedPnl / quoteStale / quoteTs / quoteSource`
- 对于终态持仓，不伪造新的 `unrealizedPnl`

---

#### TC-TRD-078 账本流水查询接口

- **类型**：IT
- **验收阶段**：Stage 6B

**前置条件**：
- 当前用户已存在入金、下单、平仓或 `Swap` 等账本流水

**操作**：调用 `GET /api/v1/trading/ledger?page=1&pageSize=20`

**预期结果**：
- 只返回当前用户自己的账本分页
- `items[*]` 至少包含 `ledgerId / bizType / amount / idempotencyKey / referenceNo / balanceBefore / balanceAfter / frozenBefore / frozenAfter / marginUsedBefore / marginUsedAfter / createdAt`
- 首版费用事实可通过 `ORDER_FEE_CHARGED / SWAP_* / LIQUIDATION_PNL / REALIZED_PNL` 等 `bizType` 观察

---

#### TC-TRD-079 强平记录查询接口

- **类型**：IT
- **验收阶段**：Stage 6B

**前置条件**：
- 当前用户已存在至少一条强平记录

**操作**：调用 `GET /api/v1/trading/liquidations?page=1&pageSize=20`

**预期结果**：
- 只返回当前用户自己的强平记录分页
- `items[*]` 至少包含 `liquidationLogId / positionId / symbol / side / quantity / entryPrice / liquidationPrice / markPrice / priceTs / priceSource / loss / fee / marginReleased / platformCoveredLoss / createdAt`
- 分页总数不包含其他用户强平记录

---

### 4.9 净敞口（Risk Exposure）

#### TC-TRD-080 开仓后净敞口更新

- **类型**：IT
- **验收阶段**：Stage 5

**操作**：开多头 1 手 BTCUSDT

**预期结果**：`t_risk_exposure` 中 `total_long_qty += 1`

---

#### TC-TRD-081 平仓后净敞口回补

- **类型**：IT
- **验收阶段**：Stage 7

**操作**：平掉多头 1 手 BTCUSDT

**预期结果**：`t_risk_exposure.total_long_qty -= 1`

**关键约束**：与平仓事务同步，不允许异步延迟

---

#### TC-TRD-082 净敞口写入与订单持仓同一事务

- **类型**：IT
- **验收阶段**：Stage 5

**操作**：模拟 `t_risk_exposure` 写入异常

**预期结果**：整个事务回滚，订单/持仓均不落库

---

### 4.10 Outbox 投递

#### TC-TRD-090 Outbox 调度正常投递

- **类型**：IT
- **验收阶段**：Stage 5

**操作**：完成一次入金，等待 Outbox 调度周期（≤ 2s）

**预期结果**：
- `t_outbox.status` 从 `PENDING` 变为 `SENT`
- Kafka 收到对应消息
- `t_outbox.sent_at` 有值

---

#### TC-TRD-091 Outbox 失败重试退避

- **类型**：IT
- **验收阶段**：Stage 5

**操作**：模拟 Kafka 不可用，触发投递失败

**预期结果**：
- `t_outbox.status = FAILED`，`retry_count += 1`
- `next_retry_at` 按退避策略递增（5s → 30s → 120s → 30m）

---

#### TC-TRD-092 Outbox 超过最大重试次数标记 DEAD

- **类型**：IT
- **验收阶段**：Stage 5

**操作**：持续失败达 10 次

**预期结果**：`t_outbox.status = DEAD`

---

---

## 5. 钱包服务测试（wallet-service）

### 5.1 地址分配

#### TC-WAL-001 为用户分配链地址

- **类型**：IT
- **验收阶段**：Stage 2B / Stage 7

**前置条件**：用户已注册，无已有地址

**操作**：调用地址分配接口

**预期结果**：
- `t_wallet_address` 新增一条记录
- 返回有效 ETH 地址
- 同一用户再次请求返回同一地址（幂等）

---

#### TC-WAL-002 重复申请地址（幂等）

- **类型**：IT
- **验收阶段**：Stage 2B

**操作**：同一用户两次申请地址

**预期结果**：返回同一地址，`t_wallet_address` 只有一条记录

---

### 5.2 链上入金检测

#### TC-WAL-010 链上入金检测写入 DETECTED

- **类型**：IT（依赖 Web3 Stub）
- **验收阶段**：Stage 6A

**操作**：模拟链上检测到归属平台地址的 USDT 转账

**预期结果**：
- `t_wallet_deposit_tx.status = DETECTED`
- 发布 `falconx.wallet.deposit.detected` 事件（通过 Outbox）

---

#### TC-TRD-011 确认数推进至 CONFIRMING

- **类型**：IT
- **验收阶段**：Stage 6A

**操作**：确认数从 0 增长，未到阈值

**预期结果**：`t_wallet_deposit_tx.status = CONFIRMING`

---

#### TC-WAL-012 确认数达到阈值时变为 CONFIRMED

- **类型**：IT
- **验收阶段**：Stage 6A

**操作**：确认数到达 `required_confirmations`

**预期结果**：
- `t_wallet_deposit_tx.status = CONFIRMED`
- `confirmed_at` 写入
- 发布 `falconx.wallet.deposit.confirmed` 事件

**验证点**：
- [ ] `confirmed_at` 只写一次，后续确认数增加不覆盖
- [ ] Outbox 中有对应事件

---

#### TC-WAL-013 链回滚导致入金撤回

- **类型**：IT
- **验收阶段**：Stage 6A

**操作**：已 CONFIRMED 的交易被链回滚

**预期结果**：
- `t_wallet_deposit_tx.status = REVERSED`
- 发布 `falconx.wallet.deposit.reversed` 事件

---

#### TC-WAL-014 重复检测同一 txHash（幂等）

- **类型**：IT
- **验收阶段**：Stage 2B

**操作**：相同 `(chain, txHash)` 被检测两次

**预期结果**：`t_wallet_deposit_tx` 只有一条记录

---

#### TC-WAL-015 ETH 外部真节点扫块自动化

- **类型**：IT（依赖外部 ETH 节点）
- **验收阶段**：Stage 6A

**前置条件**：
- 显式提供 `FALCONX_WALLET_EXTERNAL_TEST_ENABLED=true`
- 显式提供 `FALCONX_WALLET_ETH_RPC_URL`
- 本机 JVM trust store 可验证目标节点证书链

**操作**：
- 以配置限制 `ETH` 单链启动外部监听器
- 基于真实最近区块动态发现一笔原生币转账
- 把目标地址登记为平台地址，并从前一块开始扫块

**预期结果**：
- 只初始化 `ETH` 游标，不扩展其他链
- `t_wallet_chain_cursor` 会推进到最新链头
- 原生币转账先进入 `CONFIRMING`，再通过确认窗口重扫推进到 `CONFIRMED`
- `wallet.deposit.detected / confirmed` Outbox payload 中都能看到同一个 `walletTxId`

---

#### TC-WAL-016 ETH 外部真节点失败重试

- **类型**：IT（依赖外部 ETH 节点错误认证）
- **验收阶段**：Stage 6A

**前置条件**：
- 显式提供 `FALCONX_WALLET_EXTERNAL_TEST_ENABLED=true`
- 显式提供 `FALCONX_WALLET_ETH_RPC_URL` 或独立失败地址

**操作**：以真实节点错误认证地址启动 ETH 监听器

**预期结果**：
- 日志出现 `wallet.listener.chainHead.syncFailed`
- 下一轮轮询会继续重试
- owner 游标不前移
- 不产出新的链上观察记录

---

---

## 6. 网关测试（gateway）

### 6.1 路由与鉴权

#### TC-GW-001 未认证请求被拒

- **类型**：E2E
- **验收阶段**：Stage 4

**操作**：调用受保护接口，不携带 Authorization 头

**预期结果**：HTTP 401，业务码 `10001`

---

#### TC-GW-002 签名无效 Token 被拒

- **类型**：E2E
- **验收阶段**：Stage 4

**操作**：携带非法签名的 JWT

**预期结果**：HTTP 401，业务码 `10001`

---

#### TC-GW-003 已过期 Token 被拒

- **类型**：E2E
- **验收阶段**：Stage 4

**操作**：携带 `exp` 早于当前时间的 Token

**预期结果**：HTTP 401，业务码 `10001`

---

#### TC-GW-004 黑名单 Token 被拒

- **类型**：E2E
- **验收阶段**：Stage 6B

**前置条件**：Token 的 `jti` 已加入 Redis 黑名单

**预期结果**：HTTP 401，业务码 `10001`

---

#### TC-GW-005 BANNED 用户所有请求被拒

- **类型**：E2E
- **验收阶段**：Stage 4

**前置条件**：Token `status = BANNED`

**预期结果**：业务码 `10002`

---

#### TC-GW-006 FROZEN 用户写操作被拒，读操作通过

- **类型**：E2E
- **验收阶段**：Stage 4

**操作**：
1. `GET /api/v1/trading/accounts/me` → 应通过（200）
2. `POST /api/v1/trading/orders/market` → 应被拒（`10007`）

---

#### TC-GW-010 traceId 生成与透传

- **类型**：E2E
- **验收阶段**：Stage 4

**验证点**：
- [ ] 每个请求均有唯一 `X-Trace-Id` 响应头
- [ ] 同一请求在 gateway、market-service、trading-core-service 日志中的 `traceId` 完全一致
- [ ] 前端不能自定义 `X-Trace-Id`（即使传入也被覆盖）

---

#### TC-GW-011 X-User-* 头透传到下游

- **类型**：E2E
- **验收阶段**：Stage 4

**验证点**：
- [ ] 下游服务收到 `X-User-Id / X-User-Uid / X-User-Status`
- [ ] 值与 JWT payload 一致

---

### 6.2 公开接口路由

#### TC-GW-020 注册/登录/刷新接口无需 Token

- **类型**：E2E
- **验收阶段**：Stage 4

**操作**：不携带 Authorization 头调用 `/api/v1/auth/**`

**预期结果**：正常进入 identity-service，不被 gateway 拦截

---

### 6.3 北向行情 WebSocket

#### TC-GW-021 Market WebSocket 握手鉴权与头透传

- **类型**：IT
- **验收阶段**：Stage 6B

**操作**：
1. 不带 `token` 握手连接 `ws://{host}/ws/v1/market`
2. 使用 `status=BANNED` 的 Access Token 再次握手
3. 使用有效 Access Token 握手，并向下游 market 代理一个文本帧

**预期结果**：
- 第 1 步返回 `HTTP 401`
- 第 2 步返回 `HTTP 403`
- 第 3 步连接成功，gateway 会向下游透传 `X-User-Id / X-User-Uid / X-User-Status / X-Trace-Id`

---

#### TC-GW-022 同一用户第 6 个 Market WebSocket 连接被拒

- **类型**：IT
- **验收阶段**：Stage 6B

**前置条件**：同一用户已成功建立 5 个并发 `market` WebSocket 连接

**操作**：建立第 6 个连接

**预期结果**：握手阶段返回 `HTTP 429`

---

---

## 7. 跨服务集成测试（端到端链路）

### 7.1 完整用户注册激活链路

#### TC-E2E-001 注册 → 入金 → 激活链路

- **类型**：E2E
- **验收阶段**：Stage 7

**操作序列**：
1. `POST /api/v1/auth/register` → 创建用户（PENDING_DEPOSIT）
2. 模拟 wallet-service 发布 `wallet.deposit.confirmed`（userId 对应）
3. trading-core-service 消费，完成入账，发布 `trading.deposit.credited`
4. identity-service 消费，激活用户（ACTIVE）
5. `POST /api/v1/auth/login` → 登录，获取 Token
6. `GET /api/v1/trading/accounts/me` → 查看账户余额

**预期结果**：
- 步骤 4 后 `t_user.status = ACTIVE`
- 步骤 6 `balance > 0`

**验证点**：
- [ ] 每步日志中 `traceId` 贯穿
- [ ] 不同服务的 `t_inbox / t_outbox` 幂等记录完整
- [ ] 账户余额与入金金额一致

---

### 7.2 完整交易链路

#### TC-E2E-010 下单 → 持仓 → 止盈平仓链路

- **类型**：E2E
- **验收阶段**：Stage 7

**操作序列**：
1. 登录，获取 Token
2. `POST /api/v1/trading/orders/market`，设置 `takeProfitPrice`
3. 注入使交易侧有效价高于 TP 价格的行情事件（多头取 `bid`，空头取 `ask`）
4. `GET /api/v1/trading/accounts/me`

**预期结果**：步骤 4 时持仓已自动平仓，`openPositions = []`，`balance` 含止盈收益

---

#### TC-E2E-011 下单 → 持仓 → 强平链路

- **类型**：E2E
- **验收阶段**：Stage 7

**操作序列**：
1. 下单，持仓杠杆 10x
2. 注入跌破强平价格的行情事件
3. 查询账户

**预期结果**：持仓 `LIQUIDATED`，余额不为负

---

### 7.3 Outbox / Inbox 幂等链路

#### TC-E2E-020 Outbox 事件重复投递，消费端幂等

- **类型**：IT
- **验收阶段**：Stage 5

**操作**：
1. 完成入金，Outbox 投递 `deposit.credited`
2. 模拟重复投递同一事件（相同 `eventId`）

**预期结果**：identity-service 只激活一次，无重复账本记录

---

---

## 8. 安全专项测试

### 8.1 认证安全

#### TC-SEC-001 不允许 HS256 算法 Token

- **类型**：SEC
- **验收阶段**：Stage 4 / Stage 6B

**操作**：构造 HS256 签名的 JWT 提交到 gateway

**预期结果**：gateway 拒绝，HTTP 401

---

#### TC-SEC-002 algorithm confusion（alg=none 攻击）

- **类型**：SEC
- **验收阶段**：Stage 6B

**操作**：构造 `"alg": "none"` 的 JWT

**预期结果**：gateway 拒绝，HTTP 401

---

#### TC-SEC-003 密码明文不出现在日志

- **类型**：SEC
- **验收阶段**：Stage 3A

**操作**：注册/登录请求后检查日志输出

**预期结果**：日志中无 `password` 字段的明文值，无完整 JWT payload

---

#### TC-SEC-004 bcrypt 存储验证

- **类型**：IT
- **验收阶段**：Stage 3A

**验证点**：
- [ ] `t_user.password` 以 `$2a$` 或 `$2b$` 开头
- [ ] cost factor ≥ 12
- [ ] 原文密码无法从哈希值反推

---

### 8.2 输入校验

#### TC-SEC-010 SQL 注入防御

- **类型**：SEC
- **验收阶段**：Stage 5

**操作**：在 `email` 字段注入 `' OR '1'='1`

**预期结果**：业务层正常返回用户不存在，无 SQL 执行错误，不绕过认证

**验证点**：
- [ ] 所有 SQL 通过 MyBatis XML Mapper 执行，无字符串拼接

---

#### TC-SEC-011 XSS 防御

- **类型**：SEC
- **验收阶段**：Stage 7

**操作**：在 `symbol` 等字段注入 `<script>alert(1)</script>`

**预期结果**：参数校验拒绝或转义，不原样返回

---

### 8.3 速率限制

#### TC-SEC-020 注册频率限制

- **类型**：SEC
- **验收阶段**：Stage 6B

**操作**：同一 IP 1 小时内注册超过 5 次

**预期结果**：第 6 次返回 `10004`

---

---

## 9. 事务幂等专项测试

### 9.1 并发场景

#### TC-TXN-001 并发下单，余额足够一笔

- **类型**：TXN（压测级别）
- **验收阶段**：Stage 7

**前置条件**：`balance = 1000`，保证金需求 900，同时发 2 笔下单请求

**预期结果**：只有一笔成交，另一笔余额不足被拒

**验证点**：
- [ ] `t_account.balance ≥ 0` 始终成立
- [ ] `margin_used + frozen ≤ balance`
- [ ] 不出现超卖（两笔都成功）

---

#### TC-TXN-002 FOR UPDATE 锁保证账户余额一致性

- **类型**：IT
- **验收阶段**：Stage 5

**验证点**：
- [ ] 账户读写使用 `SELECT ... FOR UPDATE`（代码层面验证）
- [ ] 并发场景不出现余额不一致

---

### 9.2 事务回滚

#### TC-TXN-010 订单写入成功但持仓写入失败时全部回滚

- **类型**：IT
- **验收阶段**：Stage 3B

**操作**：模拟 `t_position` 写入失败（如唯一约束冲突）

**预期结果**：`t_order` 也回滚，账户余额不变

---

#### TC-TXN-011 Outbox 写入失败时业务事务全部回滚

- **类型**：IT
- **验收阶段**：Stage 5

**操作**：模拟 `t_outbox` 写入异常

**预期结果**：主业务事务也回滚（Outbox 与业务同事务）

---

#### TC-TXN-012 手动平仓时 t_risk_exposure 写入失败整笔事务回滚

- **类型**：IT
- **验收阶段**：Stage 7

**操作**：制造平仓前置完成但 `t_risk_exposure` 更新失败

**预期结果**：
- 平仓事务整体回滚
- `t_position` 保持 `OPEN`
- `t_account` 不结算
- 不写新的 `t_trade / t_ledger / t_outbox`
- 不新增 `t_order`

---

---

## 10. Kafka 事件专项测试

### 10.1 事件格式验证

#### TC-KFK-001 market.price.tick 事件信封完整性

- **类型**：IT
- **验收阶段**：Stage 6A

**验证点**：
- [ ] `eventId` 非空且唯一
- [ ] `eventType = "market.price.tick"`
- [ ] `schemaVersion = 1`
- [ ] `source = "falconx-market-service"`
- [ ] `occurredAt` 为 ISO8601 格式
- [ ] `traceId` 非空
- [ ] `partitionKey = symbol`
- [ ] `payload` 包含 `symbol / bid / ask / mid / mark / ts / source / stale`

---

#### TC-KFK-002 wallet.deposit.confirmed 事件 payload 完整性

- **类型**：IT
- **验收阶段**：Stage 5

**验证点**：
- [ ] `payload.userId / chain / token / txHash / fromAddress / toAddress / amount / confirmations / requiredConfirmations / confirmedAt` 全部存在
- [ ] `amount` 为字符串类型，精度 8 位

---

#### TC-KFK-003 trading.deposit.credited 事件 payload 完整性

- **类型**：IT
- **验收阶段**：Stage 5

**验证点**：
- [ ] `payload.depositId / userId / accountId / chain / token / txHash / amount / creditedAt` 全部存在

---

### 10.2 消费组命名

#### TC-KFK-010 消费组命名符合规范

- **类型**：代码审查
- **验收阶段**：Stage 5

**规范格式**：`falconx.<service-name>.<context>-consumer-group`

**验证点**：
- [ ] `trading-core-service` 消费 `price.tick` 的消费组名为 `falconx.trading-core-service.price-tick-consumer-group`
- [ ] `identity-service` 消费 `deposit.credited` 的消费组名为 `falconx.identity-service.deposit-credited-consumer-group`

---

---

## 11. 性能边界测试

### 11.1 行情处理吞吐量

#### TC-PERF-001 高频行情处理不阻塞主线程

- **类型**：PERF
- **验收阶段**：Stage 6A

**操作**：以 100 msg/s 速率注入行情数据

**预期结果**：
- Redis 写入延迟 p99 < 20ms
- ClickHouse 批量写入正常
- 无 OOM、无线程堆积

---

#### TC-PERF-002 行情回调不在 WebSocket 回调线程中执行完整消费链路

- **类型**：代码审查 / IT
- **验收阶段**：Stage 6A

**规范约束**：来自 WebSocket SDK 回调的消息必须先切换到应用自管线程再处理

**验证点**：
- [ ] 代码中 WebSocket `onMessage` 只做入队/派发，不直接调用 Redis/Kafka/DB

---

### 11.2 下单延迟

#### TC-PERF-010 市价单端到端延迟

- **类型**：PERF
- **验收阶段**：Stage 7

**操作**：在行情正常情况下提交市价单

**预期结果**：端到端响应时间（包含 gateway → trading-core-service → 数据库写入）p99 < 500ms

---

---

## 12. 日志与链路可观测性测试

### 12.1 日志规范

#### TC-LOG-001 关键业务节点日志存在

- **类型**：IT
- **验收阶段**：各阶段

| 服务 | 关键日志点 |
|------|-----------|
| identity-service | `identity.register.received / completed`，`identity.login.received / completed` |
| market-service | `market.quote.received`，`market.redis.written`，`market.kline.closed`，`market.websocket.subscribe.accepted / price.push / price.stale-push / kline.push` |
| trading-core-service | `trading.order.received`，`trading.order.filled / rejected`，`trading.swap.settlement.completed / duplicate`，`trading.liquidation.triggered / executed` |
| gateway | `gateway.request.received`，`gateway.auth.accepted / rejected`，`gateway.websocket.handshake.accepted / rejected`，`gateway.websocket.proxy.connected / closed` |
| wallet-service | `wallet.listener.chainHead.syncFailed` |

**验证点**：
- [ ] 每个关键操作有 INFO 级日志
- [ ] 日志包含 `traceId / userId / symbol`（如适用）
- [ ] 无 `password / jwt_payload / refreshToken` 明文

---

#### TC-LOG-002 traceId 跨服务一致

- **类型**：E2E
- **验收阶段**：Stage 4

**操作**：发送一个请求，收集 gateway、market-service、trading-core-service 的日志

**预期结果**：三处日志中 `traceId` 完全一致

---

#### TC-LOG-003 错误日志携带完整上下文

- **类型**：IT
- **验收阶段**：Stage 3B

**操作**：触发业务异常（如余额不足、行情过时）

**预期结果**：ERROR 日志包含 `traceId / userId / symbol / rejectionReason`，不只有异常 stacktrace

---

#### TC-LOG-004 Stage 6B 运营关键链路日志存在

- **类型**：IT / 审计
- **验收阶段**：Stage 6B

**验证点**：
- [ ] `GatewayMarketWebSocketIntegrationTests` 已验证 `gateway.websocket.handshake.rejected`（`401/403/429`）与 `gateway.websocket.proxy.connected`
- [ ] `MarketWebSocketIntegrationTests` 已验证 `market.websocket.session.opened / closed` 的 `activeSessions`，以及 `market.websocket.subscribe.accepted / unsubscribe.accepted` 的 `channelCount / symbolCount`
- [ ] `TradingSwapSettlementIntegrationTests` 已验证 `trading.swap.settlement.batch.completed / duplicate`
- [ ] `TradingLiquidationIntegrationTests` 已验证 `trading.liquidation.triggered / executed`
- [ ] `JdkTiingoQuoteProviderTests` 已验证 `market.tiingo.provider.message.parsed` 的 `quotes / accepted / filtered` 统计口径；`MarketTiingoExternalSourceAutomationIntegrationTests`、`JdkTiingoQuoteProviderExternalFailureIntegrationTests` 在显式外部环境下可提供 `market.tiingo.provider.*` 日志证据
- [ ] `Web3jChainDepositListenerTests`、`WalletExternalChainNodeAutomationIntegrationTests`、`Web3jChainDepositListenerExternalFailureIntegrationTests` 已覆盖 `wallet.listener.chainHead.synced / syncFailed` 的 `scannedBlocks / detectedCount / reversedCount` 统计日志
- [ ] `SpringTradingHedgeAlertEventPublisherTests` 已验证 `trading.risk.hedge.event.published`

---

---

## 13. 验收检查清单

### 13.1 Stage 5 验收必须用例

以下用例必须在 Stage 5 全部通过：

- [ ] TC-AUTH-001、TC-AUTH-002、TC-AUTH-010、TC-AUTH-011、TC-AUTH-020、TC-AUTH-021
- [ ] TC-AUTH-030、TC-AUTH-031（Kafka 消费幂等）
- [ ] TC-MKT-001、TC-MKT-010、TC-MKT-011、TC-MKT-012
- [ ] TC-MKT-030、TC-MKT-031、TC-MKT-032、TC-MKT-033、TC-MKT-034
- [ ] TC-TRD-001、TC-TRD-002、TC-TRD-010、TC-TRD-011（入金幂等）
- [ ] TC-TRD-020、TC-TRD-022、TC-TRD-023、TC-TRD-024、TC-TRD-025
- [ ] TC-TRD-030、TC-TRD-031（保证金计算）
- [ ] TC-TRD-080、TC-TRD-082（净敞口）
- [ ] TC-TRD-090、TC-TRD-091、TC-TRD-092（Outbox）
- [ ] TC-WAL-001、TC-WAL-002、TC-WAL-014
- [ ] TC-GW-001、TC-GW-002、TC-GW-003、TC-GW-005、TC-GW-006、TC-GW-010、TC-GW-011
- [ ] TC-TXN-001、TC-TXN-010、TC-TXN-011
- [ ] TC-SEC-003、TC-SEC-004、TC-SEC-010

### 13.2 Stage 6A 新增验收必须用例

- [ ] TC-MKT-003、TC-MKT-004（白名单热刷新）
- [ ] TC-MKT-005、TC-MKT-006（Tiingo 真源自动化与失败路径）
- [ ] TC-MKT-020、TC-MKT-021（K 线聚合）
- [ ] TC-WAL-010、TC-WAL-012、TC-WAL-013、TC-WAL-015、TC-WAL-016
- [ ] TC-KFK-001、TC-KFK-002、TC-KFK-003
- [ ] TC-PERF-002（WebSocket 线程隔离）

### 13.3 Stage 6B 新增验收必须用例

- [ ] TC-AUTH-015（登录限流）
- [ ] TC-GW-004（黑名单）
- [ ] TC-GW-021、TC-GW-022（行情 WebSocket 握手鉴权与连接限制）
- [ ] TC-SEC-001、TC-SEC-002（JWT 算法攻击）
- [ ] TC-SEC-020（注册频率限制）
- [ ] TC-MKT-043、TC-MKT-044、TC-MKT-045、TC-MKT-046、TC-MKT-047、TC-MKT-048（北向行情 WebSocket）
- [ ] TC-TRD-070、TC-TRD-071、TC-TRD-072、TC-TRD-073、TC-TRD-074、TC-TRD-075、TC-TRD-076、TC-TRD-077、TC-TRD-078、TC-TRD-079（Swap 与用户视角查询）
- [ ] TC-LOG-004（Stage 6B 运营关键链路日志）
- [ ] TC-E2E-001、TC-E2E-010、TC-E2E-011（既有主链路基线不回退）

### 13.4 Stage 7 验收必须用例（全部）

- [ ] TC-E2E-001（完整注册激活链路）
- [ ] TC-E2E-010（TP/SL 自动平仓）
- [ ] TC-E2E-011（强平链路）
- [ ] TC-TRD-043、TC-TRD-044、TC-TRD-045、TC-TRD-046、TC-TRD-047、TC-TRD-048、TC-TRD-049（手动平仓）
- [ ] TC-TRD-050、TC-TRD-051、TC-TRD-052、TC-TRD-053（TP/SL）
- [ ] TC-TRD-060、TC-TRD-061、TC-TRD-062、TC-TRD-063（强平）
- [ ] TC-TRD-041（unrealizedPnl 不持久化）
- [ ] TC-TRD-040、TC-TRD-042（浮盈浮亏）
- [ ] TC-TRD-012（入金撤回）
- [ ] TC-TXN-012（手动平仓事务回滚）
- [ ] TC-LOG-001、TC-LOG-002、TC-LOG-003

### 13.4A Stage 7 当前审计映射说明（非验收结论）

说明：

- 本节只用于把当前仓库已有事实映射到 `Stage 7` 必测项，方便后续验收编排。
- 本节不是 `Stage 7 已完成` 结论；只有 `13.4` 全部用例按正式口径复跑通过，才允许写阶段完成。

| 验收项分组 | 当前测试资产 | 当前状态 |
| --- | --- | --- |
| `TC-E2E-001 / 010 / 011` | `GatewayMinimalMainlineE2ETests`、`GatewayTakeProfitE2ETests`、`GatewayLiquidationE2ETests` | 已实现，待按 `Stage 7` 口径统一复跑 |
| `TC-TRD-043 ~ 049` | `TradingControllerIntegrationTests` | 已实现，待正式验收 |
| `TC-TRD-050 ~ 053` | `TradingAutoCloseIntegrationTests`、`QuoteDrivenEngineTriggerRuleTests` | 已实现，待正式验收 |
| `TC-TRD-060 ~ 063` | `TradingLiquidationIntegrationTests`、`DefaultTradingScheduleServiceTests` | 已实现，待正式验收 |
| `TC-TRD-012` | `TradingKafkaWalletDepositIntegrationTests` | 已实现，待正式验收 |
| `TC-TXN-012` | `TradingPersistenceIntegrationTests.shouldRollbackManualCloseWhenRiskExposureUpdateFails` | 已实现，待正式验收 |
| `TC-TRD-040 / 041 / 042` | `TradingQuoteSnapshotStaleIntegrationTests`、`TradingControllerIntegrationTests`、`TradingUserQueryControllerIntegrationTests` | 已有运行事实，`TC-TRD-042` 显式验收闭环待补齐 |
| `TC-LOG-001 / 002 / 003` | 当前无成套 `Stage 7` 验收资产 | 真实缺口 |
| 核心链路压测与统一回归 | 当前无 `Stage 7` 正式归档 | 真实缺口 |

---

## 14. 测试环境规范

### 14.1 基础设施要求

- MySQL 8.4（独立测试 schema，不复用生产 schema）
- Redis 8.2.x（独立 DB 编号）
- Kafka（Testcontainers 或独立测试集群）
- ClickHouse 25.x（独立测试库）

### 14.2 数据隔离规则

- 每个集成测试类执行前后必须清理数据（`@Transactional` 回滚或显式 `DELETE`）
- 不允许测试用例间共享用户 ID 或账户状态
- Flyway checksum 冲突时，测试环境必须使用新的隔离库，不允许静默复用已应用的旧 checksum 库

### 14.3 Stub / Mock 使用约定

| 场景 | 允许 |
|------|------|
| 单元测试中 Mock 外部依赖 | 允许 |
| 集成测试中 Mock MySQL / Redis | **禁止** |
| 集成测试中使用 Stub Provider 注入报价 | 允许（仅 dev/test profile） |
| 生产链路中使用 In-Memory Repository | **禁止** |

### 14.4 并行执行约束

- `mvn test` 和 `mvn clean compile` 不允许并行执行（共用同一 target/ 目录）
- 会清理 target/ 的命令必须串行执行

---

## 15. 问题上报规则

- 测试中发现的真实 Bug，必须回写到 [docs/process/统一问题清单.md](../process/统一问题清单.md)
- 测试用例本身若发现文档歧义，需先更正文档，再修改用例
- 不允许因测试用例"暂时通过"就关闭问题，必须验证与文档语义完全一致

---

*本文档随项目阶段演进持续更新，每次新增测试场景须同步维护验收检查清单。*
