# market-service 报价源接入分析报告

- 分析日期：2026-04-18
- 分析范围：`falconx-market-service` 全部主流程代码
- 关联问题：统一问题清单 FX-036 ~ FX-042

---

## 1. 两个报价源概述

| 源 | WebSocket 地址 | 用途 | 是否进入 bid/ask 主链路 |
|----|---------------|------|----------------------|
| Tiingo FX | `wss://api.tiingo.com/fx` | 主报价链路，提供外汇/CFD 的实时 bid/ask | ✅ 是 |
| Tiingo Crypto | `wss://api.tiingo.com/crypto` | Symbol 发现工具，启动时一次性采样，把 crypto 交易对名称落库为 `status=2 suspended` | ❌ 否 |

Crypto WebSocket 的成交流（`data[0]="T"`）只包含成交价，不包含 bid/ask，因此**设计上正确地未接入标准报价主链路**。

---

## 2. FX 报价源（主链路）接入分析

### 2.1 报文格式解析

Tiingo FX 的 WebSocket 数组帧格式（项目内冻结样例）：
```
["Q","xptusd","2026-04-17T16:35:36.534000+00:00",1000000.0,2132.35,2135.25,1000000.0,2138.15]
  [0]   [1]              [2]                        [3]      [4]    [5]       [6]      [7]
  type ticker          timestamp                  bidSize  bidPrice  mid   askSize  askPrice
```

代码映射（`TiingoWebSocketProtocolSupport.toQuoteFromArray`）：
- `[1]` → ticker ✓
- `[2]` → timestamp ✓
- `[4]` → bid ✓
- `[7]` → ask ✓

解析位置**正确**。mid 价格由系统自行重算（`(bid+ask)/2`），Tiingo 提供的 `[5]` 字段被丢弃，符合"不依赖第三方中间价"原则。

### 2.2 主链路数据流

```
Tiingo FX WebSocket
    ↓ JdkTiingoQuoteProvider（TCCL 修复，切到应用管理线程）
    ↓ MarketDataIngestionApplicationService.ingest()
    ├── QuoteStandardizationService   → StandardQuote（stale 判定、mid/mark 计算）
    ├── RedisMarketQuoteCacheWriter   → Redis（TTL=10s，最新报价）
    ├── MybatisClickHouseAnalytics    → ClickHouse（批量写入，200条/500ms）
    ├── OutboxAwareMarketEventPublisher → Kafka直发 price.tick
    └── DefaultKlineAggregationService → K-line 聚合
              ↓ 收盘时
        ClickHouse kline 写入
        t_outbox（kline.update，走 Outbox）
```

### 2.3 已正确实现的部分

| 机制 | 实现位置 | 状态 |
|------|---------|------|
| TCCL 修复（避免 Kafka 类加载失败） | `JdkTiingoQuoteProvider.runWithApplicationClassLoader` | ✅ |
| 断线自动重连 | `scheduleReconnect` + `CompletableFuture.delayedExecutor` | ✅ |
| stale 报价检测（5s） | `DefaultQuoteStandardizationService` | ✅ |
| stale tick 不参与 K-line 聚合 | `DefaultKlineAggregationService.onQuote` | ✅ |
| ClickHouse 批量写入 | `MybatisClickHouseMarketAnalyticsWriter`（队列 + 双触发） | ✅ |
| 服务停止时排水 | `@PreDestroy flushRemainingQuoteTicks` | ✅ |
| symbol 热刷新（每分钟） | `MarketFeedBootstrapRunner.refreshTiingoSymbolWhitelist` | ✅ |
| price.tick 直发 / kline.update Outbox | `OutboxAwareMarketEventPublisher` | ✅ |
| K-line 只落收盘快照 | `DefaultKlineAggregationService` | ✅ |

### 2.4 发现的问题

#### FX-036（P1）：`toQuoteFromArray` 不验证 `messageType == "Q"`

**文件**：`TiingoWebSocketProtocolSupport.java:286`

```java
String messageType = node.get(0).asText();
if (messageType == null || messageType.isBlank()) {   // ← 只检查非空
    return Optional.empty();
}
// 未要求 messageType.equalsIgnoreCase("Q")
```

Tiingo FX 数组帧 `[0]` 应为 `"Q"`（bid/ask 报价帧）。若协议后续引入其他类型数组帧（如 `"T"` 成交帧），会被误解析为报价，将错误价格写入 Redis/Kafka/ClickHouse。

**修复**：
```java
if (!"Q".equalsIgnoreCase(messageType)) {
    return Optional.empty();
}
```

#### FX-037（P2）：`ingest()` 每 tick 打 INFO 日志，生产环境日志泛滥

**文件**：`MarketDataIngestionApplicationService.java:69,88`

```java
log.info("market.ingestion.received ticker={} ts={}", ...)  // 每条 tick
log.info("market.ingestion.completed symbol={} ...", ...)   // 每条 tick
```

Tiingo FX 真连接后行情频率较高，INFO 级别日志会淹没生产日志，影响告警排障。

**修复**：两行改为 `log.debug()`。

#### FX-038（P2）：K-line 内存状态重启后丢失，产生数据断口

**文件**：`DefaultKlineAggregationService.java`（`buckets = new ConcurrentHashMap<>()`）

进程内 K-line 桶状态（`KlineBucketState`）重启后清空。若当前 1m 窗口内已累积多条 tick，重启后这些 tick 的聚合状态丢失，重启后第一条 tick 会开新窗口，ClickHouse 中留下断口。

**建议**：Stage 6A 阶段在重启时从 ClickHouse 加载最近一批收盘 K-line 用于初始化，或者在文档中明确记录"重启会产生最多一个窗口的 K-line 数据缺失"，避免 Stage 7 回溯历史时踩坑。

#### FX-039（P2）：K-line 写 ClickHouse 与写 Outbox 无事务关联，可能产生事件漏发

**文件**：`MarketDataIngestionApplicationService.java:83-86`

```java
marketAnalyticsWriter.writeKline(snapshot);              // 写 ClickHouse（成功）
marketEventPublisher.publishKlineUpdate(toKlinePayload); // 写 t_outbox（若失败，事件永久丢失）
```

ClickHouse 和 MySQL 无法共享事务。若 `publishKlineUpdate` 抛异常，K-line 在 ClickHouse 存在但 Kafka 事件永远不发出，下游 trading-core 看不到 K-line 更新。当前写入频率低（每分钟最多几次），风险可控，但应在文档中说明。

---

## 3. Crypto 报价源（Symbol 发现）接入分析

### 3.1 设计评估

将 Tiingo Crypto WebSocket 定位为"symbol 发现工具"而非"持续报价源"的设计是**正确的**：
- Crypto 成交流（`data[0]="T"`）只有成交价，没有可靠的 bid/ask
- 发现的 symbol 写入 `status=2 suspended`，需人工审核后才启用
- 默认 `enabled: false`，不影响 FX 主链路

### 3.2 发现的问题

#### FX-040（P1）：`sample()` 在 ApplicationRunner 调用线程中 `Thread.sleep(10s)`，阻塞服务启动

**文件**：`TiingoCryptoSymbolSamplingService.java:92`

```java
webSocket = connectFuture.join();
Thread.sleep(sampleWindow.toMillis());  // ← 10 秒阻塞
```

`TiingoCryptoSymbolImportRunner` 带 `@Order(HIGHEST_PRECEDENCE)` 优先执行，直接在 Spring 启动主线程上 sleep 10 秒，`MarketFeedBootstrapRunner`（主报价链路）必须等候，整个服务启动延迟 ≥10 秒。

**修复**：改为异步后台执行，不阻塞 Spring 启动线程：
```java
@Override
public void run(ApplicationArguments args) {
    if (!properties.getTiingo().getCryptoSymbolImport().isEnabled()) {
        return;
    }
    CompletableFuture.runAsync(() -> {
        try {
            doImport();
        } catch (Exception e) {
            log.warn("market.tiingo.crypto-symbol-import.background.failed reason={}", e.getMessage(), e);
        }
    });
}
```

#### FX-041（P1）：采样失败抛 `IllegalStateException` 导致服务启动失败

**文件**：`TiingoCryptoSymbolSamplingService.java:107`

```java
Throwable throwable = failure.get();
if (throwable != null) {
    throw new IllegalStateException("Tiingo crypto symbol sampling failed", throwable);
}
```

`sample()` 抛出异常后由 `TiingoCryptoSymbolImportRunner.run()` 传播到 Spring Boot 启动流程，触发服务停止。Symbol 发现是可选功能，任何 Tiingo API 网络问题都不应中断服务启动。

**修复**：`TiingoCryptoSymbolImportRunner.run()` 用 try-catch 捕获异常：
```java
try {
    List<TiingoDiscoveredCryptoSymbol> sampledSymbols = samplingService.sample();
    // ...
} catch (Exception e) {
    log.warn("market.tiingo.crypto-symbol-import.skipped reason={}", e.getMessage());
}
```

#### FX-042（P2）：`TiingoCryptoSymbolSamplingService.httpClient` 未实现 `DisposableBean`

**文件**：`TiingoCryptoSymbolSamplingService.java`

`JdkTiingoQuoteProvider` 正确实现了 `DisposableBean.destroy()` 关闭 `HttpClient` 和 `ExecutorService`。`TiingoCryptoSymbolSamplingService` 同样持有 `HttpClient`，但没有对应的清理逻辑，服务关闭时内部线程池不会被优雅释放。

**修复**：实现 `DisposableBean`，`destroy()` 中调用 `httpClient.close()`（JDK 21+ 支持）。

---

## 4. 问题汇总

| 编号 | 优先级 | 来源 | 问题简述 |
|------|--------|------|---------|
| FX-036 | P1 | FX 源 | `toQuoteFromArray` 未验证 `messageType == "Q"`，协议升级后可能误解析 |
| FX-037 | P2 | FX 源 | `ingest()` 每 tick 打 INFO 日志，生产日志泛滥 |
| FX-038 | P2 | FX 源 | K-line 内存状态重启丢失，产生数据断口 |
| FX-039 | P2 | FX 源 | K-line 写 ClickHouse 与写 Outbox 无事务，可能漏发事件 |
| FX-040 | P1 | Crypto 源 | `sample()` 阻塞启动线程 10 秒 |
| FX-041 | P1 | Crypto 源 | 采样失败直接导致服务启动失败 |
| FX-042 | P2 | Crypto 源 | `httpClient` 未实现 `DisposableBean`，资源未释放 |

---

## 5. 结论

- **FX 报价源**：接入架构正确，协议解析与主链路数据流均与 Tiingo 冻结样例一致；有 1 个 P1 协议安全性问题（FX-036）和 3 个 P2 可靠性/可观测性问题。
- **Crypto 报价源**：设计定位正确（不提供 bid/ask，只做 symbol 发现）；有 2 个 P1 启动链路问题（FX-040/041）和 1 个 P2 资源泄漏问题（FX-042），建议在 Stage 6A 接入真实 Tiingo API Key 前一并修复。
