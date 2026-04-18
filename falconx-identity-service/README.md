# falconx-identity-service

## 1. 模块职责

`falconx-identity-service` 负责用户身份域能力。

后续应在本服务中补充：

- 邮箱密码注册
- 邮箱密码登录
- JWT 签发与刷新
- 用户状态查询与维护
- 消费 `falconx.trading.deposit.credited` 完成用户激活

## 2. Owner 数据

- `falconx_identity.t_user`
- `falconx_identity.t_inbox`

## 3. 包结构

- `controller`
- `consumer`
- `application`
- `service`
- `repository`
- `entity`
- `dto`
- `command`
- `query`
- `config`

## 4. 主调用链

`Controller -> Application Service -> Domain Service -> Repository -> DB`

## 5. 当前状态

- Stage 1 可启动骨架已建立
- Stage 3A 身份服务骨架已建立
- Flyway migration 目录骨架已建立
- 已建立注册、登录、Refresh Token、用户激活骨架
- 已建立进程内 RSA JWT 签发骨架与 `deposit.credited` 消费骨架
