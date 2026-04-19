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
  - `POST /api/v1/auth/logout`
- `Stage 7` 当前范围已验收。
- `Stage 6B` 当前范围已验收，但这不等于全平台生产可用口径已完成。

## 已落地能力

- 注册、登录、刷新 Token 主链路已落地。
- Refresh Token 会话已持久化，并支持一次性使用控制。
- 已通过真实 Kafka 入口消费 `falconx.trading.deposit.credited`，默认消费组为 `falconx.identity-service.deposit-credited-consumer-group`，支持用户激活、重复 `eventId` 幂等、缺失用户失败重试和 `ACTIVE` 幂等跳过。
- RSA 密钥与数据库账号密码已改为环境变量注入。
- 已落地登录失败锁定策略。
- 已具备内部 Token 黑名单 Redis 存储能力，并与 gateway 鉴权阶段的黑名单检查 key 保持一致。
- `POST /api/v1/auth/logout` 已落地，可把当前 Access Token 的 `jti` 按剩余 TTL 写入黑名单；同一 Access Token 再经 gateway 访问受保护接口会返回 `10001`。

## 未完成范围

- 当前 `logout` 只吊销当前 Access Token，不引入 Refresh Token 主动撤销语义。
- 本轮没有改变既有 `register / login / refresh` 契约。
- 当前范围虽已验收，但这不等于 `wallet-service` 与 `market-service` 真运行时已纳入同一 E2E，也不等于全平台生产可用。
- 当前不应把 `identity-service` 表述为“安全专项已完成”或“可直接对外生产使用”。
