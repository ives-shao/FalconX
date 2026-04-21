package com.falconx.trading.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 持仓查询项。
 */
public record TradingPositionItemResponse(
        Long positionId,
        Long openingOrderId,
        String symbol,
        String side,
        BigDecimal quantity,
        BigDecimal entryPrice,
        BigDecimal leverage,
        BigDecimal margin,
        String marginMode,
        BigDecimal liquidationPrice,
        BigDecimal takeProfitPrice,
        BigDecimal stopLossPrice,
        BigDecimal markPrice,
        BigDecimal unrealizedPnl,
        BigDecimal closePrice,
        String closeReason,
        BigDecimal realizedPnl,
        String status,
        Boolean quoteStale,
        OffsetDateTime quoteTs,
        String quoteSource,
        OffsetDateTime openedAt,
        OffsetDateTime closedAt,
        OffsetDateTime updatedAt
) {
}
