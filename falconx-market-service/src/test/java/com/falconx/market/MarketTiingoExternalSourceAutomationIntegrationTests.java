package com.falconx.market;

import static com.falconx.market.support.TiingoExternalTestSupport.combinedOutput;
import static com.falconx.market.support.TiingoExternalTestSupport.waitForCondition;
import static com.falconx.market.support.TiingoExternalTestSupport.waitForLog;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.falconx.infrastructure.kafka.KafkaEventHeaderConstants;
import com.falconx.market.analytics.mapper.test.MarketAnalyticsTestSupportMapper;
import com.falconx.market.application.MarketFeedBootstrapRunner;
import com.falconx.market.entity.StandardQuote;
import com.falconx.market.repository.MarketLatestQuoteRepository;
import com.falconx.market.repository.mapper.test.MarketSymbolTestSupportMapper;
import com.falconx.market.support.MarketMybatisTestSupportConfiguration;
import com.falconx.market.support.MarketTestDatabaseInitializer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/**
 * Tiingo 外部真源自动化验证。
 *
 * <p>这组测试显式连到 Tiingo `fx` 真端点，并把外部报价走进 market-service 当前真实主链路，
 * 用于补齐 Stage 6A 剩余的“外部真源纳入自动化用例”缺口。
 *
 * <p>为了避免把日常本地测试强制绑定到外部网络，该类只在同时满足下面两个条件时执行：
 *
 * <ul>
 *   <li>`FALCONX_MARKET_TIINGO_EXTERNAL_TEST_ENABLED=true`</li>
 *   <li>`FALCONX_MARKET_TIINGO_API_KEY` 已显式提供</li>
 * </ul>
 */
@EnabledIfEnvironmentVariable(named = "FALCONX_MARKET_TIINGO_EXTERNAL_TEST_ENABLED", matches = "(?i)true")
@EnabledIfEnvironmentVariable(named = "FALCONX_MARKET_TIINGO_API_KEY", matches = ".+")
@ExtendWith(OutputCaptureExtension.class)
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
                "spring.kafka.bootstrap-servers=localhost:9092",
                "falconx.market.analytics.jdbc-url=jdbc:clickhouse://localhost:8123/falconx_market_analytics",
                "falconx.market.analytics.username=default",
                "falconx.market.analytics.password=",
                "falconx.market.analytics.quote-batch-size=1",
                "falconx.market.analytics.quote-flush-interval=50",
                "falconx.market.redis.quote-ttl=10s",
                "falconx.market.stale.max-age=3s",
                "falconx.market.tiingo.enabled=true",
                "falconx.market.tiingo.api-key=${FALCONX_MARKET_TIINGO_API_KEY}",
                "falconx.market.tiingo.connect-timeout=15s",
                "falconx.market.tiingo.reconnect-interval=2s",
                "falconx.market.tiingo.trust-store-location=${FALCONX_MARKET_TIINGO_TRUST_STORE_LOCATION:}",
                "falconx.market.tiingo.trust-store-password=${FALCONX_MARKET_TIINGO_TRUST_STORE_PASSWORD:}",
                "falconx.market.tiingo.trust-store-type=${FALCONX_MARKET_TIINGO_TRUST_STORE_TYPE:PKCS12}",
                "logging.level.com.falconx.market.provider.JdkTiingoQuoteProvider=DEBUG"
        }
)
class MarketTiingoExternalSourceAutomationIntegrationTests {

    private static final String PRICE_TICK_TOPIC = "falconx.market.price.tick";
    private static final Set<String> TRACKED_SYMBOLS = Set.of("EURUSD", "USDJPY", "XAUUSD");

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private MarketLatestQuoteRepository marketLatestQuoteRepository;

    @Autowired
    private MarketAnalyticsTestSupportMapper marketAnalyticsTestSupportMapper;

    @Autowired
    private MarketFeedBootstrapRunner marketFeedBootstrapRunner;

    @Autowired
    private MarketSymbolTestSupportMapper marketSymbolTestSupportMapper;

    @BeforeEach
    void resetTrackedSymbols() {
        TRACKED_SYMBOLS.forEach(symbol -> {
            marketSymbolTestSupportMapper.updateSymbolStatus(symbol, 1);
            stringRedisTemplate.delete("falconx:market:price:" + symbol);
        });
        marketAnalyticsTestSupportMapper.clearAnalyticsTables();
    }

    @Test
    void shouldVerifyRealTiingoFeedConnectionFlowAndDynamicStale(CapturedOutput output) throws Exception {
        try (KafkaConsumer<String, String> consumer = createTopicConsumer(PRICE_TICK_TOPIC)) {
            waitForLog(output, "market.tiingo.provider.connected", Duration.ofSeconds(20));
            waitForLog(output, "market.tiingo.provider.subscription.confirmed", Duration.ofSeconds(30));

            List<ConsumerRecord<String, String>> records = waitForTrackedTopicRecords(
                    consumer,
                    TRACKED_SYMBOLS,
                    3,
                    Duration.ofSeconds(40)
            );

            Assertions.assertTrue(records.size() >= 3);
            Instant earliestQuoteTime = null;
            Instant latestQuoteTime = null;
            for (ConsumerRecord<String, String> record : records) {
                JsonNode payload = objectMapper.readTree(record.value());
                Assertions.assertTrue(TRACKED_SYMBOLS.contains(record.key()));
                Assertions.assertEquals("market.price.tick",
                        headerValue(record, KafkaEventHeaderConstants.EVENT_TYPE_HEADER));
                Assertions.assertEquals("falconx-market-service",
                        headerValue(record, KafkaEventHeaderConstants.EVENT_SOURCE_HEADER));
                Assertions.assertNotNull(headerValue(record, KafkaEventHeaderConstants.EVENT_ID_HEADER));
                Assertions.assertNotNull(headerValue(record, KafkaEventHeaderConstants.TRACE_ID_HEADER));
                Assertions.assertEquals(record.key(), payload.path("symbol").asText());
                Assertions.assertEquals("TIINGO_FOREX", payload.path("source").asText());
                Assertions.assertFalse(payload.path("stale").asBoolean());

                Instant quoteTime = parseKafkaTimestamp(payload.path("ts").decimalValue());
                if (earliestQuoteTime == null || quoteTime.isBefore(earliestQuoteTime)) {
                    earliestQuoteTime = quoteTime;
                }
                if (latestQuoteTime == null || quoteTime.isAfter(latestQuoteTime)) {
                    latestQuoteTime = quoteTime;
                }
            }
            Assertions.assertNotNull(earliestQuoteTime);
            Assertions.assertNotNull(latestQuoteTime);
            Assertions.assertTrue(latestQuoteTime.isAfter(earliestQuoteTime) || !latestQuoteTime.equals(earliestQuoteTime));

            String observedSymbol = records.getLast().key();
            waitForCondition(
                    Duration.ofSeconds(10),
                    "未在超时内从 Redis 读到 Tiingo 真源报价 symbol=" + observedSymbol,
                    () -> marketLatestQuoteRepository.findBySymbol(observedSymbol).isPresent()
            );
            waitForCondition(
                    Duration.ofSeconds(10),
                    "未在超时内从 ClickHouse 读到 Tiingo 真源报价 symbol=" + observedSymbol,
                    () -> {
                        Long count = marketAnalyticsTestSupportMapper.countQuoteTickBySymbol(observedSymbol);
                        return count != null && count > 0L;
                    }
            );

            StandardQuote freshQuote = marketLatestQuoteRepository.findBySymbol(observedSymbol).orElseThrow();
            Assertions.assertFalse(freshQuote.stale());

            waitForCondition(
                    Duration.ofSeconds(10),
                    "日志中未观察到 Tiingo 真源解析或缓存写入证据" + System.lineSeparator() + combinedOutput(output),
                    () -> {
                        String logs = combinedOutput(output);
                        return logs.contains("market.tiingo.provider.message.parsed quotes=")
                                && logs.contains("market.quote.cache.write symbol=" + observedSymbol);
                    }
            );

            marketSymbolTestSupportMapper.updateSymbolStatus(observedSymbol, 2);
            marketFeedBootstrapRunner.refreshTiingoSymbolWhitelist();
            waitForLog(output, "market.tiingo.provider.symbols.refreshed", Duration.ofSeconds(10));

            waitForCondition(
                    Duration.ofSeconds(8),
                    "禁用白名单后未观察到 stale=true symbol=" + observedSymbol,
                    () -> marketLatestQuoteRepository.findBySymbol(observedSymbol)
                            .map(StandardQuote::stale)
                            .orElse(false)
            );
        }
    }

    private KafkaConsumer<String, String> createTopicConsumer(String topic) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "market-tiingo-external-it-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties);
        List<TopicPartition> partitions = awaitTopicPartitions(consumer, topic);
        consumer.assign(partitions);
        Map<TopicPartition, Long> endOffsets = consumer.endOffsets(partitions);
        endOffsets.forEach(consumer::seek);
        return consumer;
    }

    private List<TopicPartition> awaitTopicPartitions(KafkaConsumer<String, String> consumer, String topic) {
        long deadline = System.currentTimeMillis() + 10_000L;
        while (System.currentTimeMillis() < deadline) {
            List<PartitionInfo> partitionInfos = consumer.partitionsFor(topic, Duration.ofMillis(500));
            if (partitionInfos != null && !partitionInfos.isEmpty()) {
                return partitionInfos.stream()
                        .map(partitionInfo -> new TopicPartition(partitionInfo.topic(), partitionInfo.partition()))
                        .toList();
            }
        }
        Assertions.fail("测试消费者未能在超时内加载 topic=" + topic + " 的分区信息");
        return List.of();
    }

    private List<ConsumerRecord<String, String>> waitForTrackedTopicRecords(KafkaConsumer<String, String> consumer,
                                                                            Set<String> trackedSymbols,
                                                                            int minimumRecords,
                                                                            Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        List<ConsumerRecord<String, String>> records = new ArrayList<>();
        while (System.currentTimeMillis() < deadline) {
            for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                if (trackedSymbols.contains(record.key())) {
                    records.add(record);
                    if (records.size() >= minimumRecords) {
                        return records;
                    }
                }
            }
        }
        Assertions.fail("未在超时内收到足够的 Tiingo 真源 Kafka 报价，当前记录数=" + records.size());
        return List.of();
    }

    private String headerValue(ConsumerRecord<String, String> record, String headerName) {
        if (record.headers().lastHeader(headerName) == null) {
            return null;
        }
        return new String(record.headers().lastHeader(headerName).value(), StandardCharsets.UTF_8);
    }

    private Instant parseKafkaTimestamp(BigDecimal timestamp) {
        BigDecimal normalized = timestamp.stripTrailingZeros();
        BigDecimal secondsPart = normalized.setScale(0, RoundingMode.DOWN);
        BigDecimal nanosPart = normalized.subtract(secondsPart)
                .movePointRight(9)
                .setScale(0, RoundingMode.HALF_UP);
        return Instant.ofEpochSecond(secondsPart.longValueExact(), nanosPart.longValueExact());
    }
}
