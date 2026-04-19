package com.falconx.trading.event;

import com.falconx.trading.entity.TradingHedgeTriggerSource;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * B-book 对冲告警桩事件。
 *
 * <p>当前阶段只在服务内发布 Spring Event，作为后续接入真实告警或 A-book 对冲出口的占位桩。
 * 它不是 Kafka 契约，也不代表当前已经实现自动对冲执行。
 */
public record TradingHedgeAlertEvent(
        OffsetDateTime occurredAt,
        String symbol,
        BigDecimal netExposureUsd,
        BigDecimal hedgeThresholdUsd,
        Long positionId,
        TradingHedgeTriggerSource triggerSource,
        BigDecimal markPrice,
        OffsetDateTime quoteTs,
        String priceSource,
        Long hedgeLogId
) {
}
