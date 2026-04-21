package com.falconx.market;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.falconx.market.analytics.mapper.test.MarketAnalyticsTestSupportMapper;
import com.falconx.market.application.MarketDataIngestionApplicationService;
import com.falconx.market.config.MarketTraceContextFilter;
import com.falconx.market.provider.TiingoRawQuote;
import com.falconx.market.service.KlineAggregationService;
import com.falconx.market.support.MarketMybatisTestSupportConfiguration;
import com.falconx.market.support.MarketTestDatabaseInitializer;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * market-service 北向 WebSocket 集成测试。
 */
@ActiveProfiles("stage5")
@ContextConfiguration(initializers = MarketTestDatabaseInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(
        classes = {
                MarketServiceApplication.class,
                MarketMybatisTestSupportConfiguration.class
        },
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:mysql://localhost:3306/falconx_market_ws_it?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                "spring.datasource.username=root",
                "spring.datasource.password=root",
                "spring.data.redis.host=localhost",
                "spring.data.redis.port=6379",
                "falconx.market.analytics.jdbc-url=jdbc:clickhouse://localhost:8123/falconx_market_analytics",
                "falconx.market.analytics.username=default",
                "falconx.market.analytics.password=",
                "falconx.market.tiingo.enabled=false",
                "falconx.market.stale.max-age=1s",
                "falconx.market.web-socket.stale-scan-interval=200ms",
                "falconx.market.web-socket.ping-interval=200ms",
                "falconx.market.web-socket.pong-timeout=2s"
        }
)
class MarketWebSocketIntegrationTests {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    @LocalServerPort
    private int port;

    @Autowired
    private MarketDataIngestionApplicationService marketDataIngestionApplicationService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private MarketAnalyticsTestSupportMapper marketAnalyticsTestSupportMapper;

    @Autowired
    private KlineAggregationService klineAggregationService;

    @Autowired
    private MarketTraceContextFilter marketTraceContextFilter;

    private TestWebSocketListener listener;
    private WebSocket webSocket;

    @BeforeEach
    void setUp() {
        stringRedisTemplate.delete("falconx:market:price:EURUSD");
        marketAnalyticsTestSupportMapper.clearAnalyticsTables();
        clearKlineAggregationState();
    }

    @AfterEach
    void tearDown() {
        if (webSocket != null) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "test-finished").join();
        }
    }

    @Test
    void shouldSubscribePriceTickAndKlineFrames(CapturedOutput output) throws Exception {
        connect();
        send("""
                {"type":"subscribe","requestId":"req-001","channels":["price.tick","kline.1m"],"symbols":["EURUSD"]}
                """);

        JsonNode subscribed = waitForJson(node -> "subscribed".equals(node.path("type").asText()));
        Assertions.assertEquals("req-001", subscribed.path("requestId").asText());

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime firstTickTime = now.withSecond(10).withNano(0);
        if (firstTickTime.isAfter(now)) {
            firstTickTime = firstTickTime.minusMinutes(1);
        }
        OffsetDateTime secondTickTime = firstTickTime.withSecond(50);
        OffsetDateTime thirdTickTime = firstTickTime.plusMinutes(1).withSecond(1);

        ingestQuote("EURUSD", "1.08100000", "1.08120000", firstTickTime);
        ingestQuote("EURUSD", "1.08200000", "1.08220000", secondTickTime);
        ingestQuote("EURUSD", "1.08050000", "1.08070000", thirdTickTime);

        JsonNode priceTick = waitForJson(node -> "price.tick".equals(node.path("type").asText())
                && "EURUSD".equals(node.path("symbol").asText())
                && !node.path("stale").asBoolean());
        JsonNode activeKline = waitForJson(node -> "kline.1m".equals(node.path("type").asText())
                && !node.path("isFinal").asBoolean());
        JsonNode finalKline = waitForJson(node -> "kline.1m".equals(node.path("type").asText())
                && node.path("isFinal").asBoolean());

        Assertions.assertEquals("EURUSD", priceTick.path("symbol").asText());
        Assertions.assertEquals("1m", activeKline.path("interval").asText());
        Assertions.assertEquals("1m", finalKline.path("interval").asText());
        String logs = output.toString();
        Assertions.assertTrue(logs.contains("market.websocket.subscribe.accepted"));
        Assertions.assertTrue(logs.contains("market.websocket.price.push symbol=EURUSD"));
        Assertions.assertTrue(logs.contains("market.websocket.kline.push symbol=EURUSD interval=1m"));
    }

    @Test
    void shouldRejectUnknownSymbolSubscription(CapturedOutput output) throws Exception {
        connect();
        send("""
                {"type":"subscribe","requestId":"req-err","channels":["price.tick"],"symbols":["INVALID"]}
                """);

        JsonNode error = waitForJson(node -> "error".equals(node.path("type").asText()));
        Assertions.assertEquals("30001", error.path("code").asText());
        Assertions.assertTrue(error.path("message").asText().contains("INVALID"));
        Assertions.assertTrue(output.toString().contains("market.websocket.session.opened"));
    }

    @Test
    void shouldPushStaleNotificationOnceAfterQuoteExpires(CapturedOutput output) throws Exception {
        connect();
        send("""
                {"type":"subscribe","requestId":"req-stale","channels":["price.tick"],"symbols":["EURUSD"]}
                """);
        waitForJson(node -> "subscribed".equals(node.path("type").asText()));

        ingestQuote("EURUSD", "1.08100000", "1.08120000", OffsetDateTime.now());

        JsonNode stale = waitForJson(node -> "price.tick".equals(node.path("type").asText())
                && node.path("stale").asBoolean());
        Assertions.assertEquals("EURUSD", stale.path("symbol").asText());
        Assertions.assertTrue(stale.path("bid").isMissingNode());
        Assertions.assertTrue(output.toString().contains("market.websocket.price.stale-push symbol=EURUSD"));
    }

    @Test
    void shouldStopPushingPriceTickAfterUnsubscribe(CapturedOutput output) throws Exception {
        connect();
        send("""
                {"type":"subscribe","requestId":"req-sub","channels":["price.tick"],"symbols":["EURUSD"]}
                """);
        waitForJson(node -> "subscribed".equals(node.path("type").asText()));

        send("""
                {"type":"unsubscribe","requestId":"req-unsub","channels":["price.tick"],"symbols":["EURUSD"]}
                """);

        JsonNode unsubscribed = waitForJson(node -> "unsubscribed".equals(node.path("type").asText()));
        Assertions.assertEquals("req-unsub", unsubscribed.path("requestId").asText());

        ingestQuote("EURUSD", "1.08300000", "1.08320000", OffsetDateTime.now());
        assertNoMatchingJson(node -> "price.tick".equals(node.path("type").asText())
                && "EURUSD".equals(node.path("symbol").asText()), Duration.ofSeconds(2));
        Assertions.assertTrue(output.toString().contains("market.websocket.unsubscribe.accepted"));
    }

    @Test
    void shouldReplyPongAndSendProtocolHeartbeat() throws Exception {
        connect();
        send("""
                {"type":"ping","ts":"2026-04-21T12:00:00Z"}
                """);

        JsonNode pong = waitForJson(node -> "pong".equals(node.path("type").asText()));
        Assertions.assertEquals("2026-04-21T12:00:00Z", pong.path("ts").asText());
        Assertions.assertTrue(listener.awaitProtocolPing(Duration.ofSeconds(5)),
                "未在超时内收到服务端协议层 Ping");
    }

    @Test
    void shouldRequireResubscribeAfterReconnect() throws Exception {
        connect();
        send("""
                {"type":"subscribe","requestId":"req-first","channels":["price.tick"],"symbols":["EURUSD"]}
                """);
        waitForJson(node -> "subscribed".equals(node.path("type").asText()));

        webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "reconnect").join();
        webSocket = null;

        connect();
        ingestQuote("EURUSD", "1.08400000", "1.08420000", OffsetDateTime.now());
        assertNoMatchingJson(node -> "price.tick".equals(node.path("type").asText())
                && "EURUSD".equals(node.path("symbol").asText()), Duration.ofSeconds(2));

        send("""
                {"type":"subscribe","requestId":"req-second","channels":["price.tick"],"symbols":["EURUSD"]}
                """);
        waitForJson(node -> "subscribed".equals(node.path("type").asText())
                && "req-second".equals(node.path("requestId").asText()));

        ingestQuote("EURUSD", "1.08500000", "1.08520000", OffsetDateTime.now());
        JsonNode priceTick = waitForJson(node -> "price.tick".equals(node.path("type").asText())
                && "EURUSD".equals(node.path("symbol").asText())
                && !node.path("stale").asBoolean());
        Assertions.assertEquals("EURUSD", priceTick.path("symbol").asText());
    }

    private void connect() {
        listener = new TestWebSocketListener();
        webSocket = HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .buildAsync(URI.create("ws://localhost:" + port + "/ws/v1/market"), listener)
                .join();
    }

    private void send(String payload) {
        webSocket.sendText(payload, true).join();
    }

    private void ingestQuote(String symbol, String bid, String ask, OffsetDateTime ts) {
        marketDataIngestionApplicationService.ingest(new TiingoRawQuote(
                symbol,
                new BigDecimal(bid),
                new BigDecimal(ask),
                ts
        ));
    }

    private JsonNode waitForJson(JsonMatcher matcher) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000L;
        while (System.currentTimeMillis() < deadline) {
            String payload = listener.textFrames.poll(500, TimeUnit.MILLISECONDS);
            if (payload == null) {
                continue;
            }
            JsonNode jsonNode = OBJECT_MAPPER.readTree(payload);
            if (matcher.matches(jsonNode)) {
                return jsonNode;
            }
        }
        Assertions.fail("未在超时内收到匹配的 WebSocket 文本帧");
        return null;
    }

    private void assertNoMatchingJson(JsonMatcher matcher, Duration duration) throws Exception {
        long deadline = System.currentTimeMillis() + duration.toMillis();
        while (System.currentTimeMillis() < deadline) {
            String payload = listener.textFrames.poll(200, TimeUnit.MILLISECONDS);
            if (payload == null) {
                continue;
            }
            JsonNode jsonNode = OBJECT_MAPPER.readTree(payload);
            if (matcher.matches(jsonNode)) {
                Assertions.fail("在不应推送的窗口内收到了匹配的 WebSocket 文本帧: " + payload);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void clearKlineAggregationState() {
        Object bucketsField = ReflectionTestUtils.getField(klineAggregationService, "buckets");
        if (bucketsField instanceof Map<?, ?> buckets) {
            buckets.clear();
        }
    }

    @FunctionalInterface
    private interface JsonMatcher {
        boolean matches(JsonNode node);
    }

    private static final class TestWebSocketListener implements WebSocket.Listener {
        private final BlockingQueue<String> textFrames = new LinkedBlockingQueue<>();
        private final BlockingQueue<String> protocolPings = new LinkedBlockingQueue<>();
        private final StringBuilder textBuffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletableFuture<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            textBuffer.append(data);
            if (last) {
                textFrames.offer(textBuffer.toString());
                textBuffer.setLength(0);
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<?> onPong(WebSocket webSocket, java.nio.ByteBuffer message) {
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<?> onPing(WebSocket webSocket, ByteBuffer message) {
            protocolPings.offer("ping");
            webSocket.request(1);
            return webSocket.sendPong(message);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            textFrames.offer("{\"type\":\"error\",\"code\":\"TEST\",\"message\":\"" + error.getMessage() + "\"}");
        }

        private boolean awaitProtocolPing(Duration timeout) throws InterruptedException {
            return protocolPings.poll(timeout.toMillis(), TimeUnit.MILLISECONDS) != null;
        }
    }
}
