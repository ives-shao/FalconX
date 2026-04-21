package com.falconx.trading;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import tools.jackson.databind.ObjectMapper;
import com.falconx.infrastructure.kafka.KafkaEventMessageSupport;
import com.falconx.market.contract.event.MarketKlineUpdateEventPayload;
import com.falconx.market.contract.event.MarketPriceTickEventPayload;
import com.falconx.trading.dto.PriceTickProcessingResult;
import com.falconx.trading.engine.QuoteDrivenEngine;
import com.falconx.trading.entity.TradingQuoteSnapshot;
import com.falconx.trading.repository.mapper.test.TradingTestSupportMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * market -> trading 真实 Kafka 入口集成测试。
 *
 * <p>本组用例覆盖 Stage 6A 当前新增的两个收口点：
 *
 * <ul>
 *   <li>`market.kline.update` 在 trading-core 的正式消费</li>
 *   <li>`market.price.tick` 在 Kafka 入口失败后的显式重试</li>
 * </ul>
 */
@ActiveProfiles("stage5")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(
        classes = {
                TradingCoreServiceApplication.class,
                TradingKafkaMarketEventIntegrationTests.QuoteDrivenEngineRetryTestConfiguration.class
        },
        properties = {
                "spring.datasource.url=jdbc:mysql://localhost:3306/falconx_trading_it?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                "spring.datasource.username=root",
                "spring.datasource.password=root",
                "spring.data.redis.host=localhost",
                "spring.data.redis.port=6379",
                "falconx.trading.kafka.consumer-group-id=trading-kafka-market-it-${random.uuid}"
        }
)
class TradingKafkaMarketEventIntegrationTests {

    private static final String MARKET_PRICE_TICK_TOPIC = "falconx.market.price.tick";
    private static final String MARKET_KLINE_UPDATE_TOPIC = "falconx.market.kline.update";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private TradingTestSupportMapper tradingTestSupportMapper;

    @Autowired
    private QuoteDrivenEngine quoteDrivenEngine;

    @BeforeEach
    void cleanOwnerTables() {
        tradingTestSupportMapper.clearOwnerTables();
        reset(quoteDrivenEngine);
    }

    @Test
    void shouldConsumeMarketKlineUpdateViaKafkaAndRecordInboxFact() throws Exception {
        String eventId = "evt-market-kline-0001";
        MarketKlineUpdateEventPayload payload = new MarketKlineUpdateEventPayload(
                "EURUSD",
                "1m",
                new BigDecimal("1.08000000"),
                new BigDecimal("1.08200000"),
                new BigDecimal("1.07950000"),
                new BigDecimal("1.08150000"),
                new BigDecimal("12.34000000"),
                OffsetDateTime.parse("2026-04-20T12:00:00Z"),
                OffsetDateTime.parse("2026-04-20T12:00:59Z"),
                true
        );

        kafkaTemplate.send(KafkaEventMessageSupport.buildJsonMessage(
                        MARKET_KLINE_UPDATE_TOPIC,
                        payload.symbol() + ":" + payload.interval(),
                        objectMapper.writeValueAsString(payload),
                        eventId,
                        "market.kline.update",
                        "falconx-market-service"
                ))
                .get(5, TimeUnit.SECONDS);

        waitForAssertion(() -> {
            Assertions.assertEquals(1, tradingTestSupportMapper.countInboxByEventId(eventId));
            Assertions.assertEquals(1, tradingTestSupportMapper.countInboxByEventType("market.kline.update"));
        }, "market.kline.update 未在 trading-core 写入 Inbox 审计事实");
    }

    @Test
    void shouldRetryMarketPriceTickAtKafkaEntryAndEventuallySucceed() throws Exception {
        String eventId = "evt-market-tick-0001";
        AtomicInteger attempts = new AtomicInteger();
        when(quoteDrivenEngine.processTick(any(MarketPriceTickEventPayload.class))).thenAnswer(invocation -> {
            int currentAttempt = attempts.incrementAndGet();
            if (currentAttempt == 1) {
                throw new IllegalStateException("simulated price tick failure");
            }
            return new PriceTickProcessingResult(
                    new TradingQuoteSnapshot(
                            "BTCUSDT",
                            new BigDecimal("9990.00000000"),
                            new BigDecimal("10000.00000000"),
                            new BigDecimal("9995.00000000"),
                            OffsetDateTime.parse("2026-04-20T12:01:00Z"),
                            "market-kafka-it",
                            false
                    ),
                    0
            );
        });

        MarketPriceTickEventPayload payload = new MarketPriceTickEventPayload(
                "BTCUSDT",
                new BigDecimal("9990.00000000"),
                new BigDecimal("10000.00000000"),
                new BigDecimal("9995.00000000"),
                new BigDecimal("9995.00000000"),
                OffsetDateTime.parse("2026-04-20T12:01:00Z"),
                "market-kafka-it",
                false
        );

        kafkaTemplate.send(KafkaEventMessageSupport.buildJsonMessage(
                        MARKET_PRICE_TICK_TOPIC,
                        payload.symbol(),
                        objectMapper.writeValueAsString(payload),
                        eventId,
                        "market.price.tick",
                        "falconx-market-service"
                ))
                .get(5, TimeUnit.SECONDS);

        waitForAssertion(() -> Assertions.assertTrue(attempts.get() >= 2),
                "market.price.tick Kafka 入口失败后未触发重试");
        waitForDuration(Duration.ofSeconds(1));

        Assertions.assertEquals(2, attempts.get());
        Assertions.assertEquals(0, tradingTestSupportMapper.countInboxByEventId(eventId));
    }

    private void waitForAssertion(Runnable assertion, String failureMessage) throws Exception {
        AssertionError lastError = null;
        long deadline = System.currentTimeMillis() + 12_000L;
        while (System.currentTimeMillis() < deadline) {
            try {
                assertion.run();
                return;
            } catch (AssertionError error) {
                lastError = error;
                waitForDuration(Duration.ofMillis(250));
            }
        }
        if (lastError != null) {
            throw lastError;
        }
        Assertions.fail(failureMessage);
    }

    private void waitForDuration(Duration duration) throws InterruptedException {
        Thread.sleep(duration.toMillis());
    }

    @TestConfiguration
    static class QuoteDrivenEngineRetryTestConfiguration {

        @Bean
        @Primary
        QuoteDrivenEngine primaryQuoteDrivenEngineMock() {
            return mock(QuoteDrivenEngine.class);
        }
    }
}
