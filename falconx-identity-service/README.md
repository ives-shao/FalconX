# falconx-identity-service

## 模块职责

`falconx-identity-service` 负责身份域 owner 能力：

- 用户注册、登录、Token 刷新
- 用户状态维护
- 基于业务事件的用户激活

## 当前真实状态

- 身份域最小业务闭环已落地，owner MySQL、Flyway、`MyBatis + XML Mapper` 已接入。
- 当前已对外提供：
  - `POST /api/v1/auth/register`
  - `POST /api/v1/auth/login`
  - `POST /api/v1/auth/refresh`
- JWT 当前由服务内 RS256 密钥对签发，`Stage 6B` 的安全完整化尚未完成。

## 已落地能力

- 注册、登录、刷新 Token 的主链路已落地。
- Refresh Token 会话已持久化，并支持一次性使用控制。
- 已通过真实 Kafka 入口消费 `falconx.trading.deposit.credited`，默认消费组为 `falconx.identity-service.deposit-credited-consumer-group`，支持用户激活、重复 `eventId` 幂等、缺失用户失败重试和 `ACTIVE` 幂等跳过。
- 访问 Token 已具备 Gateway 鉴权所需的基础声明字段。

## 未完成范围

- RSA 密钥外部化尚未完成。
- Token 黑名单能力尚未完成。
- 注册/登录限流与生产态安全配置尚未完成。
