# falconx-gateway

## 1. 模块职责

`falconx-gateway` 是 FalconX 的统一北向入口服务。

当前阶段已完成 Stage 4 的最小网关联调闭环。

后续应在本服务中补充：

- 聚合查询
- 统一错误响应
- 更细粒度的路由治理与限流

## 2. 包结构

- `config`
- `security`
- `filter`
- `controller`
- `client`
- `assembler`
- `exception`

## 3. 主调用链

`Client -> Gateway Filter -> Security -> Route/Controller -> Downstream Client -> Response Assembler -> Client`

## 4. 当前状态

- Stage 1 可启动骨架已建立
- Stage 4 网关联调已完成
- 已建立 `/api/v1/auth/**`、`/api/v1/market/**`、`/api/v1/trading/**`、`/api/v1/wallet/**` 路由
- 已建立 JWT 鉴权过滤器、`traceId` 生成与透传过滤器
- 已验证：
  - 公开认证路由可直接透传
  - 受保护路由无 Token 会被拒绝
  - 合法 Token 会向下游透传 `X-User-*` 与 `X-Trace-Id`
