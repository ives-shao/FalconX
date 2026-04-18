package com.falconx.market.service.impl;

import com.falconx.market.entity.StandardQuote;
import com.falconx.market.error.MarketBusinessException;
import com.falconx.market.repository.MarketLatestQuoteRepository;
import com.falconx.market.service.MarketQuoteQueryService;
import org.springframework.stereotype.Service;

/**
 * 市场最新报价查询服务默认实现。
 *
 * <p>当前阶段该实现直接读取内存最新报价仓储，
 * 并把“品种无可用报价”转换为市场域业务异常。
 */
@Service
public class DefaultMarketQuoteQueryService implements MarketQuoteQueryService {

    private final MarketLatestQuoteRepository marketLatestQuoteRepository;

    public DefaultMarketQuoteQueryService(MarketLatestQuoteRepository marketLatestQuoteRepository) {
        this.marketLatestQuoteRepository = marketLatestQuoteRepository;
    }

    @Override
    public StandardQuote getLatestQuote(String symbol) {
        return marketLatestQuoteRepository.findBySymbol(symbol)
                .orElseThrow(() -> new MarketBusinessException("30003", "Quote Not Available"));
    }
}
