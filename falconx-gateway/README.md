# falconx-gateway

## 模块职责

`falconx-gateway` 是 FalconX 的统一北向入口服务，负责：

- 统一 JWT 鉴权与用户透传头组装
- `traceId` 生成与透传
- 下游服务路由转发
- 认证入口与交易路径限流
- 下游超时 / 熔断 / fallback 收敛

## 当前真实状态

- `Stage 4` 网关联调已完成。
- `Stage 6A` 主链路收口已完成。
- `Stage 6B` 当前冻结范围已收口：gateway 已补齐北向行情 WebSocket 握手鉴权、连接限制与代理链路，但这不等于全平台联调或生产可用口径已完成。

## 已落地能力

- 已建立 `/api/v1/auth/**`、`/api/v1/market/**`、`/api/v1/trading/**`、`/api/v1/wallet/**` 路由。
- 已建立 JWT 鉴权过滤器、`traceId` 透传过滤器与统一错误响应。
- gateway 会在鉴权阶段检查 Access Token `jti` 是否命中 Redis 黑名单 key。
- `/api/v1/auth/login` 与 `/api/v1/auth/register` 已落地每 IP 每分钟 20 次限流。
- `/api/v1/trading/**` 已落地每用户每秒 10 次限流，超限返回 `429 / 10012 / Trading Rate Limited`。
- 所有 `/api/v1/**` 已落地每 IP 每分钟 200 次全局兜底限流，超限返回 `429 / 10013 / Global IP Rate Limited`。
- identity / market / trading / wallet 四条路由已补 `CircuitBreaker`、`connect-timeout`、`response-timeout` 与统一 fallback。
- 下游服务超时或不可用时，会通过 `GatewayFallbackController` 统一返回 `90002`，不会把原始异常直接泄漏给客户端。
- `POST /api/v1/auth/logout` 已可经 gateway 路由到 identity-service；成功后同一 Access Token 再访问受保护接口会返回 `10001`。
- 已落地 `ws://{host}/ws/v1/market?token=<accessToken>`：
  - 握手阶段校验 Access Token
  - `BANNED` 用户返回 `HTTP 403`
  - 同一用户最多 5 个并发 market WebSocket 连接，第 6 个返回 `HTTP 429`
  - 成功握手后向 `market-service` 透传 `X-User-* / X-Trace-Id`
  - 代理链路记录 `gateway.websocket.*` 结构化日志
- 已通过网关侧验证：
  - 公开认证路由可直接透传。
  - 受保护路由无 Token 会被拒绝。
  - 合法 Token 会向下游透传 `X-User-*` 与 `X-Trace-Id`。
  - `GatewayRoutingIntegrationTests` 已验证认证入口限流、trading 用户级限流、全局 IP 兜底限流，以及 `logout -> blacklist -> gateway reject` 闭环。
  - `GatewayMarketWebSocketIntegrationTests` 已验证 market WebSocket 的 `401 / 403 / 429` 握手结果、头透传与连接限制。
  - 代表性 E2E 已纳入 `wallet-service` 与 `market-service` 真运行时参测，当前通过的是：
    - `TC-E2E-001`：`注册 -> wallet.deposit.confirmed -> trading.deposit.credited -> 激活 -> 登录 -> 账户查询 -> 开仓`
    - `TC-E2E-010`：`注册 -> 入金 -> 激活 -> 登录 -> 带 TP 开仓 -> market.price.tick -> TP 自动平仓`
    - `TC-E2E-011`：`注册 -> 入金 -> 激活 -> 登录 -> 10x 开仓 -> market.price.tick -> 强平`

## 未完成范围

- 当前 `logout` 只吊销当前 Access Token，不引入新的 Refresh Token 主动撤销语义。
- 当前边界是：外部链节点真扫块与 Tiingo 外部真源还未纳入同一自动化用例。
- 当前只冻结并实现 `market` 行情 WebSocket；账户 / 订单 / 持仓 / 费用等用户侧实时推送端点尚未冻结正式契约。
- 当前不应把 gateway 表述为“全平台生产可用”，也不应把 `/api/v1/market/**` 或 `/api/v1/wallet/**` 的所有真运行时北向链路验证写成已完成。
