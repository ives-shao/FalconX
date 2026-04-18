package com.falconx.market.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * market-service 内部标准报价对象。
 *
 * <p>该对象对应市场数据契约中的标准报价模型，
 * 用于在 Provider、标准化服务、缓存、分析写入和事件发布之间传递统一语义的数据。
 * 该类属于 market-service 内部 owner，不对外作为 contract 暴露。
 */
public record StandardQuote(
        String symbol,
        BigDecimal bid,
        BigDecimal ask,
        BigDecimal mid,
        BigDecimal mark,
        OffsetDateTime ts,
        String source,
        boolean stale
) {
}
