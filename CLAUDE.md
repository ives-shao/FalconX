# FalconX 仓库规则

## 1. 目的

本文件用于把当前 FalconX 仓库中的架构、数据库、接口、事件、编码、日志、测试与交付规则固化为默认实施规范。

后续任何 AI 编码代理在本仓库中工作时，都必须先读取并遵守本文件，再进行分析、设计、编码、测试或文档更新。

## 2. 规范来源

下列文档是 FalconX 的正式规范来源，不是参考资料。

### 2.1 架构与实施顺序

- [docs/architecture/falconx一期网关-服务-数据库架构方案.md](docs/architecture/falconx一期网关-服务-数据库架构方案.md)
- [docs/setup/开发启动手册.md](docs/setup/开发启动手册.md)

### 2.2 数据库与存储

- [docs/database/falconx一期数据库设计.md](docs/database/falconx一期数据库设计.md)
- [docs/sql/V1__init_schema.sql](docs/sql/V1__init_schema.sql)
- [docs/sql/V2__seed_symbols.sql](docs/sql/V2__seed_symbols.sql)
- [docs/sql/CH_V1__market_analytics.sql](docs/sql/CH_V1__market_analytics.sql)

### 2.3 接口、安全、事件与状态

- [docs/api/REST接口规范.md](docs/api/REST接口规范.md)
- [docs/api/WebSocket接口规范.md](docs/api/WebSocket接口规范.md)
- [docs/api/FalconX统一接口文档.md](docs/api/FalconX统一接口文档.md)
- [docs/security/安全规范.md](docs/security/安全规范.md)
- [docs/event/Kafka事件规范.md](docs/event/Kafka事件规范.md)
- [docs/domain/状态机规范.md](docs/domain/状态机规范.md)
- [docs/architecture/事务与幂等规范.md](docs/architecture/事务与幂等规范.md)
- [docs/market/tiingo报价接入契约.md](docs/market/tiingo报价接入契约.md)

### 2.4 编码、日志、测试与交付

- [docs/architecture/falconx编码与测试规范.md](docs/architecture/falconx编码与测试规范.md)
- [docs/architecture/日志打印规范.md](docs/architecture/日志打印规范.md)
- [docs/process/完成定义.md](docs/process/完成定义.md)
- [docs/process/统一问题清单.md](docs/process/统一问题清单.md)

### 2.5 架构决策

- [docs/adr/README.md](docs/adr/README.md)

### 2.6 当前进度（状态文档，非规范来源）

- [docs/setup/当前开发计划.md](docs/setup/当前开发计划.md)

> 说明：当前开发计划记录阶段完成状态与下一步范围，属于进度跟踪文档。代理应以此了解项目所处阶段，但不得将"已完成"的历史状态当作当前约束执行。规范约束以 §2.1–§2.5 为准。

## 3. 强制工作规则

1. 不允许凭记忆或猜测直接开始编码，非简单改动先读再改。
2. 任何功能开始前，必须先读取主架构文档、开发启动手册，以及与当前任务直接相关的专题规范。
3. 任务如果涉及 API、数据库、事件、状态机、日志、测试、接口文档，必须先读取对应文档再修改代码。
4. 若文档之间存在冲突，先修文档，再写代码。
   4.1 不过度重构
5. 不允许写出违反服务边界、数据 owner、事件 owner 的实现。
6. 不允许把业务 DTO、Entity、Mapper、Repository 上收进 `common` 或 `infrastructure`，除非架构文档明确允许。
7. 业务代码（Service / Application 层）的数据库查询一律通过 `MyBatis + XML Mapper` 实现；禁止在 Service / Application / Controller 层拼接 SQL 字符串。测试辅助类与工具脚本不受此限制。
8. 一旦进入真实实现阶段，数据库查询必须通过 owner 服务的真实 `Mapper + XML + Repository` 链路完成，禁止用 `In-Memory Repository` 代替正式查询实现。
9. 可以抽象复用的代码尽量复用，但复用不能破坏服务 owner 边界；禁止跨服务共用业务 Mapper、业务 Repository 或查询实现。
   9.1 产品元数据、可交易 symbol 列表、品种分类与市场代码等 owner 数据，必须来自正式 owner 数据源（数据库、owner 写入的 Redis 快照、或冻结后的 contract），禁止在运行时代码中通过 `List.of(...)`、本地静态白名单、配置默认值等方式硬编码返回。
   9.2 若某服务需要消费另一服务的产品配置结果，应优先使用 owner 产出的正式共享数据（如 market 写入的 Redis 快照），不得在本地再维护一份静态产品白名单。
   9.3 `market-service` 的 Tiingo 本地放行白名单必须只依赖 `t_symbol.status=1` 的 owner 数据，并支持运行时热刷新；数据库启停状态变化后，不允许再要求通过重启服务才能生效。
   9.4 若修改的是原始基线 migration 或 SQL 蓝图（如 `V1 / V2`），必须同时处理 Flyway checksum 影响。测试或调试环境不得静默复用已应用旧 checksum 的库；应切换到新的隔离测试库，或在得到明确授权后执行 repair。
10. 不允许未更新文档就新增主题名、错误码、包结构、状态码、存储模式、接口契约。
11. 不允许代理自行扩展任务范围、增加用户未明确要求的实现内容或替用户做架构外决策。
12. 若决策会影响以下任一项，必须先向用户确认，再继续实施：接口契约（路径 / 方法 / 字段）、数据库 schema（新增表 / 字段 / 索引）、Kafka 事件 payload、包结构、错误码、状态码。纯内部实现细节（算法选择、私有方法拆分等）不需要确认，选择最符合规范的路径即可。
13. 若任务会改变阶段状态、阶段验收或下一步实施顺序，必须同步更新 [docs/setup/当前开发计划.md](docs/setup/当前开发计划.md)。
14. 不允许把“骨架完成”表述成“真实基础设施已打通”或“生产可用”。
15. 若当前实现仍基于 `In-Memory Repository`、`stub provider / listener` 或日志型 publisher，必须在计划与结论中显式说明。
16. 阶段完成后不得留下“下一步为空白”的计划真空，必须把下一阶段范围写入计划文档。
17. 若用户要求“读取某份问题 / 审计 / 补充事项文档并修复”，必须先逐条核对问题是否真实存在，再区分为：
- `真实存在，需要修复`
- `已修复，仅需同步文档`
- `不成立，不得误修`
  未完成逐条核对前，不得直接开始批量改代码。
  17.1 后续新增问题、审计项、整改项，默认统一收敛到 [docs/process/统一问题清单.md](docs/process/统一问题清单.md)。
  17.2 若用户未明确指定问题文档，且任务是“统一读取问题并修复”，默认优先读取该文件。
18. 对 Redis 快照、Kafka payload、跨服务共享 JSON 结构的修改，必须同时考虑跨服务兼容性：
- 新增字段默认不得导致现有消费方反序列化失败
- 若存在跨服务共享缓存或事件模型，优先保证消费方可忽略未知字段，或通过 `contract` 模块显式冻结 DTO
- 修改后必须验证生产者与消费者的联通性，不能只测单边服务
  18.1 对来自 WebSocket、SDK、客户端库或外部监听器线程的回调，禁止直接在回调线程里执行完整的业务消费链路（Kafka、Redis、DB、ClickHouse、复杂应用服务）。必须先切换到应用自管线程或执行器，再进入后续处理；若下游组件依赖 TCCL、MDC 或其他线程上下文，也必须显式恢复。
19. 任何缓存型实现都必须明确 3 件事后才算完成：
- TTL
- 刷新策略（定时 / 事件驱动 / 启动预热）
- cache miss 时的业务降级策略
  若这三项缺任一项，不得在结论中写“已完成”。
20. 执行验证命令时，禁止并行运行会相互影响构建产物的命令，尤其是：
- `mvn test`
- `mvn clean compile`
- 其他会清理同一 `target/` 目录的命令
  这类命令必须串行执行，避免把环境冲突误判成代码问题。
21. 在不违背既有架构、服务边界和稳定性要求的前提下，新增代码必须优先采用 `JDK 25` 与 `Spring Boot 4` 的正式能力与性能方案，不得无故沿用明显过时的旧写法。若选择不使用更合适的新特性，必须有明确原因（兼容性、可读性、约束冲突或官方不推荐）。
22. JSON 技术栈当前有专项迁移约束：
- 目标版本是 `Jackson 3.x`
- 当前仓库实际仍运行在 `Jackson 2.21.2` 兼容路径
- 在未完成专项迁移前，不得宣称“项目已统一到 Jackson 3+”
- 不得新增 Jackson 2 的自定义版本覆盖、额外变种依赖或与 `Spring Boot` BOM 冲突的私自升级
- 若要真正切换到 Jackson 3，必须单独评估 `com.fasterxml.jackson.* -> tools.jackson.*` 迁移影响，并先得到用户确认
23. 若本轮处理过真实问题、架构偏差或测试环境陷阱，必须把结论回写到 [docs/process/统一问题清单.md](docs/process/统一问题清单.md)，不得只保留在会话上下文中。
    23.1 若外部行情源、链节点或其他第三方源提供的是“全量订阅 + 本地过滤”模型，运行时放行范围必须依赖 owner 数据源中的启用品种，不得再按固定 `market_code`、手写枚举或单一资产类别假设做硬编码过滤。

## 4. 当前实施约束

1. FalconX v1 采用 `GODSA`。
2. 一期服务固定为：
    - `falconx-gateway`
    - `falconx-identity-service`
    - `falconx-market-service`
    - `falconx-trading-core-service`
    - `falconx-wallet-service`
3. 一期共享模块固定为：
    - `falconx-common`
    - `falconx-domain`
    - `falconx-infrastructure`
    - 各服务 `contract` 模块
4. `market-service` 负责：
    - 报价标准化
    - Redis 市场缓存
    - ClickHouse 报价历史
    - ClickHouse K 线历史
5. `trading-core-service` 负责：
    - 账户
    - 账本
    - 入金入账
    - 订单
    - 持仓
    - 成交
    - 风控
    - 强平
6. `wallet-service` 只管理链上原始事实，不管理最终业务入账事实。
7. `price.tick` 属于高频事件：
    - 直接发 Kafka
    - 不写 `t_outbox`
    - 不写 `t_inbox`
8. 低频关键事件必须使用 `Outbox + Inbox`。

## 5. 代码质量规则

1. 所有 Java 文件都必须有清晰注释。
   详细业务注释重点覆盖：
    - 公开类与公开方法
    - 复杂业务逻辑
    - 状态迁移
    - 外部依赖调用
    - 事务边界、幂等点和副作用
      简单 DTO、record、配置类和测试支撑类至少应具备类注释与必要字段/用途说明，不要求机械重复事务边界说明。
2. 所有核心逻辑都必须有测试：
    - 单元测试
    - 集成测试
    - 关键链路测试
3. 所有关键业务节点都必须按日志规范打日志。
4. `traceId` 必须由系统自动生成，并通过日志上下文自动输出。
5. 不允许在业务代码中反复手工拼接 `traceId`。
6. 阶段验收结论不能只写命令名，必须同时写清楚对应的业务链路验证结果。
7. 需要重复出现的转换、校验、查询组装、日志字段组装与 XML SQL 片段，优先做受控复用，避免同层重复实现。SQL 公共片段（`<sql>` 标签）应集中放在对应服务的 `*BaseMapper.xml` 或专属 `*SqlFragments.xml` 中，通过 `<include>` 引用，禁止在各 XML 中各自复制粘贴。
8. 涉及交易时间、状态机、规则引擎、费用结算、行情时效等规则型逻辑时，测试不能只覆盖主路径，至少要补齐文档列出的边界场景；如果文档已有检查清单，必须按清单逐项关闭。
9. 复杂业务代码必须补充块级注释，至少说明业务意图、关键分支、状态变化、账务影响或幂等边界；禁止只留下“做了什么”而不解释“为什么这样做”。

## 6. 接口交付规则

1. 每次接口开发并测试通过后，必须按照文档分类同步更新接口文档，如果没有当前分类，则新建一个：
    - [docs/api/FalconX统一接口文档.md](docs/api/FalconX统一接口文档.md)
2. 这里的“接口”包括：
    - HTTP REST 接口
    - WebSocket 接口
    - 对外暴露的内部接口（若后续启用）
3. 未更新统一接口文档，不视为接口任务完成。
4. 统一接口文档必须包含：
    - 所属服务
    - 接口名称
    - 请求路径
    - 请求方法
    - 认证要求
    - 请求头
    - 请求参数或请求体
    - 成功响应示例
    - 失败响应与错误码
    - 关键日志点
    - 测试结论

## 7. 冲突处理优先级

若多个文档同时相关，按下面顺序处理：

1. 架构文档
2. 开发启动手册
3. 数据库设计与 SQL 蓝图
4. REST / WebSocket / 安全 / Kafka / 状态机 / 事务规范
5. 编码 / 日志 / 测试 / 完成定义
6. ADR

若仍冲突，不允许自行猜测，必须先修正文档。

## 8. 交付要求

任务完成前必须做以下检查：

1. 说明本次实现遵循了哪些核心文档。
2. 验证代码与当前文档一致。
3. 运行相关验证：
    - 构建验证
    - 测试验证
    - `docker compose` 验证（仅在涉及基础设施变更时需要：新增 schema、修改容器配置、新增端口映射等）
    - 服务链路验证
4. 若无法验证，必须明确说明原因。
5. 若任务包含接口开发，必须确认统一接口文档已更新。
6. 若本轮存在未在用户要求中明确授权的事项，必须暂停并等待用户决定，不得自行推进。
7. 若本轮起点是问题清单或审计文档，交付结论必须明确写出：
    - 哪些问题真实存在
    - 哪些问题不再存在
    - 哪些只需要文档修正
      并同步回原问题文档状态。

## 9. 人工协作补充约束

1. 提交代码前必须对照 [docs/process/完成定义.md](docs/process/完成定义.md) 自检。
2. 若使用 Git 平台提交流程，优先使用仓库中的 PR 模板。
3. 不允许以“后续再补文档/测试/日志”为理由跳过当前交付要求。
