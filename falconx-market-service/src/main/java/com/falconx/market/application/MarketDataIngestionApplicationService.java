package com.falconx.market.application;

import com.falconx.market.analytics.MarketAnalyticsWriter;
import com.falconx.market.cache.MarketQuoteCacheWriter;
import com.falconx.market.contract.event.MarketKlineUpdateEventPayload;
import com.falconx.market.contract.event.MarketPriceTickEventPayload;
import com.falconx.market.entity.KlineAggregationResult;
import com.falconx.market.entity.KlineSnapshot;
import com.falconx.market.entity.StandardQuote;
import com.falconx.market.producer.MarketEventPublisher;
import com.falconx.market.provider.TiingoRawQuote;
import com.falconx.market.service.KlineAggregationService;
import com.falconx.market.service.QuoteStandardizationService;
import com.falconx.market.websocket.MarketWebSocketPushService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 市场数据接入应用服务。
 *
 * <p>该服务是 Stage 2A 最核心的应用层编排骨架。
 * 它负责把 Tiingo 原始报价串成完整市场链路：
 *
 * <ol>
 *   <li>原始报价标准化</li>
 *   <li>写 Redis 最新价</li>
 *   <li>写 ClickHouse 报价历史</li>
 *   <li>发布价格事件</li>
 *   <li>更新 K 线聚合状态</li>
 *   <li>必要时写 ClickHouse K 线并发布 K 线事件</li>
 * </ol>
 *
 * <p>当前阶段已经接入真实 Redis、ClickHouse 与 Kafka。
 * 仍保留骨架状态的外部依赖只有 Tiingo 真连接与 K 线真实聚合算法。
 */
@Service
public class MarketDataIngestionApplicationService {

    private static final Logger log = LoggerFactory.getLogger(MarketDataIngestionApplicationService.class);

    private final QuoteStandardizationService quoteStandardizationService;
    private final MarketQuoteCacheWriter marketQuoteCacheWriter;
    private final MarketAnalyticsWriter marketAnalyticsWriter;
    private final MarketEventPublisher marketEventPublisher;
    private final KlineAggregationService klineAggregationService;
    private final MarketWebSocketPushService marketWebSocketPushService;

    public MarketDataIngestionApplicationService(QuoteStandardizationService quoteStandardizationService,
                                                 MarketQuoteCacheWriter marketQuoteCacheWriter,
                                                 MarketAnalyticsWriter marketAnalyticsWriter,
                                                 MarketEventPublisher marketEventPublisher,
                                                 KlineAggregationService klineAggregationService,
                                                 MarketWebSocketPushService marketWebSocketPushService) {
        this.quoteStandardizationService = quoteStandardizationService;
        this.marketQuoteCacheWriter = marketQuoteCacheWriter;
        this.marketAnalyticsWriter = marketAnalyticsWriter;
        this.marketEventPublisher = marketEventPublisher;
        this.klineAggregationService = klineAggregationService;
        this.marketWebSocketPushService = marketWebSocketPushService;
    }

    /**
     * 处理一条 Tiingo 原始报价。
     *
     * <p>当前方法是 market-service Stage 2A 的最小可用主调用链。
     * 后续只允许在该链路内继续补全真实客户端实现，不应破坏其总体依赖方向。
     *
     * @param rawQuote Tiingo 原始报价
     * @return 标准化后的报价对象，便于上层测试和调用方观察最终结果
     */
    public StandardQuote ingest(TiingoRawQuote rawQuote) {
        // 市场数据链路必须先完成标准化，再进入缓存、分析存储和事件分发。
        // 这样可以保证后续所有下游看到的是统一语义的 bid/ask/mark/stale 字段，
        // 避免每个消费者各自解释 Tiingo 原始格式。
        StandardQuote standardQuote = quoteStandardizationService.standardize(rawQuote);
        log.info("market.quote.received symbol={} source={} quoteTs={} stale={}",
                standardQuote.symbol(),
                standardQuote.source(),
                standardQuote.ts(),
                standardQuote.stale());
        marketQuoteCacheWriter.writeLatestQuote(standardQuote);
        marketAnalyticsWriter.writeQuoteTick(standardQuote);
        marketEventPublisher.publishPriceTick(toPriceTickPayload(standardQuote));
        marketWebSocketPushService.publishQuote(standardQuote);

        // K 线处理放在 tick 主链路的最后一段：
        // 先保证最新价和 price.tick 事件落地，再把同一条报价用于推进 K 线聚合。
        // 这样即使 K 线聚合后续失败，也不会影响“最新价可读”和“tick 事件可消费”的核心链路。
        KlineAggregationResult aggregationResult = klineAggregationService.onQuote(standardQuote);
        aggregationResult.activeSnapshots().forEach(marketWebSocketPushService::publishKline);
        List<KlineSnapshot> finalizedSnapshots = aggregationResult.finalizedSnapshots();
        finalizedSnapshots.forEach(snapshot -> {
            // 对 K 线而言，Kafka 通知路径优先于分析存储。
            // `market.kline.update` 代表“平台已经确认有一根收盘线需要下游感知”，
            // 因此应先把事件写入 owner Outbox，再执行 ClickHouse 分析写入。
            // 即使 ClickHouse 暂时不可用，下游仍可先通过事件感知这根 K 线的存在，
            // 避免出现“分析存储里有收盘线，但业务事件永久漏发”的单边事实。
            marketEventPublisher.publishKlineUpdate(toKlinePayload(snapshot));
            marketAnalyticsWriter.writeKline(snapshot);
            marketWebSocketPushService.publishKline(snapshot);
        });

        log.debug("market.ingestion.completed symbol={} ts={} stale={}",
                standardQuote.symbol(),
                standardQuote.ts(),
                standardQuote.stale());
        return standardQuote;
    }

    private MarketPriceTickEventPayload toPriceTickPayload(StandardQuote quote) {
        return new MarketPriceTickEventPayload(
                quote.symbol(),
                quote.bid(),
                quote.ask(),
                quote.mid(),
                quote.mark(),
                quote.ts(),
                quote.source(),
                quote.stale()
        );
    }

    private MarketKlineUpdateEventPayload toKlinePayload(KlineSnapshot snapshot) {
        return new MarketKlineUpdateEventPayload(
                snapshot.symbol(),
                snapshot.interval(),
                snapshot.open(),
                snapshot.high(),
                snapshot.low(),
                snapshot.close(),
                snapshot.volume(),
                snapshot.openTime(),
                snapshot.closeTime(),
                snapshot.isFinal()
        );
    }
}
