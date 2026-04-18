package com.falconx.market.analytics;

import com.falconx.market.analytics.mapper.MarketKlineMapper;
import com.falconx.market.analytics.mapper.MarketQuoteTickMapper;
import com.falconx.market.analytics.mapper.record.MarketQuoteTickRecord;
import com.falconx.market.config.MarketServiceProperties;
import com.falconx.market.entity.StandardQuote;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * ClickHouse 分析写入器单元测试。
 *
 * <p>该测试锁定 `FX-020` 的三个关键行为：
 *
 * <ul>
 *   <li>达到批量阈值时立即刷盘</li>
 *   <li>低吞吐场景下定时刷新会兜底</li>
 *   <li>批量写失败时缓冲会回灌，不会直接丢 Tick</li>
 * </ul>
 */
class MybatisClickHouseMarketAnalyticsWriterTests {

    @Test
    void shouldFlushImmediatelyWhenBatchThresholdReached() {
        MarketQuoteTickMapper quoteTickMapper = Mockito.mock(MarketQuoteTickMapper.class);
        MarketKlineMapper klineMapper = Mockito.mock(MarketKlineMapper.class);
        MarketServiceProperties properties = new MarketServiceProperties();
        properties.getAnalytics().setQuoteBatchSize(2);

        MybatisClickHouseMarketAnalyticsWriter writer = new MybatisClickHouseMarketAnalyticsWriter(
                quoteTickMapper,
                klineMapper,
                properties
        );

        writer.writeQuoteTick(quote("EURUSD", "2026-04-18T02:00:00Z"));
        Mockito.verifyNoInteractions(quoteTickMapper);

        writer.writeQuoteTick(quote("EURUSD", "2026-04-18T02:00:01Z"));

        ArgumentCaptor<List<MarketQuoteTickRecord>> batchCaptor = ArgumentCaptor.forClass(List.class);
        Mockito.verify(quoteTickMapper).insertQuoteTicks(batchCaptor.capture());
        Assertions.assertEquals(2, batchCaptor.getValue().size());
    }

    @Test
    void shouldFlushPendingQuoteOnScheduledTick() {
        MarketQuoteTickMapper quoteTickMapper = Mockito.mock(MarketQuoteTickMapper.class);
        MarketKlineMapper klineMapper = Mockito.mock(MarketKlineMapper.class);
        MarketServiceProperties properties = new MarketServiceProperties();
        properties.getAnalytics().setQuoteBatchSize(10);

        MybatisClickHouseMarketAnalyticsWriter writer = new MybatisClickHouseMarketAnalyticsWriter(
                quoteTickMapper,
                klineMapper,
                properties
        );

        writer.writeQuoteTick(quote("XAUUSD", "2026-04-18T02:10:00Z"));
        Mockito.verifyNoInteractions(quoteTickMapper);

        writer.flushQuoteTicksOnSchedule();

        ArgumentCaptor<List<MarketQuoteTickRecord>> batchCaptor = ArgumentCaptor.forClass(List.class);
        Mockito.verify(quoteTickMapper).insertQuoteTicks(batchCaptor.capture());
        Assertions.assertEquals(1, batchCaptor.getValue().size());
        Assertions.assertEquals("XAUUSD", batchCaptor.getValue().getFirst().symbol());
    }

    @Test
    void shouldRestorePendingQuoteWhenBatchInsertFails() {
        MarketQuoteTickMapper quoteTickMapper = Mockito.mock(MarketQuoteTickMapper.class);
        MarketKlineMapper klineMapper = Mockito.mock(MarketKlineMapper.class);
        MarketServiceProperties properties = new MarketServiceProperties();
        properties.getAnalytics().setQuoteBatchSize(1);

        Mockito.doThrow(new IllegalStateException("clickhouse unavailable"))
                .doReturn(1)
                .when(quoteTickMapper)
                .insertQuoteTicks(Mockito.anyList());

        MybatisClickHouseMarketAnalyticsWriter writer = new MybatisClickHouseMarketAnalyticsWriter(
                quoteTickMapper,
                klineMapper,
                properties
        );

        writer.writeQuoteTick(quote("US500USD", "2026-04-18T02:20:00Z"));
        writer.flushQuoteTicksOnSchedule();

        ArgumentCaptor<List<MarketQuoteTickRecord>> batchCaptor = ArgumentCaptor.forClass(List.class);
        Mockito.verify(quoteTickMapper, Mockito.times(2)).insertQuoteTicks(batchCaptor.capture());
        Assertions.assertEquals(1, batchCaptor.getAllValues().getFirst().size());
        Assertions.assertEquals("US500USD", batchCaptor.getAllValues().getLast().getFirst().symbol());
    }

    private StandardQuote quote(String symbol, String ts) {
        return new StandardQuote(
                symbol,
                new BigDecimal("1.10000000"),
                new BigDecimal("1.10010000"),
                new BigDecimal("1.10005000"),
                new BigDecimal("1.10005000"),
                OffsetDateTime.parse(ts),
                "TIINGO_FOREX",
                false
        );
    }
}
