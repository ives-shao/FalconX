package com.falconx.market.service.impl;

import com.falconx.market.config.MarketServiceProperties;
import com.falconx.market.entity.StandardQuote;
import com.falconx.market.provider.TiingoRawQuote;
import com.falconx.market.service.QuoteStandardizationService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;

/**
 * 默认报价标准化实现。
 *
 * <p>当前实现遵循市场数据契约中的标准字段要求：
 * 1. `mid = (bid + ask) / 2`
 * 2. `mark` 当前阶段直接与 `mid` 对齐
 * 3. `stale` 按配置的最大可接受年龄计算
 *
 * <p>后续若需要加入点差调整、标记价算法或来源差异修正，应继续放在该服务内处理。
 */
@Service
public class DefaultQuoteStandardizationService implements QuoteStandardizationService {

    private final MarketServiceProperties properties;

    public DefaultQuoteStandardizationService(MarketServiceProperties properties) {
        this.properties = properties;
    }

    /**
     * 标准化 Tiingo 原始报价。
     *
     * @param rawQuote Tiingo 原始报价
     * @return 平台标准报价对象
     */
    @Override
    public StandardQuote standardize(TiingoRawQuote rawQuote) {
        BigDecimal mid = rawQuote.bid()
                .add(rawQuote.ask())
                .divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP);
        OffsetDateTime now = OffsetDateTime.now();
        boolean stale = rawQuote.ts().plus(properties.getStale().getMaxAge()).isBefore(now);
        return new StandardQuote(
                rawQuote.ticker(),
                rawQuote.bid(),
                rawQuote.ask(),
                mid,
                mid,
                rawQuote.ts(),
                "TIINGO_FOREX",
                stale
        );
    }
}
