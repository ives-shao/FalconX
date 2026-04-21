package com.falconx.market.service.impl;

import com.falconx.market.config.MarketServiceProperties;
import com.falconx.market.entity.KlineAggregationResult;
import com.falconx.market.entity.KlineSnapshot;
import com.falconx.market.entity.StandardQuote;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * {@link DefaultKlineAggregationService} 单元测试。
 *
 * <p>该测试覆盖 Stage 6A 中最关键的 K 线规则：
 *
 * <ul>
 *   <li>报价跨分钟时，上一根 1m K 线应收盘输出</li>
 *   <li>报价跨 5 分钟窗口时，5m K 线应收盘输出</li>
 *   <li>stale 报价不应污染任何聚合窗口</li>
 * </ul>
 */
class DefaultKlineAggregationServiceTests {

    @Test
    void shouldFinalizeOneMinuteKlineWhenQuoteMovesToNextMinute() {
        DefaultKlineAggregationService service = new DefaultKlineAggregationService(properties(List.of("1m")));

        KlineAggregationResult firstResult = service.onQuote(quote("EURUSD", "1.10000000", "2026-04-18T10:00:10Z", false));
        KlineAggregationResult secondResult = service.onQuote(quote("EURUSD", "1.12000000", "2026-04-18T10:00:40Z", false));
        KlineAggregationResult thirdResult = service.onQuote(quote("EURUSD", "1.09000000", "2026-04-18T10:01:02Z", false));

        Assertions.assertEquals(1, firstResult.activeSnapshots().size());
        Assertions.assertTrue(firstResult.finalizedSnapshots().isEmpty());
        Assertions.assertEquals(1, secondResult.activeSnapshots().size());
        Assertions.assertTrue(secondResult.finalizedSnapshots().isEmpty());
        Assertions.assertEquals(1, thirdResult.activeSnapshots().size());
        Assertions.assertEquals(1, thirdResult.finalizedSnapshots().size());

        KlineSnapshot snapshot = thirdResult.finalizedSnapshots().getFirst();
        Assertions.assertEquals("EURUSD", snapshot.symbol());
        Assertions.assertEquals("1m", snapshot.interval());
        Assertions.assertEquals(0, new BigDecimal("1.10000000").compareTo(snapshot.open()));
        Assertions.assertEquals(0, new BigDecimal("1.12000000").compareTo(snapshot.high()));
        Assertions.assertEquals(0, new BigDecimal("1.10000000").compareTo(snapshot.low()));
        Assertions.assertEquals(0, new BigDecimal("1.12000000").compareTo(snapshot.close()));
        Assertions.assertEquals(0, new BigDecimal("2").compareTo(snapshot.volume()));
        Assertions.assertEquals(OffsetDateTime.of(2026, 4, 18, 10, 0, 0, 0, ZoneOffset.UTC), snapshot.openTime());
        Assertions.assertEquals(OffsetDateTime.of(2026, 4, 18, 10, 0, 59, 0, ZoneOffset.UTC), snapshot.closeTime());
        Assertions.assertTrue(snapshot.isFinal());
    }

    @Test
    void shouldFinalizeFiveMinuteKlineWhenConfiguredWindowCloses() {
        DefaultKlineAggregationService service = new DefaultKlineAggregationService(properties(List.of("5m")));

        service.onQuote(quote("EURUSD", "1.10100000", "2026-04-18T10:00:10Z", false));
        service.onQuote(quote("EURUSD", "1.11500000", "2026-04-18T10:04:58Z", false));
        KlineAggregationResult finalized = service.onQuote(quote("EURUSD", "1.10800000", "2026-04-18T10:05:01Z", false));

        Assertions.assertEquals(1, finalized.finalizedSnapshots().size());
        Assertions.assertEquals(1, finalized.activeSnapshots().size());
        KlineSnapshot snapshot = finalized.finalizedSnapshots().getFirst();
        Assertions.assertEquals("5m", snapshot.interval());
        Assertions.assertEquals(0, new BigDecimal("1.10100000").compareTo(snapshot.open()));
        Assertions.assertEquals(0, new BigDecimal("1.11500000").compareTo(snapshot.high()));
        Assertions.assertEquals(0, new BigDecimal("1.10100000").compareTo(snapshot.low()));
        Assertions.assertEquals(0, new BigDecimal("1.11500000").compareTo(snapshot.close()));
        Assertions.assertEquals(0, new BigDecimal("2").compareTo(snapshot.volume()));
        Assertions.assertEquals(OffsetDateTime.of(2026, 4, 18, 10, 4, 59, 0, ZoneOffset.UTC), snapshot.closeTime());
    }

    @Test
    void shouldSkipStaleQuoteWithoutProducingKline() {
        DefaultKlineAggregationService service = new DefaultKlineAggregationService(properties(List.of("1m")));

        KlineAggregationResult finalized = service.onQuote(quote("EURUSD", "1.10000000", "2026-04-18T10:00:10Z", true));

        Assertions.assertTrue(finalized.activeSnapshots().isEmpty());
        Assertions.assertTrue(finalized.finalizedSnapshots().isEmpty());
    }

    private MarketServiceProperties properties(List<String> intervals) {
        MarketServiceProperties properties = new MarketServiceProperties();
        properties.getKline().setIntervals(intervals);
        properties.getKline().setTimezone("UTC");
        return properties;
    }

    private StandardQuote quote(String symbol, String markPrice, String ts, boolean stale) {
        BigDecimal mark = new BigDecimal(markPrice);
        return new StandardQuote(
                symbol,
                mark,
                mark,
                mark,
                mark,
                OffsetDateTime.parse(ts),
                "TIINGO_FOREX",
                stale
        );
    }
}
