package com.falconx.market;

import com.falconx.market.application.MarketDataIngestionApplicationService;
import com.falconx.market.entity.MarketTradingScheduleSnapshot;
import com.falconx.market.entity.MarketSwapRateSnapshot;
import com.falconx.market.analytics.mapper.test.MarketAnalyticsTestSupportMapper;
import com.falconx.market.entity.StandardQuote;
import com.falconx.market.provider.TiingoRawQuote;
import com.falconx.market.repository.MarketLatestQuoteRepository;
import com.falconx.market.repository.MarketSymbolRepository;
import com.falconx.market.repository.RedisMarketSwapRateSnapshotRepository;
import com.falconx.market.service.KlineAggregationService;
import com.falconx.market.service.MarketSwapRateWarmupService;
import com.falconx.market.service.MarketTradingScheduleWarmupService;
import com.falconx.market.repository.RedisMarketTradingScheduleSnapshotRepository;
import com.falconx.market.repository.mapper.test.MarketSwapRateTestSupportMapper;
import com.falconx.market.support.MarketMybatisTestSupportConfiguration;
import com.falconx.market.support.MarketTestDatabaseInitializer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * market-service 真实基础设施集成测试。
 *
 * <p>该测试直接验证 Stage 5 下 market owner 的真实写入路径：
 *
 * <ol>
 *   <li>应用层接收原始报价</li>
 *   <li>最新价写入 Redis</li>
 *   <li>报价历史写入 ClickHouse</li>
 * </ol>
 */
@ActiveProfiles("stage5")
@ContextConfiguration(initializers = MarketTestDatabaseInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(
        classes = {
                MarketServiceApplication.class,
                MarketMybatisTestSupportConfiguration.class
        },
        properties = {
                "spring.datasource.url=jdbc:mysql://localhost:3306/falconx_market_it?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                "spring.datasource.username=root",
                "spring.datasource.password=root",
                "spring.data.redis.host=localhost",
                "spring.data.redis.port=6379",
                "falconx.market.stale.max-age=10m",
                "falconx.market.analytics.quote-batch-size=1",
                "falconx.market.analytics.quote-flush-interval=50",
                "falconx.market.analytics.jdbc-url=jdbc:clickhouse://localhost:8123/falconx_market_analytics",
                "falconx.market.analytics.username=default",
                "falconx.market.analytics.password="
        }
)
class MarketInfrastructureIntegrationTests {

    @Autowired
    private MarketDataIngestionApplicationService marketDataIngestionApplicationService;

    @Autowired
    private MarketLatestQuoteRepository marketLatestQuoteRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private MarketAnalyticsTestSupportMapper marketAnalyticsTestSupportMapper;

    @Autowired
    private MarketTradingScheduleWarmupService marketTradingScheduleWarmupService;

    @Autowired
    private RedisMarketTradingScheduleSnapshotRepository marketTradingScheduleSnapshotRepository;

    @Autowired
    private MarketSwapRateWarmupService marketSwapRateWarmupService;

    @Autowired
    private RedisMarketSwapRateSnapshotRepository marketSwapRateSnapshotRepository;

    @Autowired
    private MarketSwapRateTestSupportMapper marketSwapRateTestSupportMapper;

    @Autowired
    private KlineAggregationService klineAggregationService;

    @Autowired
    private MarketSymbolRepository marketSymbolRepository;

    @BeforeEach
    void cleanMarketStores() {
        stringRedisTemplate.delete("falconx:market:price:EURUSD");
        stringRedisTemplate.delete("falconx:market:trading:schedule:BTCUSDT");
        stringRedisTemplate.delete("falconx:market:trading:schedule:EURUSD");
        stringRedisTemplate.delete("falconx:market:swap-rate:BTCUSDT");
        marketAnalyticsTestSupportMapper.clearAnalyticsTables();
        marketSwapRateTestSupportMapper.deleteAllSwapRates();
        clearKlineAggregationState();
    }

    @Test
    void shouldPersistLatestQuoteToRedisAndClickHouse() {
        OffsetDateTime now = OffsetDateTime.now().minusSeconds(1);
        StandardQuote standardQuote = marketDataIngestionApplicationService.ingest(new TiingoRawQuote(
                "EURUSD",
                new BigDecimal("1.08100000"),
                new BigDecimal("1.08120000"),
                now
        ));

        StandardQuote persistedQuote = marketLatestQuoteRepository.findBySymbol("EURUSD").orElseThrow();
        Long quoteTickCount = marketAnalyticsTestSupportMapper.countQuoteTickBySymbol("EURUSD");

        Assertions.assertEquals(standardQuote.bid(), persistedQuote.bid());
        Assertions.assertEquals(standardQuote.ask(), persistedQuote.ask());
        Assertions.assertNotNull(quoteTickCount);
        Assertions.assertTrue(quoteTickCount >= 1L);
    }

    @Test
    void shouldPersistFinalizedOneMinuteKlineToClickHouse() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime firstTickTime = now.withSecond(10).withNano(0);
        if (firstTickTime.isAfter(now)) {
            firstTickTime = firstTickTime.minusMinutes(1);
        }
        OffsetDateTime secondTickTime = firstTickTime.withSecond(50);
        OffsetDateTime thirdTickTime = firstTickTime.plusMinutes(1).withSecond(1);

        marketDataIngestionApplicationService.ingest(new TiingoRawQuote(
                "EURUSD",
                new BigDecimal("1.08200000"),
                new BigDecimal("1.08220000"),
                firstTickTime
        ));
        marketDataIngestionApplicationService.ingest(new TiingoRawQuote(
                "EURUSD",
                new BigDecimal("1.08300000"),
                new BigDecimal("1.08320000"),
                secondTickTime
        ));
        marketDataIngestionApplicationService.ingest(new TiingoRawQuote(
                "EURUSD",
                new BigDecimal("1.08150000"),
                new BigDecimal("1.08170000"),
                thirdTickTime
        ));

        Long klineCount = marketAnalyticsTestSupportMapper.countKlineBySymbolAndInterval("EURUSD", "1m");

        Assertions.assertNotNull(klineCount);
        Assertions.assertEquals(1L, klineCount);
    }

    @Test
    void shouldWarmTradingScheduleSnapshotToRedis() {
        marketTradingScheduleWarmupService.refreshAll();

        MarketTradingScheduleSnapshot btcSnapshot = marketTradingScheduleSnapshotRepository.findBySymbol("BTCUSDT")
                .orElseThrow();
        MarketTradingScheduleSnapshot eurusdSnapshot = marketTradingScheduleSnapshotRepository.findBySymbol("EURUSD")
                .orElseThrow();
        Long btcScheduleTtl = stringRedisTemplate.getExpire("falconx:market:trading:schedule:BTCUSDT");

        Assertions.assertEquals("CRYPTO", btcSnapshot.marketCode());
        Assertions.assertEquals(7, btcSnapshot.sessions().size());
        Assertions.assertEquals("FX", eurusdSnapshot.marketCode());
        Assertions.assertEquals(5, eurusdSnapshot.sessions().size());
        Assertions.assertNotNull(btcScheduleTtl);
        Assertions.assertTrue(btcScheduleTtl <= 90_000L && btcScheduleTtl >= 89_000L);
    }

    @Test
    void shouldWarmSwapRateSnapshotToRedis() {
        marketSwapRateTestSupportMapper.upsertSwapRate(
                "BTCUSDT",
                new BigDecimal("-0.00010000"),
                new BigDecimal("0.00012000"),
                LocalTime.of(22, 0),
                LocalDate.now().minusDays(1)
        );
        marketSwapRateTestSupportMapper.upsertSwapRate(
                "BTCUSDT",
                new BigDecimal("-0.00020000"),
                new BigDecimal("0.00030000"),
                LocalTime.of(22, 0),
                LocalDate.now()
        );

        marketSwapRateWarmupService.refreshAll();

        MarketSwapRateSnapshot snapshot = marketSwapRateSnapshotRepository.findBySymbol("BTCUSDT")
                .orElseThrow();
        Long ttl = stringRedisTemplate.getExpire("falconx:market:swap-rate:BTCUSDT");

        Assertions.assertEquals("BTCUSDT", snapshot.symbol());
        Assertions.assertEquals(2, snapshot.rates().size());
        Assertions.assertEquals(LocalDate.now().minusDays(1), snapshot.rates().getFirst().effectiveFrom());
        Assertions.assertEquals(LocalDate.now(), snapshot.rates().getLast().effectiveFrom());
        Assertions.assertEquals(new BigDecimal("-0.00020000"), snapshot.rates().getLast().longRate());
        Assertions.assertNotNull(ttl);
        Assertions.assertTrue(ttl <= 90_000L && ttl >= 89_000L);
    }

    @Test
    void shouldAppendNewCryptoSymbolWithoutOverwritingExistingSymbol() {
        int insertedCount = marketSymbolRepository.appendIfAbsent(List.of(
                new com.falconx.market.entity.MarketSymbol(
                        999_001L,
                        "TESTXUSDT",
                        1,
                        "CRYPTO",
                        "TESTX",
                        "USDT",
                        8,
                        6,
                        new BigDecimal("0.000001"),
                        new BigDecimal("1000000.000000"),
                        new BigDecimal("10.000000"),
                        100,
                        new BigDecimal("0.000500"),
                        new BigDecimal("0.00000000"),
                        2
                ),
                new com.falconx.market.entity.MarketSymbol(
                        999_002L,
                        "BTCUSDT",
                        1,
                        "CRYPTO",
                        "BTC",
                        "USDT",
                        8,
                        6,
                        new BigDecimal("0.000001"),
                        new BigDecimal("1000000.000000"),
                        new BigDecimal("10.000000"),
                        100,
                        new BigDecimal("0.000500"),
                        new BigDecimal("0.00000000"),
                        2
                )
        ));

        com.falconx.market.entity.MarketSymbol appended = marketSymbolRepository.findBySymbol("TESTXUSDT").orElseThrow();
        com.falconx.market.entity.MarketSymbol existing = marketSymbolRepository.findBySymbol("BTCUSDT").orElseThrow();

        Assertions.assertEquals(1, insertedCount);
        Assertions.assertEquals("TESTX", appended.baseCurrency());
        Assertions.assertEquals("USDT", appended.quoteCurrency());
        Assertions.assertEquals(2, appended.status());
        Assertions.assertEquals(1, existing.status());
        Assertions.assertEquals(2, existing.pricePrecision());
    }

    @SuppressWarnings("unchecked")
    private void clearKlineAggregationState() {
        Object bucketsField = ReflectionTestUtils.getField(klineAggregationService, "buckets");
        if (bucketsField instanceof Map<?, ?> buckets) {
            buckets.clear();
        }
    }
}
