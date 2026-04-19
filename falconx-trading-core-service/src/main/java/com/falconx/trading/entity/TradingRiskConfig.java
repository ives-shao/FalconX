package com.falconx.trading.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 风控参数实体。
 *
 * <p>该对象对应 `t_risk_config`，用于承载 owner 侧按品种维护的交易风控配置。
 * 本轮新增的 `hedgeThresholdUsd` 用于冻结 B-book 净美元敞口告警阈值。
 */
public record TradingRiskConfig(
        Long riskConfigId,
        String symbol,
        BigDecimal maxPositionPerUser,
        BigDecimal maxPositionTotal,
        BigDecimal maintenanceMarginRate,
        Integer maxLeverage,
        BigDecimal hedgeThresholdUsd,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
