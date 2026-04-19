# falconx-gateway

## 模块职责

`falconx-gateway` 是 FalconX 的统一北向入口服务，负责：

- 统一 JWT 鉴权与用户透传头组装
- `traceId` 生成与透传
- 下游服务路由转发
- 认证入口基础限流
- 下游超时 / 熔断 / fallback 收敛

## 当前真实状态

- `Stage 4` 网关联调已完成。
- `Stage 7` 当前范围已验收，但这不等于 gateway 已完成全平台联调。
- `Stage 6B` 已开始但未验收；当前 gateway 已具备安全基线的部分能力，但仍有剩余缺口。

## 已落地能力

- 已建立 `/api/v1/auth/**`、`/api/v1/market/**`、`/api/v1/trading/**`、`/api/v1/wallet/**` 路由。
- 已建立 JWT 鉴权过滤器、`traceId` 透传过滤器与统一错误响应。
- gateway 会在鉴权阶段检查 Access Token `jti` 是否命中 Redis 黑名单 key。
- `/api/v1/auth/login` 与 `/api/v1/auth/register` 已落地每 IP 每分钟 20 次限流。
- identity / market / trading / wallet 四条路由已补 `CircuitBreaker`、`connect-timeout`、`response-timeout` 与统一 fallback。
- 下游服务超时或不可用时，会通过 `GatewayFallbackController` 统一返回 `90002`，不会把原始异常直接泄漏给客户端。
- 已通过网关侧验证：
  - 公开认证路由可直接透传。
  - 受保护路由无 Token 会被拒绝。
  - 合法 Token 会向下游透传 `X-User-*` 与 `X-Trace-Id`。
  - `gateway + identity-service + trading-core-service + Kafka` 的代表性 E2E 已通过：
    - `TC-E2E-001`：`注册 -> wallet.deposit.confirmed -> trading.deposit.credited -> 激活 -> 登录 -> 账户查询 -> 开仓`
    - `TC-E2E-010`：`注册 -> 入金 -> 激活 -> 登录 -> 带 TP 开仓 -> market.price.tick -> TP 自动平仓`
    - `TC-E2E-011`：`注册 -> 入金 -> 激活 -> 登录 -> 10x 开仓 -> market.price.tick -> 强平`

## 未完成范围

- 当前没有 `logout` 北向接口，因此 Token 黑名单没有完整对外闭环。
- `/api/v1/trading/**` 用户级限流未完成。
- 全局按 IP 的兜底限流未完成。
- 当前代表性 E2E 不包含 `wallet-service` 与 `market-service` 真运行时同时参测。
- 当前不应把 gateway 表述为“全平台生产可用”，也不应把 `/api/v1/market/**` 或 `/api/v1/wallet/**` 的真运行时北向路由验证写成已完成。
