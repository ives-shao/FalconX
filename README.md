# FalconX

FalconX 当前处于 `v1` 架构与基础设施底座已落地阶段。

一期采用的正式架构名称是：

`GODSA (Gateway-Orchestrated Domain Core Services Architecture)`

中文名称：

`网关编排的领域核心服务架构`

## 当前状态

当前已完成：

- `Stage 1`：共享模块与服务骨架
- `Stage 2A`：`market-service` 市场数据底座
- `Stage 2B`：`wallet-service` 钱包事件底座
- `Stage 3A`：`identity-service` 身份底座
- `Stage 3B`：`trading-core-service` 交易核心底座
- `Stage 4`：`gateway` 网关与服务框架集成
- `Stage 5`：数据持久化与基础设施接入
- `Stage 6A`：已完成 `market-service` 的 Tiingo 真连接与真实 K 线聚合子项

当前已接入的真实基础设施：

- MySQL owner schema
- Redis / Redisson
- ClickHouse
- Flyway migration
- Kafka 客户端与服务内事件收发

当前仍未进入的范围：

- 真实链上扫块、交易解析与确认推进
- 真实业务 Topic 级联调
- Swap / 隔夜利息结算
- 生产态安全配置

## 文档阅读顺序

建议后续开发人员按下面顺序理解项目：

1. 架构总览  
   - [FalconX v1 GODSA 最终架构方案](docs/architecture/falconx一期网关-服务-数据库架构方案.md)

2. 数据库边界  
   - [FalconX v1 数据库设计](docs/database/falconx一期数据库设计.md)
   - [V1__init_schema.sql](docs/sql/V1__init_schema.sql)
   - [V2__seed_symbols.sql](docs/sql/V2__seed_symbols.sql)
   - [CH_V1__market_analytics.sql](docs/sql/CH_V1__market_analytics.sql)

3. 接口与安全规则  
   - [REST 接口规范](docs/api/REST接口规范.md)
   - [WebSocket 接口规范](docs/api/WebSocket接口规范.md)
   - [FalconX统一接口文档](docs/api/FalconX统一接口文档.md)
   - [安全规范](docs/security/安全规范.md)

4. 事件、一致性与状态  
   - [Kafka 事件规范](docs/event/Kafka事件规范.md)
   - [状态机规范](docs/domain/状态机规范.md)
   - [事务与幂等规范](docs/architecture/事务与幂等规范.md)
   - [Tiingo 报价与实时市场数据契约](docs/market/tiingo报价接入契约.md)

5. 开发约束  
   - [FalconX v1 编码与测试规范](docs/architecture/falconx编码与测试规范.md)
   - [FalconX v1 日志打印规范](docs/architecture/日志打印规范.md)
   - [FalconX v1 完成定义](docs/process/完成定义.md)
   - [FalconX ADR 目录](docs/adr/README.md)

6. 实施与环境  
   - [开发启动手册](docs/setup/开发启动手册.md)
   - [当前开发计划](docs/setup/当前开发计划.md)
   - [Jackson 3 迁移计划](docs/process/Jackson3迁移计划.md)
   - [本地基础设施启动说明](docs/setup/本地基础设施启动说明.md)

## 实施入口

后续继续实施时，只按两份文档推进：

1. [FalconX v1 GODSA 最终架构方案](docs/architecture/falconx一期网关-服务-数据库架构方案.md)
2. [开发启动手册](docs/setup/开发启动手册.md)
