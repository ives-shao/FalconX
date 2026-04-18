package com.falconx.market.provider;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Tiingo crypto 成交流中抽取出的 symbol 候选。
 *
 * <p>该对象只服务于“symbol 发现与补录”场景，不进入标准报价主链路。
 * 原因是当前 Tiingo `crypto` 端点返回的主流量是成交帧，而不是 `bid/ask` 顶档报价。
 */
public record TiingoDiscoveredCryptoSymbol(
        String symbol,
        String baseCurrency,
        String quoteCurrency,
        String exchange,
        OffsetDateTime observedAt,
        BigDecimal quantity,
        BigDecimal price
) {
}
