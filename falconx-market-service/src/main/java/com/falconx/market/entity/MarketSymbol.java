package com.falconx.market.entity;

import java.math.BigDecimal;

/**
 * 市场品种元数据。
 *
 * <p>该对象对应 `falconx_market.t_symbol`，
 * 只承载交易时间、交易精度和报价展示所需的低频元数据。
 */
public record MarketSymbol(
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
