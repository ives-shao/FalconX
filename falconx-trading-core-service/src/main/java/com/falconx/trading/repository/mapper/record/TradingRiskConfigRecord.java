package com.falconx.trading.repository.mapper.record;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 风控参数 MyBatis 记录对象。
 */
public record TradingRiskConfigRecord(
        Long id,
        String symbol,
        BigDecimal maxPositionPerUser,
        BigDecimal maxPositionTotal,
        BigDecimal maintenanceMarginRate,
        Integer maxLeverage,
        BigDecimal hedgeThresholdUsd,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
