package com.falconx.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.falconx.domain.enums.ChainType;
import com.falconx.identity.IdentityServiceApplication;
import com.falconx.identity.config.IdentityServiceProperties;
import com.falconx.infrastructure.kafka.KafkaEventHeaderConstants;
import com.falconx.infrastructure.kafka.KafkaEventMessageSupport;
import com.falconx.infrastructure.trace.TraceIdConstants;
import com.falconx.market.contract.event.MarketPriceTickEventPayload;
import com.falconx.trading.TradingCoreServiceApplication;
import com.falconx.trading.engine.OpenPositionSnapshotStore;
import com.falconx.trading.engine.QuoteDrivenEngine;
import com.falconx.trading.producer.TradingOutboxDispatcher;
import com.falconx.trading.repository.RedisTradingScheduleSnapshotRepository;
import com.falconx.trading.service.model.TradingScheduleSnapshot;
import com.falconx.trading.service.model.TradingSessionWindow;
import com.falconx.wallet.contract.event.WalletDepositConfirmedEventPayload;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import javax.sql.DataSource;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.http.MediaType;

/**
 * gateway + identity-service + trading-core-service + Kafka 最小主链路 E2E 测试。
 *
 * <p>该测试必须通过 gateway 作为唯一 HTTP 入口，配合真实 Kafka 主题与真实下游应用上下文，
 * 验证 Milestone A 仍缺失的最小联合主链路：
 *
 * <ul>
 *   <li>注册得到 `PENDING_DEPOSIT` 用户</li>
 *   <li>`wallet.deposit.confirmed` 进入 trading-core 完成入账并产出 `trading.deposit.credited`</li>
 *   <li>identity-service 消费 credited 事件并把用户激活为 `ACTIVE`</li>
 *   <li>用户可经 gateway 登录、查询账户并完成最小开仓</li>
 * </ul>
 */
@SpringBootTest(classes = GatewayApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayMinimalMainlineE2ETests {

    private static final String WALLET_DEPOSIT_CONFIRMED_TOPIC = "falconx.wallet.deposit.confirmed";
    private static final String TRADING_DEPOSIT_CREDITED_TOPIC = "falconx.trading.deposit.credited";
    private static final int IDENTITY_USER_STATUS_ACTIVE = 1;
    private static final String IDENTITY_CONFIG_LOCATION = moduleResourceLocation(
            "falconx-identity-service",
            "src/main/resources/application.yml"
    );
    private static final String TRADING_CONFIG_LOCATION = moduleResourceLocation(
            "falconx-trading-core-service",
            "src/main/resources/application.yml"
    );
    private static final String IDENTITY_FLYWAY_LOCATION = moduleResourceLocation(
            "falconx-identity-service",
            "src/main/resources/db/migration"
    );
    private static final String TRADING_FLYWAY_LOCATION = moduleResourceLocation(
            "falconx-trading-core-service",
            "src/main/resources/db/migration"
    );
    private static final String IDENTITY_DB_NAME = "falconx_identity_gateway_e2e_" + randomSuffix();
    private static final String TRADING_DB_NAME = "falconx_trading_gateway_e2e_" + randomSuffix();
    private static final StartedServiceHolder IDENTITY_SERVICE = new StartedServiceHolder(
            IdentityServiceApplication.class,
            "stage5",
            List.of(
                    "spring.main.web-application-type=servlet",
                    "spring.cloud.gateway.enabled=false",
                    "spring.config.location=file:" + IDENTITY_CONFIG_LOCATION,
                    "spring.flyway.locations=filesystem:" + IDENTITY_FLYWAY_LOCATION,
                    "spring.autoconfigure.exclude=org.springframework.cloud.gateway.config.GatewayAutoConfiguration,"
                            + "org.springframework.cloud.gateway.config.GatewayClassPathWarningAutoConfiguration,"
                            + "org.springframework.cloud.gateway.config.GatewayRedisAutoConfiguration",
                    "server.port=0",
                    "spring.datasource.url=jdbc:mysql://localhost:3306/" + IDENTITY_DB_NAME
                            + "?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                    "spring.datasource.username=root",
                    "spring.datasource.password=root",
                    "falconx.identity.kafka.consumer-group-id=gateway-e2e-identity-" + randomSuffix()
            )
    );
    private static final StartedServiceHolder TRADING_SERVICE = new StartedServiceHolder(
            TradingCoreServiceApplication.class,
            "stage5",
            List.of(
                    "spring.main.web-application-type=servlet",
                    "spring.cloud.gateway.enabled=false",
                    "spring.config.location=file:" + TRADING_CONFIG_LOCATION,
                    "spring.flyway.locations=filesystem:" + TRADING_FLYWAY_LOCATION,
                    "spring.autoconfigure.exclude=org.springframework.cloud.gateway.config.GatewayAutoConfiguration,"
                            + "org.springframework.cloud.gateway.config.GatewayClassPathWarningAutoConfiguration,"
                            + "org.springframework.cloud.gateway.config.GatewayRedisAutoConfiguration",
                    "server.port=0",
                    "spring.datasource.url=jdbc:mysql://localhost:3306/" + TRADING_DB_NAME
                            + "?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                    "spring.datasource.username=root",
                    "spring.datasource.password=root",
                    "spring.data.redis.host=localhost",
                    "spring.data.redis.port=6379",
                    "falconx.trading.kafka.consumer-group-id=gateway-e2e-trading-" + randomSuffix()
            )
    );

    @LocalServerPort
    private int gatewayPort;

    @Autowired
    private ObjectMapper objectMapper;

    private WebTestClient webTestClient;

    @DynamicPropertySource
    static void registerGatewayRouteProperties(DynamicPropertyRegistry registry) {
        registry.add("falconx.gateway.routes.identity-base-url", IDENTITY_SERVICE::baseUrl);
        registry.add("falconx.gateway.routes.trading-base-url", TRADING_SERVICE::baseUrl);
        registry.add("falconx.gateway.routes.market-base-url", TRADING_SERVICE::baseUrl);
        registry.add("falconx.gateway.routes.wallet-base-url", TRADING_SERVICE::baseUrl);
        registry.add("falconx.gateway.security.public-key-pem",
                () -> IDENTITY_SERVICE.getBean(IdentityServiceProperties.class).getKeyPair().getPublicKeyPem());
    }

    @AfterAll
    static void stopStartedServices() {
        TRADING_SERVICE.close();
        IDENTITY_SERVICE.close();
    }

    @BeforeEach
    void setUpWebTestClient() {
        this.webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + gatewayPort)
                .responseTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Test
    void shouldCompleteRegisterDepositActivateLoginAndOpenPositionThroughGatewayAndKafka() throws Exception {
        waitForKafkaListenerAssignment("trading", TRADING_SERVICE.getBean(KafkaListenerEndpointRegistry.class));
        waitForKafkaListenerAssignment("identity", IDENTITY_SERVICE.getBean(KafkaListenerEndpointRegistry.class));
        resetTradingPreheatState();

        String email = "gateway.e2e." + randomSuffix() + "@example.com";
        String password = "Passw0rd!";
        BigDecimal depositAmount = new BigDecimal("1500.00000000");
        String walletConfirmedEventId = "evt-gw-wallet-confirmed-" + shortRandomSuffix(24);
        String walletTraceId = "trace-gateway-e2e-wallet-" + randomSuffix();
        long walletTxId = System.currentTimeMillis();
        String txHash = "0xgatewaye2e" + randomSuffix();

        EntityExchangeResult<byte[]> registerResult = webTestClient.post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "email": "%s",
                          "password": "%s"
                        }
                        """.formatted(email, password))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists(TraceIdConstants.TRACE_ID_HEADER)
                .expectBody()
                .returnResult();
        assertHasTraceHeader(registerResult);

        JsonNode registerJson = readJson(registerResult);
        Assertions.assertEquals("0", registerJson.path("code").asText());
        Assertions.assertEquals("PENDING_DEPOSIT", registerJson.path("data").path("status").asText());
        long userId = registerJson.path("data").path("userId").asLong();

        try (KafkaConsumer<String, String> creditedConsumer = createTopicConsumer(TRADING_DEPOSIT_CREDITED_TOPIC)) {
            sendWalletDepositConfirmed(walletConfirmedEventId, walletTraceId, new WalletDepositConfirmedEventPayload(
                    walletTxId,
                    userId,
                    ChainType.ETH,
                    "USDT",
                    txHash,
                    "0xgatewaye2efrom",
                    "0xgatewaye2eto",
                    depositAmount,
                    12,
                    12,
                    OffsetDateTime.now().minusMinutes(10)
            ));

            waitForAssertion(() -> {
                Assertions.assertEquals(1L, countRows(
                        TRADING_SERVICE.getBean(DataSource.class),
                        "SELECT COUNT(1) FROM t_inbox WHERE event_id = ? AND status = 1",
                        walletConfirmedEventId
                ));
                Assertions.assertEquals(1L, countRows(
                        TRADING_SERVICE.getBean(DataSource.class),
                        "SELECT COUNT(1) FROM t_deposit WHERE wallet_tx_id = ?",
                        walletTxId
                ));
                Assertions.assertEquals(1L, countRows(
                        TRADING_SERVICE.getBean(DataSource.class),
                        "SELECT COUNT(1) FROM t_outbox WHERE event_type = ?",
                        "trading.deposit.credited"
                ));
                Assertions.assertEquals(0, depositAmount.compareTo(decimalValue(
                        TRADING_SERVICE.getBean(DataSource.class),
                        "SELECT balance FROM t_account WHERE user_id = ?",
                        userId
                )));
            }, "wallet.deposit.confirmed 未驱动 trading-core 完成真实业务入账");

            dispatchTradingOutboxNow();
            ConsumerRecord<String, String> creditedRecord = waitForCreditedRecord(creditedConsumer, txHash);
            String creditedEventId = requiredHeader(creditedRecord, KafkaEventHeaderConstants.EVENT_ID_HEADER);
            String creditedTraceId = requiredHeader(creditedRecord, KafkaEventHeaderConstants.TRACE_ID_HEADER);
            Assertions.assertFalse(creditedTraceId.isBlank());

            waitForAssertion(() -> {
                Assertions.assertEquals(1L, countRows(
                        IDENTITY_SERVICE.getBean(DataSource.class),
                        "SELECT COUNT(1) FROM t_inbox WHERE event_id = ? AND status = 1",
                        creditedEventId
                ));
                Assertions.assertEquals(IDENTITY_USER_STATUS_ACTIVE, intValue(
                        IDENTITY_SERVICE.getBean(DataSource.class),
                        "SELECT status FROM t_user WHERE id = ?",
                        userId
                ));
            }, "identity-service 未在真实 Kafka 入口下完成用户激活");

            EntityExchangeResult<byte[]> loginResult = webTestClient.post()
                    .uri("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {
                              "email": "%s",
                              "password": "%s"
                            }
                            """.formatted(email, password))
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().exists(TraceIdConstants.TRACE_ID_HEADER)
                    .expectBody()
                    .returnResult();
            assertHasTraceHeader(loginResult);

            JsonNode loginJson = readJson(loginResult);
            Assertions.assertEquals("0", loginJson.path("code").asText());
            String accessToken = loginJson.path("data").path("accessToken").asText();
            Assertions.assertFalse(accessToken.isBlank());

            preheatTradingForMarketOrder();

            EntityExchangeResult<byte[]> accountBeforeOrderResult = webTestClient.get()
                    .uri("/api/v1/trading/accounts/me")
                    .header("Authorization", "Bearer " + accessToken)
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().exists(TraceIdConstants.TRACE_ID_HEADER)
                    .expectBody()
                    .returnResult();
            assertHasTraceHeader(accountBeforeOrderResult);

            JsonNode accountBeforeOrderJson = readJson(accountBeforeOrderResult);
            Assertions.assertEquals("0", accountBeforeOrderJson.path("code").asText());
            Assertions.assertTrue(new BigDecimal(accountBeforeOrderJson.path("data").path("balance").asText())
                    .compareTo(BigDecimal.ZERO) > 0);
            Assertions.assertEquals(0, depositAmount.compareTo(decimalValue(
                    TRADING_SERVICE.getBean(DataSource.class),
                    "SELECT balance FROM t_account WHERE user_id = ?",
                    userId
            )));

            String clientOrderId = "gw-e2e-order-" + shortRandomSuffix(12);

            EntityExchangeResult<byte[]> marketOrderResult = webTestClient.post()
                    .uri("/api/v1/trading/orders/market")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""
                            {
                              "symbol": "BTCUSDT",
                              "side": "BUY",
                              "quantity": 1.0,
                              "leverage": 10,
                              "takeProfitPrice": 10100.0,
                              "stopLossPrice": 9800.0,
                              "clientOrderId": "%s"
                            }
                            """.formatted(clientOrderId))
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().exists(TraceIdConstants.TRACE_ID_HEADER)
                    .expectBody()
                    .returnResult();
            assertHasTraceHeader(marketOrderResult);

            JsonNode marketOrderJson = readJson(marketOrderResult);
            Assertions.assertEquals("0", marketOrderJson.path("code").asText());
            Assertions.assertFalse(marketOrderJson.path("data").path("positionId").isMissingNode());

            EntityExchangeResult<byte[]> accountAfterOrderResult = webTestClient.get()
                    .uri("/api/v1/trading/accounts/me")
                    .header("Authorization", "Bearer " + accessToken)
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().exists(TraceIdConstants.TRACE_ID_HEADER)
                    .expectBody()
                    .returnResult();
            assertHasTraceHeader(accountAfterOrderResult);

            JsonNode accountAfterOrderJson = readJson(accountAfterOrderResult);
            Assertions.assertEquals("0", accountAfterOrderJson.path("code").asText());
            Assertions.assertTrue(accountAfterOrderJson.path("data").path("openPositions").isArray());
            Assertions.assertTrue(accountAfterOrderJson.path("data").path("openPositions").size() > 0);

            Assertions.assertEquals(1L, countRows(
                    TRADING_SERVICE.getBean(DataSource.class),
                    "SELECT COUNT(1) FROM t_inbox WHERE event_id = ? AND status = 1",
                    walletConfirmedEventId
            ));
            Assertions.assertEquals(1L, countRows(
                    TRADING_SERVICE.getBean(DataSource.class),
                    "SELECT COUNT(1) FROM t_outbox WHERE event_type = ?",
                    "trading.deposit.credited"
            ));
            Assertions.assertEquals(1L, countRows(
                    IDENTITY_SERVICE.getBean(DataSource.class),
                    "SELECT COUNT(1) FROM t_inbox WHERE event_id = ? AND status = 1",
                    creditedEventId
            ));
            Assertions.assertEquals(IDENTITY_USER_STATUS_ACTIVE, intValue(
                    IDENTITY_SERVICE.getBean(DataSource.class),
                    "SELECT status FROM t_user WHERE id = ?",
                    userId
            ));
            Assertions.assertTrue(countRows(
                    TRADING_SERVICE.getBean(DataSource.class),
                    "SELECT COUNT(1) FROM t_position WHERE user_id = ? AND status = 1",
                    userId
            ) > 0);
        }
    }

    private void sendWalletDepositConfirmed(String eventId,
                                            String traceId,
                                            WalletDepositConfirmedEventPayload payload) throws Exception {
        KafkaTemplate<String, String> kafkaTemplate = TRADING_SERVICE.getBean(KafkaTemplate.class);
        MDC.put(TraceIdConstants.TRACE_ID_MDC_KEY, traceId);
        try {
            kafkaTemplate.send(KafkaEventMessageSupport.buildJsonMessage(
                            WALLET_DEPOSIT_CONFIRMED_TOPIC,
                            payload.chain().name() + ":" + payload.txHash(),
                            objectMapper.writeValueAsString(payload),
                            eventId,
                            "wallet.deposit.confirmed",
                            "falconx-wallet-service"
                    ))
                    .get();
        } finally {
            MDC.remove(TraceIdConstants.TRACE_ID_MDC_KEY);
        }
    }

    private void dispatchTradingOutboxNow() {
        TRADING_SERVICE.getBean(TradingOutboxDispatcher.class).dispatchPendingMessages();
    }

    private void preheatTradingForMarketOrder() {
        StringRedisTemplate stringRedisTemplate = TRADING_SERVICE.getBean(StringRedisTemplate.class);
        stringRedisTemplate.delete("falconx:trading:quote:snapshot:BTCUSDT");
        stringRedisTemplate.delete("falconx:market:trading:schedule:BTCUSDT");
        TRADING_SERVICE.getBean(RedisTradingScheduleSnapshotRepository.class).saveForTest(new TradingScheduleSnapshot(
                "BTCUSDT",
                "CRYPTO",
                alwaysOpenSessions(),
                List.of(),
                List.of(),
                OffsetDateTime.now()
        ));
        TRADING_SERVICE.getBean(QuoteDrivenEngine.class).processTick(new MarketPriceTickEventPayload(
                "BTCUSDT",
                new BigDecimal("9990.00000000"),
                new BigDecimal("10000.00000000"),
                new BigDecimal("9995.00000000"),
                new BigDecimal("9995.00000000"),
                OffsetDateTime.now(),
                "gateway-e2e-test",
                false
        ));
    }

    private void resetTradingPreheatState() {
        StringRedisTemplate stringRedisTemplate = TRADING_SERVICE.getBean(StringRedisTemplate.class);
        stringRedisTemplate.delete("falconx:trading:quote:snapshot:BTCUSDT");
        stringRedisTemplate.delete("falconx:market:trading:schedule:BTCUSDT");
        TRADING_SERVICE.getBean(OpenPositionSnapshotStore.class).replaceAll(List.of());
    }

    private void waitForKafkaListenerAssignment(String label, KafkaListenerEndpointRegistry registry) throws Exception {
        waitForAssertion(() -> {
            Assertions.assertFalse(registry.getListenerContainers().isEmpty());
            boolean ready = registry.getListenerContainers().stream().allMatch(this::hasAssignments);
            Assertions.assertTrue(ready);
        }, label + " Kafka listener 未在超时内完成分区分配");
    }

    private boolean hasAssignments(MessageListenerContainer container) {
        return container.isRunning()
                && container.getAssignedPartitions() != null
                && !container.getAssignedPartitions().isEmpty();
    }

    private KafkaConsumer<String, String> createTopicConsumer(String topic) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "gateway-e2e-observer-" + randomSuffix());
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

    private ConsumerRecord<String, String> waitForCreditedRecord(KafkaConsumer<String, String> consumer, String txHash)
            throws Exception {
        long deadline = System.currentTimeMillis() + 12_000L;
        while (System.currentTimeMillis() < deadline) {
            for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                if (record.value() != null && record.value().contains(txHash)) {
                    return record;
                }
            }
        }
        throw new AssertionError("未收到 txHash=" + txHash + " 对应的 trading.deposit.credited Kafka 消息");
    }

    private String requiredHeader(ConsumerRecord<String, String> record, String headerName) {
        Header header = record.headers().lastHeader(headerName);
        Assertions.assertNotNull(header, "Kafka 消息头缺少 " + headerName);
        return new String(header.value(), StandardCharsets.UTF_8);
    }

    private void waitForAssertion(Runnable assertion, String failureMessage) throws Exception {
        AssertionError lastError = null;
        long deadline = System.currentTimeMillis() + 15_000L;
        while (System.currentTimeMillis() < deadline) {
            try {
                assertion.run();
                return;
            } catch (AssertionError error) {
                lastError = error;
                Thread.sleep(250L);
            }
        }
        if (lastError != null) {
            throw lastError;
        }
        throw new AssertionError(failureMessage);
    }

    private JsonNode readJson(EntityExchangeResult<byte[]> result) throws Exception {
        return objectMapper.readTree(Objects.requireNonNullElse(result.getResponseBodyContent(), new byte[0]));
    }

    private void assertHasTraceHeader(EntityExchangeResult<byte[]> result) {
        String traceId = result.getResponseHeaders().getFirst(TraceIdConstants.TRACE_ID_HEADER);
        Assertions.assertNotNull(traceId);
        Assertions.assertFalse(traceId.isBlank());
    }

    private long countRows(DataSource dataSource, String sql, Object... parameters) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindParameters(statement, parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                Assertions.assertTrue(resultSet.next());
                return resultSet.getLong(1);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("执行计数 SQL 失败: " + sql, exception);
        }
    }

    private BigDecimal decimalValue(DataSource dataSource, String sql, Object... parameters) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindParameters(statement, parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                Assertions.assertTrue(resultSet.next());
                BigDecimal value = resultSet.getBigDecimal(1);
                Assertions.assertNotNull(value);
                return value;
            }
        } catch (Exception exception) {
            throw new IllegalStateException("执行数值 SQL 失败: " + sql, exception);
        }
    }

    private int intValue(DataSource dataSource, String sql, Object... parameters) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindParameters(statement, parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                Assertions.assertTrue(resultSet.next());
                return resultSet.getInt(1);
            }
        } catch (Exception exception) {
            throw new IllegalStateException("执行整型 SQL 失败: " + sql, exception);
        }
    }

    private void bindParameters(PreparedStatement statement, Object... parameters) throws Exception {
        for (int index = 0; index < parameters.length; index++) {
            statement.setObject(index + 1, parameters[index]);
        }
    }

    private List<TradingSessionWindow> alwaysOpenSessions() {
        return List.of(
                new TradingSessionWindow(1, 1, LocalTime.of(0, 0), LocalTime.of(23, 59, 59), "UTC", true, LocalDate.of(2026, 1, 1), null),
                new TradingSessionWindow(2, 1, LocalTime.of(0, 0), LocalTime.of(23, 59, 59), "UTC", true, LocalDate.of(2026, 1, 1), null),
                new TradingSessionWindow(3, 1, LocalTime.of(0, 0), LocalTime.of(23, 59, 59), "UTC", true, LocalDate.of(2026, 1, 1), null),
                new TradingSessionWindow(4, 1, LocalTime.of(0, 0), LocalTime.of(23, 59, 59), "UTC", true, LocalDate.of(2026, 1, 1), null),
                new TradingSessionWindow(5, 1, LocalTime.of(0, 0), LocalTime.of(23, 59, 59), "UTC", true, LocalDate.of(2026, 1, 1), null),
                new TradingSessionWindow(6, 1, LocalTime.of(0, 0), LocalTime.of(23, 59, 59), "UTC", true, LocalDate.of(2026, 1, 1), null),
                new TradingSessionWindow(7, 1, LocalTime.of(0, 0), LocalTime.of(23, 59, 59), "UTC", true, LocalDate.of(2026, 1, 1), null)
        );
    }

    private static String randomSuffix() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String shortRandomSuffix(int maxLength) {
        String suffix = randomSuffix();
        return suffix.substring(0, Math.min(maxLength, suffix.length()));
    }

    private static String moduleResourceLocation(String moduleName, String relativePath) {
        Path userDir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        List<Path> candidates = List.of(
                userDir.resolve(moduleName).resolve(relativePath),
                userDir.resolve("..").resolve(moduleName).resolve(relativePath)
        );
        return candidates.stream()
                .map(Path::normalize)
                .filter(Files::exists)
                .map(Path::toAbsolutePath)
                .findFirst()
                .map(Path::toString)
                .orElseThrow(() -> new IllegalStateException("未找到模块资源: " + moduleName + " -> " + relativePath));
    }

    /**
     * 供 gateway E2E 测试复用的下游真实应用上下文持有器。
     */
    private static final class StartedServiceHolder {

        private final Class<?> applicationClass;
        private final String profile;
        private final List<String> properties;
        private ConfigurableApplicationContext context;
        private int port;

        private StartedServiceHolder(Class<?> applicationClass, String profile, List<String> properties) {
            this.applicationClass = applicationClass;
            this.profile = profile;
            this.properties = properties;
        }

        private synchronized String baseUrl() {
            startIfNecessary();
            return "http://localhost:" + port;
        }

        private synchronized <T> T getBean(Class<T> beanType) {
            startIfNecessary();
            return context.getBean(beanType);
        }

        private synchronized void close() {
            if (context != null) {
                context.close();
                context = null;
            }
        }

        private void startIfNecessary() {
            if (context != null) {
                return;
            }
            String previousWebApplicationType = System.getProperty("spring.main.web-application-type");
            String previousAutoConfigurationExclude = System.getProperty("spring.autoconfigure.exclude");
            try {
                System.setProperty("spring.main.web-application-type", "servlet");
                System.clearProperty("spring.autoconfigure.exclude");
                context = new SpringApplicationBuilder(applicationClass)
                        .profiles(profile)
                        .run(properties.stream()
                                .map(property -> "--" + property)
                                .toArray(String[]::new));
            } finally {
                if (previousWebApplicationType == null) {
                    System.clearProperty("spring.main.web-application-type");
                } else {
                    System.setProperty("spring.main.web-application-type", previousWebApplicationType);
                }
                if (previousAutoConfigurationExclude == null) {
                    System.clearProperty("spring.autoconfigure.exclude");
                } else {
                    System.setProperty("spring.autoconfigure.exclude", previousAutoConfigurationExclude);
                }
            }
            Integer localPort = context.getEnvironment().getProperty("local.server.port", Integer.class);
            Assertions.assertNotNull(localPort, "下游应用上下文未暴露 local.server.port");
            port = localPort;
        }
    }
}
