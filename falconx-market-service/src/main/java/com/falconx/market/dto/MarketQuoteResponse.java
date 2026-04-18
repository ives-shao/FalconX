package com.falconx.market.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 市场最新报价响应 DTO。
 *
 * <p>该对象用于 market-service 北向接口返回标准最新报价，
 * 字段语义与内部 `StandardQuote` 保持一致，但只暴露查询所需的稳定结果。
 */
public record MarketQuoteResponse(
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
