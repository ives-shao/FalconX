package com.falconx.trading.service.model;

import java.math.BigDecimal;

/**
 * 风控决策结果。
 *
 * <p>该对象用于把风控校验输出从服务层传递到应用层，
 * 避免应用层再次重复计算成交价、保证金、手续费和强平价。
 */
public record TradingRiskDecision(
        boolean accepted,
        String rejectReason,
        BigDecimal fillPrice,
        BigDecimal margin,
        BigDecimal fee,
        BigDecimal liquidationPrice
) {
}
