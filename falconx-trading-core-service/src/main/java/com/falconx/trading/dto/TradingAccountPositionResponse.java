package com.falconx.trading.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 交易账户下的持仓响应 DTO。
 *
 * <p>该对象用于在账户查询接口中返回用户当前 OPEN 持仓，
 * 并基于 Redis 最新 `markPrice` 动态拼接未实现盈亏，而不是把该动态值持久化到 MySQL。
 */
public record TradingAccountPositionResponse(
        Long positionId,
        String symbol,
        String side,
        BigDecimal quantity,
        BigDecimal entryPrice,
        BigDecimal markPrice,
        BigDecimal unrealizedPnl,
        String marginMode,
        BigDecimal liquidationPrice,
        BigDecimal takeProfitPrice,
        BigDecimal stopLossPrice,
        boolean quoteStale,
        OffsetDateTime quoteTs,
        String priceSource
) {
}
