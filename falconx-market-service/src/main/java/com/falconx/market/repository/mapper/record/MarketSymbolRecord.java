package com.falconx.market.repository.mapper.record;

import java.math.BigDecimal;

/**
 * `t_symbol` 持久化记录。
 */
public record MarketSymbolRecord(
        Long id,
        String symbol,
        int category,
        String marketCode,
        String baseCurrency,
        String quoteCurrency,
        int pricePrecision,
        int qtyPrecision,
        BigDecimal minQty,
        BigDecimal maxQty,
        BigDecimal minNotional,
        int maxLeverage,
        BigDecimal takerFeeRate,
        BigDecimal spread,
        int status
) {
}
