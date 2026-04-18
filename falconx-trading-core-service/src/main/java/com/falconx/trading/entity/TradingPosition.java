package com.falconx.trading.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 持仓实体。
 *
 * <p>该对象对应 `falconx_trading.t_position` 的内存骨架表达。
 * 当前阶段只保留“一次开仓生成一条持仓”的最小模型，
 * 用于支撑订单、保证金和强平价的最小链路测试。
 */
public record TradingPosition(
        Long positionId,
        Long openingOrderId,
        Long userId,
        String symbol,
        TradingOrderSide side,
        BigDecimal quantity,
        BigDecimal entryPrice,
        BigDecimal leverage,
        BigDecimal margin,
        BigDecimal liquidationPrice,
        BigDecimal takeProfitPrice,
        BigDecimal stopLossPrice,
        TradingPositionStatus status,
        OffsetDateTime openedAt,
        OffsetDateTime updatedAt
) {
}
