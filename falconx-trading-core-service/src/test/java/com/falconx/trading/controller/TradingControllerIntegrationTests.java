package com.falconx.trading.controller;

import com.falconx.market.contract.event.MarketPriceTickEventPayload;
import com.falconx.trading.TradingCoreServiceApplication;
import com.falconx.trading.config.TradingCoreServiceProperties;
import com.falconx.trading.config.TradingTraceContextFilter;
import com.falconx.trading.engine.OpenPositionSnapshotStore;
import com.falconx.trading.engine.QuoteDrivenEngine;
import com.falconx.trading.repository.RedisTradingScheduleSnapshotRepository;
import com.falconx.trading.repository.mapper.test.TradingTestSupportMapper;
import com.falconx.trading.service.TradingAccountService;
import com.falconx.trading.service.model.TradingHolidayRule;
import com.falconx.trading.service.model.TradingHoursExceptionRule;
import com.falconx.trading.service.model.TradingScheduleSnapshot;
import com.falconx.trading.service.model.TradingSessionWindow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * trading-core-service 控制器集成测试。
 *
 * <p>该测试覆盖 Stage 5 真实基础设施下的最小交易北向接口，
 * 验证账户查询、市价单受理和统一错误响应可以通过 HTTP 层正常工作。
 */
@ActiveProfiles("stage5")
@SpringBootTest(
        classes = TradingCoreServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.datasource.url=jdbc:mysql://localhost:3306/falconx_trading_it?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                "spring.datasource.username=root",
                "spring.datasource.password=root",
                "spring.data.redis.host=localhost",
                "spring.data.redis.port=6379"
        }
)
class TradingControllerIntegrationTests {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private TradingTraceContextFilter tradingTraceContextFilter;

    @Autowired
    private TradingAccountService tradingAccountService;

    @Autowired
    private QuoteDrivenEngine quoteDrivenEngine;

    @Autowired
    private TradingCoreServiceProperties tradingCoreServiceProperties;

    @Autowired
    private TradingTestSupportMapper tradingTestSupportMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedisTradingScheduleSnapshotRepository tradingScheduleSnapshotRepository;

    @Autowired
    private OpenPositionSnapshotStore openPositionSnapshotStore;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        tradingTestSupportMapper.clearOwnerTables();
        openPositionSnapshotStore.replaceAll(List.of());
        stringRedisTemplate.delete("falconx:trading:quote:snapshot:BTCUSDT");
        stringRedisTemplate.delete("falconx:trading:quote:snapshot:ETHUSDT");
        stringRedisTemplate.delete("falconx:market:trading:schedule:BTCUSDT");
        stringRedisTemplate.delete("falconx:market:trading:schedule:ETHUSDT");
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(tradingTraceContextFilter)
                .build();
    }

    @Test
    void shouldReturnCurrentAccountSnapshot() throws Exception {
        long userId = 31001L;
        tradingAccountService.creditDeposit(
                userId,
                tradingCoreServiceProperties.getSettlementToken(),
                new BigDecimal("1500.00000000"),
                "it-credit-31001",
                "seed-31001",
                OffsetDateTime.now()
        );
        seedAlwaysOpenSchedule("BTCUSDT", "CRYPTO");
        quoteDrivenEngine.processTick(new MarketPriceTickEventPayload(
                "BTCUSDT",
                new BigDecimal("9990.00000000"),
                new BigDecimal("10000.00000000"),
                new BigDecimal("9995.00000000"),
                new BigDecimal("9995.00000000"),
                OffsetDateTime.now(),
                "integration-test",
                false
        ));
        post("/api/v1/trading/orders/market", """
                {
                  "symbol": "BTCUSDT",
                  "side": "BUY",
                  "quantity": 1.0,
                  "leverage": 10,
                  "takeProfitPrice": 10200.0,
                  "stopLossPrice": 9800.0,
                  "clientOrderId": "integration-order-31001"
                }
                """, userId);

        MockHttpServletResponse response = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/trading/accounts/me")
                        .header("X-User-Id", String.valueOf(userId)))
                .andReturn()
                .getResponse();
        String body = response.getContentAsString();

        Assertions.assertEquals(200, response.getStatus());
        Assertions.assertNotNull(response.getHeader("X-Trace-Id"));
        Assertions.assertTrue(body.contains("\"code\":\"0\""));
        Assertions.assertTrue(body.contains("\"currency\":\"USDT\""));
        Assertions.assertTrue(body.contains("\"openPositions\""));
        Assertions.assertTrue(body.contains("\"unrealizedPnl\":-5.00000000"));
        Assertions.assertTrue(body.contains("\"takeProfitPrice\":10200"));
    }

    @Test
    void shouldPlaceFilledMarketOrderSuccessfully() throws Exception {
        long userId = 31002L;
        tradingAccountService.creditDeposit(
                userId,
                tradingCoreServiceProperties.getSettlementToken(),
                new BigDecimal("2000.00000000"),
                "it-credit-31002",
                "seed-31002",
                OffsetDateTime.now()
        );
        seedAlwaysOpenSchedule("BTCUSDT", "CRYPTO");
        quoteDrivenEngine.processTick(new MarketPriceTickEventPayload(
                "BTCUSDT",
                new BigDecimal("9990.00000000"),
                new BigDecimal("10000.00000000"),
                new BigDecimal("9995.00000000"),
                new BigDecimal("9995.00000000"),
                OffsetDateTime.now(),
                "integration-test",
                false
        ));

        MockHttpServletResponse response = post("/api/v1/trading/orders/market", """
                {
                  "symbol": "BTCUSDT",
                  "side": "BUY",
                  "quantity": 1.0,
                  "leverage": 10,
                  "takeProfitPrice": 10100.0,
                  "stopLossPrice": 9800.0,
                  "clientOrderId": "integration-order-31002"
                }
                """, userId);
        String body = response.getContentAsString();

        Assertions.assertEquals(200, response.getStatus());
        Assertions.assertTrue(body.contains("\"code\":\"0\""));
        Assertions.assertTrue(body.contains("\"orderStatus\":\"FILLED\""));
        Assertions.assertTrue(body.contains("\"symbol\":\"BTCUSDT\""));
        Assertions.assertTrue(body.contains("\"positionId\":"));
        Assertions.assertTrue(body.contains("\"marginUsed\":1000.00000000"));
        Assertions.assertTrue(body.contains("\"takeProfitPrice\":10100.0"));
        Assertions.assertTrue(body.contains("\"stopLossPrice\":9800.0"));
        Assertions.assertNotNull(response.getHeader("X-Trace-Id"));
    }

    @Test
    void shouldReturnRejectedBusinessCodeWhenQuoteIsStale() throws Exception {
        long userId = 31003L;
        tradingAccountService.creditDeposit(
                userId,
                tradingCoreServiceProperties.getSettlementToken(),
                new BigDecimal("2000.00000000"),
                "it-credit-31003",
                "seed-31003",
                OffsetDateTime.now()
        );
        seedAlwaysOpenSchedule("ETHUSDT", "CRYPTO");
        // stale 现在按“读取时当前时间 - quote.ts”动态计算，因此这里必须写入一个已过期的时间戳，
        // 不能再依赖 payload 自带的 `stale=true` 静态提示位。
        quoteDrivenEngine.processTick(new MarketPriceTickEventPayload(
                "ETHUSDT",
                new BigDecimal("1990.00000000"),
                new BigDecimal("2000.00000000"),
                new BigDecimal("1995.00000000"),
                new BigDecimal("1995.00000000"),
                OffsetDateTime.now().minusSeconds(10),
                "integration-test",
                false
        ));

        MockHttpServletResponse response = post("/api/v1/trading/orders/market", """
                {
                  "symbol": "ETHUSDT",
                  "side": "SELL",
                  "quantity": 1.0,
                  "leverage": 10,
                  "takeProfitPrice": 1800.0,
                  "stopLossPrice": 2100.0,
                  "clientOrderId": "integration-order-31003"
                }
                """, userId);
        String body = response.getContentAsString();

        Assertions.assertEquals(200, response.getStatus());
        Assertions.assertTrue(body.contains("\"code\":\"40002\""));
        Assertions.assertTrue(body.contains("\"message\":\"Order Rejected\""));
        Assertions.assertTrue(body.contains("\"rejectionReason\":\"MARKET_QUOTE_STALE\""));
    }

    @Test
    void shouldReturnTradingSuspendedWhenHolidayBlocksSymbol() throws Exception {
        long userId = 31004L;
        tradingAccountService.creditDeposit(
                userId,
                tradingCoreServiceProperties.getSettlementToken(),
                new BigDecimal("2000.00000000"),
                "it-credit-31004",
                "seed-31004",
                OffsetDateTime.now()
        );
        seedHolidayClosedSchedule("BTCUSDT", "CRYPTO");
        quoteDrivenEngine.processTick(new MarketPriceTickEventPayload(
                "BTCUSDT",
                new BigDecimal("9990.00000000"),
                new BigDecimal("10000.00000000"),
                new BigDecimal("9995.00000000"),
                new BigDecimal("9995.00000000"),
                OffsetDateTime.now(),
                "integration-test",
                false
        ));

        MockHttpServletResponse response = post("/api/v1/trading/orders/market", """
                {
                  "symbol": "BTCUSDT",
                  "side": "BUY",
                  "quantity": 1.0,
                  "leverage": 10,
                  "clientOrderId": "integration-order-31004"
                }
                """, userId);
        String body = response.getContentAsString();

        Assertions.assertEquals(200, response.getStatus());
        Assertions.assertTrue(body.contains("\"code\":\"40008\""));
        Assertions.assertTrue(body.contains("\"message\":\"Symbol Trading Suspended\""));
        Assertions.assertTrue(body.contains("\"rejectionReason\":\"SYMBOL_TRADING_SUSPENDED\""));
    }

    private MockHttpServletResponse post(String path, String body, long userId) throws Exception {
        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders.post(path)
                        .header("X-User-Id", String.valueOf(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
        return mvcResult.getResponse();
    }

    private void seedAlwaysOpenSchedule(String symbol, String marketCode) {
        tradingScheduleSnapshotRepository.saveForTest(new TradingScheduleSnapshot(
                symbol,
                marketCode,
                alwaysOpenSessions(),
                List.of(),
                List.of(),
                OffsetDateTime.now()
        ));
    }

    private void seedHolidayClosedSchedule(String symbol, String marketCode) {
        tradingScheduleSnapshotRepository.saveForTest(new TradingScheduleSnapshot(
                symbol,
                marketCode,
                alwaysOpenSessions(),
                List.of(),
                List.of(new TradingHolidayRule(
                        marketCode,
                        OffsetDateTime.now(ZoneOffset.UTC).toLocalDate(),
                        1,
                        null,
                        null,
                        "UTC",
                        "Integration Holiday",
                        "INT"
                )),
                OffsetDateTime.now()
        ));
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
}
