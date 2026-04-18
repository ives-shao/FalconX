package com.falconx.market.provider;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Tiingo 原始报价对象骨架。
 *
 * <p>该对象表示来自 Tiingo WebSocket 的最小可用字段，
 * 后续真实接入时应由 Provider 层负责把外部报文映射为该对象，
 * 再交给标准化服务进行统一处理。
 */
public record TiingoRawQuote(
        String ticker,
        BigDecimal bid,
        BigDecimal ask,
        OffsetDateTime ts
) {
}
