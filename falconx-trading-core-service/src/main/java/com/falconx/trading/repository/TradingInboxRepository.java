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
}
