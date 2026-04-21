package com.falconx.market.websocket;

import com.falconx.market.config.MarketServiceProperties;
import com.falconx.market.entity.KlineSnapshot;
import com.falconx.market.entity.StandardQuote;
import com.falconx.market.repository.MarketLatestQuoteRepository;
import java.util.Set;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * market-service WebSocket 推送编排服务。
 *
 * <p>该服务把报价、K 线和 stale 通知统一收口到 session registry，
 * 避免上游 ingestion 和下游连接管理互相耦合。
 */
@Service
public class MarketWebSocketPushService {

    private final MarketWebSocketSessionRegistry sessionRegistry;
    private final MarketLatestQuoteRepository marketLatestQuoteRepository;
    private final MarketServiceProperties properties;

    public MarketWebSocketPushService(MarketWebSocketSessionRegistry sessionRegistry,
                                      MarketLatestQuoteRepository marketLatestQuoteRepository,
                                      MarketServiceProperties properties) {
        this.sessionRegistry = sessionRegistry;
        this.marketLatestQuoteRepository = marketLatestQuoteRepository;
        this.properties = properties;
    }

    public void publishQuote(StandardQuote quote) {
        sessionRegistry.publishQuote(quote);
    }

    public void publishKline(KlineSnapshot snapshot) {
        sessionRegistry.publishKline(snapshot);
    }

    @Scheduled(fixedDelayString = "${falconx.market.web-socket.stale-scan-interval:1s}")
    public void scanAndPublishStaleQuotes() {
        Set<String> symbols = sessionRegistry.subscribedSymbols();
        for (String symbol : symbols) {
            marketLatestQuoteRepository.findBySymbol(symbol)
                    .filter(StandardQuote::stale)
                    .ifPresent(sessionRegistry::publishStale);
        }
    }
}
