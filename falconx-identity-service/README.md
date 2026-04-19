# falconx-identity-service

## 模块职责

`falconx-identity-service` 负责身份域 owner 能力：

- 用户注册、登录、Token 刷新
- 用户状态维护
- 基于业务事件的用户激活
- 身份域安全策略与认证会话治理

## 当前真实状态

- 身份域最小业务闭环已落地，owner MySQL、Flyway、`MyBatis + XML Mapper` 已接入。
- 当前已对外提供：
  - `POST /api/v1/auth/register`
  - `POST /api/v1/auth/login`
  - `POST /api/v1/auth/refresh`
- `Stage 7` 当前范围已验收，但 `Stage 6B` 只处于已开始未验收状态。

## 已落地能力

- 注册、登录、刷新 Token 主链路已落地。
- Refresh Token 会话已持久化，并支持一次性使用控制。
- 已通过真实 Kafka 入口消费 `falconx.trading.deposit.credited`，默认消费组为 `falconx.identity-service.deposit-credited-consumer-group`，支持用户激活、重复 `eventId` 幂等、缺失用户失败重试和 `ACTIVE` 幂等跳过。
- RSA 密钥与数据库账号密码已改为环境变量注入。
- 已落地登录失败锁定策略。
- 已具备内部 Token 黑名单 Redis 存储能力，并与 gateway 鉴权阶段的黑名单检查 key 保持一致。

## 未完成范围

- 当前没有 `logout` 北向接口，因此 Token 黑名单没有形成完整对外闭环。
- 本轮只收口了黑名单 owner 存储能力，没有改变登录 / 刷新接口契约。
- 注册 / 登录的对外安全基线仍未整体验收完成。
- 当前不应把 `identity-service` 表述为“安全专项已完成”或“可直接对外生产使用”。
