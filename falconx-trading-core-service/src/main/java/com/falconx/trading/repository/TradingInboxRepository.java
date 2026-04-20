package com.falconx.trading.repository;

import java.time.OffsetDateTime;

/**
 * 交易核心入站幂等仓储接口。
 *
 * <p>该接口用于承接低频关键事件消费时的 `eventId` 去重。
 */
public interface TradingInboxRepository {

    /**
     * 若 `eventId` 未处理过则标记为已处理。
     *
     * @param eventId 事件 ID
     * @param eventType 事件类型
     * @param processedAt 处理时间
     * @return `true` 表示首次写入，`false` 表示重复事件
     */
    boolean markProcessedIfAbsent(String eventId, String eventType, OffsetDateTime processedAt);

    /**
     * 若 `eventId` 未处理过则按给定来源与 payload 标记为已处理。
     *
     * <p>低频关键事件需要在 `t_inbox` 中保留最小审计事实，便于后续追踪实际消费来源与原始 payload。
     *
     * @param eventId 事件 ID
     * @param eventType 事件类型
     * @param source 来源服务
     * @param payloadJson 原始 JSON payload
     * @param processedAt 处理时间
     * @return `true` 表示首次写入，`false` 表示重复事件
     */
    boolean markProcessedIfAbsent(String eventId,
                                  String eventType,
                                  String source,
                                  String payloadJson,
                                  OffsetDateTime processedAt);
}
