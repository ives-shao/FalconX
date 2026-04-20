# FalconX 开发技能手册（SKILLS.md）

本文件为 FalconX 项目常见开发任务提供可复用的操作模板。
每个 Skill 描述"做什么、在哪里做、遵循什么约束"，可直接驱动 AI 代理执行。

---

## Skill 1：新增 REST 接口

### 适用场景
在某个业务服务中增加一个新的 HTTP API 端点。

### 操作步骤

1. **读取规范**
   - `docs/api/REST接口规范.md`
   - `docs/api/FalconX统一接口文档.md`

2. **新增 Command 对象**（如果是写操作）
   ```
   路径：com.falconx.{service}.command.XxxCommand.java
   规则：Record 或普通 POJO，不含业务逻辑
   ```

3. **新增/修改 ApplicationService**
   ```
   路径：com.falconx.{service}.application.XxxApplicationService.java
   规则：
   - 负责编排，不含领域规则
   - 数据库操作必须通过 Repository 接口
   - 方法开头打 INFO 日志（含 traceId 由 MDC 自动携带）
   ```

4. **新增/修改 Controller**
   ```
   路径：com.falconx.{service}.controller.XxxController.java
   规则：
   - 只做参数绑定、委托 ApplicationService、包装 ApiResponse
   - 不含任何业务逻辑
   - 返回类型：ApiResponse<T>
   ```

5. **错误码**（如有新错误场景）
   ```
   路径：com.falconx.{service}.error.XxxErrorCode.java（enum，implements ErrorCode）
   ```

6. **写单元测试 + 集成测试**
   ```
   路径：src/test/java/com/falconx/{service}/controller/XxxControllerTests.java
   ```

7. **同步接口文档**
   ```
   文件：docs/api/FalconX统一接口文档.md
   必填字段：所属服务、接口名称、路径、方法、认证要求、请求头、
            请求参数/体、成功响应示例、失败响应与错误码、关键日志点、测试结论
   ```

---

## Skill 2：新增 Kafka 事件（低频关键事件，走 Outbox/Inbox）

### 适用场景
服务间解耦的业务事件，如存款确认、订单状态变更等。

### 操作步骤

1. **读取规范**
   - `docs/event/Kafka事件规范.md`

2. **在 contract 模块定义 Payload**
   ```
   路径：falconx-{service}-contract/src/main/java/com/falconx/{service}/contract/event/XxxEventPayload.java
   规则：
   - 只含字段，无业务逻辑
   - 新增字段必须有默认值，保证消费方向后兼容
   - 使用 Jackson 注解：@JsonIgnoreProperties(ignoreUnknown = true)
   ```

3. **生产者：写入 t_outbox**
   ```
   路径：com.falconx.{service}.producer.XxxEventProducer.java
   规则：
   - 在业务事务内写 outbox 记录（与业务操作同一个事务）
   - 不在此处直接发 Kafka
   - OutboxRetryDelayPolicy 处理重试间隔
   ```

4. **消费者：写入 t_inbox 并处理**
   ```
   路径：com.falconx.{service}.consumer.XxxEventConsumer.java
   规则：
   - @KafkaListener 回调只负责提取消息并切换线程
   - 业务处理委托给 ApplicationService（在应用线程池中执行）
   - 幂等处理：先检查 inbox 是否已处理
   ```

5. **新增 Topic**
   - 更新 `docs/event/Kafka事件规范.md`
   - 更新 `CLAUDE.md` §5 Topic 表格
   - 在对应服务 `application.yml` 中注册 topic 名称配置项

---

## Skill 3：新增数据库表

### 适用场景
需要在某个服务的 MySQL schema 中增加新表或字段。

### 操作步骤

1. **读取规范**
   - `docs/database/falconx一期数据库设计.md`

2. **新建 Flyway migration**
   ```
   路径：{service}/src/main/resources/db/migration/V{N+1}__{描述}.sql
   规则：
   - N 必须是当前最大版本号 + 1（查看已有文件确认）
   - 禁止修改已提交的 V{N}__ 文件（会导致 checksum 错误）
   - 所有字段必须有 COMMENT
   - 时间字段：created_at、updated_at（TIMESTAMP, DEFAULT CURRENT_TIMESTAMP）
   ```

3. **新增 Entity**
   ```
   路径：com.falconx.{service}.entity.XxxEntity.java
   规则：
   - 字段名与数据库列名对应（@TableField 或 MyBatis resultMap）
   - 枚举字段用 @EnumValue 或 TypeHandler
   ```

4. **新增 Mapper Interface**
   ```
   路径：com.falconx.{service}.repository.mapper.XxxMapper.java
   规则：继承 BaseMapper<XxxEntity>（MyBatis Plus）
   ```

5. **新增 XML Mapper**
   ```
   路径：{service}/src/main/resources/mapper/XxxMapper.xml
   规则：
   - namespace 与 Mapper interface 全路径一致
   - 公共列片段用 <sql id="baseColumns"> 定义
   - 复杂查询写 XML，简单 CRUD 用 MyBatis Plus 内置方法
   ```

6. **新增 Repository 接口 + MyBatis 实现**
   ```
   接口：com.falconx.{service}.repository.XxxRepository.java
   实现：com.falconx.{service}.repository.MybatisXxxRepository.java
   规则：ApplicationService 只依赖接口，不依赖 Mapper
   ```

7. **同步数据库文档**
   ```
   文件：docs/database/falconx一期数据库设计.md
   ```

---

## Skill 4：新增缓存（Redis）

### 适用场景
为查询热点数据或跨服务共享状态引入 Redis 缓存。

### 三件必明确（缺一不算完成）

| 项目 | 要求 |
|------|------|
| TTL | 必须在代码/配置中显式指定，禁止 no-expiry |
| 刷新策略 | 定时刷新 / 事件驱动更新 / 启动预热，三选一或组合 |
| cache miss 降级 | 回源 DB 查询 / 返回默认值 / 拒绝服务，必须明确 |

### 操作步骤

1. 在 `application.yml` 中定义 TTL 配置项（禁止硬编码）
2. 使用 `Redisson`（`RedissonClient`）操作 Redis，不混用 `StringRedisTemplate`
3. 缓存 key 格式：`falconx:{service}:{entity}:{id}`，文档化到对应服务 README 或注释
4. owner 数据（如可交易品种）只能由 owner 服务写入缓存，其他服务只读
5. 修改 Redis 快照 JSON 结构前，评估跨服务消费方兼容性

---

## Skill 5：新增 Wallet 链支持

### 适用场景
为 `wallet-service` 接入新的区块链。

### 操作步骤

1. 在 `falconx-domain` 的 `ChainType` 枚举中新增链类型
2. 在 `wallet-service` 的 `client/` 下新增链客户端实现
3. 在 `wallet-service` 的 `listener/` 下新增链监听器
   - **回调线程只做消息接收，立即提交到 `ExecutorService`，不在回调线程执行业务**
4. 在 `WalletServiceProperties` 中新增链配置（RPC URL、确认数、cursor-type）
5. 新建 Flyway migration 处理新链的地址/游标表变更
6. 更新 `application.yml` 和 `application-local.yml`

---

## Skill 6：新增风险控制规则

### 适用场景
在 `trading-core-service` 中新增或修改风险控制逻辑。

### 操作步骤

1. **读取规范**
   - `docs/domain/状态机规范.md`
   - `docs/architecture/事务与幂等规范.md`

2. 风险规则在 `TradingRiskService` 中实现
3. 风险参数来自 `t_risk_config`，通过 `TradingRiskConfigMapper` 读取，**禁止硬编码风险参数**
4. 强平逻辑经过 `TradingLiquidationLog` 留存记录
5. 实时风控指标写 Redis（`TradingRiskExposure`），需满足 Skill 4 三件必明确
6. 测试必须覆盖：正常路径、边界保证金场景、多仓并发场景

---

## Skill 7：全链路验证

### 适用场景
跨服务改动或基础设施变更后的联通性验证。

### 操作步骤

```bash
# 1. 启动基础设施
docker compose up -d mysql redis kafka clickhouse

# 2. 等待健康检查通过（约 30s）

# 3. 按依赖顺序启动服务
mvn -pl falconx-identity-service spring-boot:run &
mvn -pl falconx-market-service spring-boot:run &
mvn -pl falconx-trading-core-service spring-boot:run &
mvn -pl falconx-wallet-service spring-boot:run &
mvn -pl falconx-gateway spring-boot:run &

# 4. 执行 E2E 测试（串行，不并行）
mvn -pl falconx-gateway test -Dtest=GatewayMinimalMainlineE2ETests

# 5. 验证 Kafka 事件流
# 使用 kafka-console-consumer 消费 falconx.market.price.tick 确认行情流通
```

### 验证矩阵

| 场景 | 验证命令/接口 |
|------|-------------|
| 注册登录 | POST /api/v1/auth/register → POST /api/v1/auth/login |
| 行情推送 | Kafka topic: falconx.market.price.tick |
| 下单 | POST /api/v1/trading/orders → 检查 t_order 状态 |
| 存款入账 | wallet→Kafka→trading→Kafka→identity 完整链路 |
| 强平触发 | GatewayLiquidationE2ETests |

---

## Skill 8：问题清单修复流程

### 适用场景
收到问题清单、审计报告或整改要求时。

### 操作步骤

1. **先读取** `docs/process/统一问题清单.md`（若未指定其他文档）
2. **逐条核对**，分类为：
   - `真实存在，需要修复` → 修复代码
   - `已修复，仅需同步文档` → 更新文档状态
   - `不成立，不得误修` → 标注原因并跳过
3. **禁止未完成逐条核对直接开始批量改代码**
4. 修复完成后回写问题清单状态（已解决 / 不适用 / 日期）
5. 若本轮发现新问题，追加到 `docs/process/统一问题清单.md`

---

## Skill 9：接口文档同步

### 适用场景
任何 REST 或 WebSocket 接口开发、修改完成后。

### 文档位置
`docs/api/FalconX统一接口文档.md`

### 每条接口记录必须包含

```markdown
### {接口名称}

- **所属服务**：falconx-{service}
- **路径**：POST /api/v1/{resource}
- **认证**：Bearer JWT / 无
- **请求头**：X-Trace-Id（可选，网关自动注入）
- **请求体**：
  ```json
  { ... }
  ```
- **成功响应**：
  ```json
  { "code": 0, "data": {...} }
  ```
- **失败响应**：
  | 错误码 | HTTP Status | 说明 |
  |--------|------------|------|
  | XXX_001 | 400 | ... |
- **关键日志点**：[INFO] 操作描述 traceId={} userId={}
- **测试结论**：已通过 / 待测试
```

**未更新统一接口文档，不视为接口任务完成。**

---

## Skill 10：服务间 contract 变更

### 适用场景
需要修改跨服务共享的事件 Payload 或 API DTO。

### 变更原则

1. **只新增字段，不删除/重命名已有字段**（消费方可能正在使用）
2. 新字段必须有 `@JsonIgnoreProperties(ignoreUnknown = true)` 保护消费方
3. 若必须破坏性变更：
   - 在 contract 模块新建 V2 Payload 类
   - 生产者和消费者同步迁移，分两个 PR 上线
   - 旧 Payload 至少保留一个版本周期
4. 变更后必须同时验证生产者和消费者（不能只测单边）
5. 同步更新 `docs/event/Kafka事件规范.md` 或 `docs/api/REST接口规范.md`
