package com.falconx.trading.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 交易订单实体。
 *
 * <p>该对象对应 `falconx_trading.t_order` 的内存骨架表达。
 * 一期 Stage 3B 先冻结市场单最小结构，确保后续接入真实持久层时
 * 订单幂等键、成交价格、保证金和手续费等关键字段语义不再漂移。
 */
public record TradingOrder(
        Long orderId,
        String orderNo,
        Long userId,
        String symbol,
        TradingOrderSide side,
        TradingOrderType orderType,
        BigDecimal quantity,
        BigDecimal requestedPrice,
        BigDecimal filledPrice,
        BigDecimal leverage,
        BigDecimal margin,
        BigDecimal fee,
        String clientOrderId,
        TradingOrderStatus status,
        String rejectReason,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
