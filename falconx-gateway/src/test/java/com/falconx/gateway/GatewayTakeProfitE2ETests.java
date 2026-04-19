package com.falconx.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.falconx.gateway.support.E2ECleanupDatabases;
import com.falconx.market.contract.event.MarketPriceTickEventPayload;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * `TC-E2E-010` 代表性 E2E：注册 -> 入金 -> 激活 -> 登录 -> 下单 -> TP 自动平仓。
 */
@SpringBootTest(classes = GatewayApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@E2ECleanupDatabases
class GatewayTakeProfitE2ETests extends GatewayTradingRiskE2ETestSupport {

    private static final String IDENTITY_DB_NAME = "fx_id_gw_tp_" + shortRandomSuffix(16);
    private static final String TRADING_DB_NAME = "fx_tr_gw_tp_" + shortRandomSuffix(16);
    private static final StartedServiceHolder IDENTITY_SERVICE = newIdentityServiceHolder(
            IDENTITY_DB_NAME,
            "gateway-tp-identity-" + randomSuffix()
    );
    private static final StartedServiceHolder TRADING_SERVICE = newTradingServiceHolder(
            TRADING_DB_NAME,
            "gateway-tp-trading-" + randomSuffix()
    );

    @DynamicPropertySource
    static void registerGatewayProperties(DynamicPropertyRegistry registry) {
        registerGatewayRouteProperties(registry, IDENTITY_SERVICE, TRADING_SERVICE);
    }

    @AfterAll
    static void stopServices() {
        stopStartedServices(TRADING_SERVICE, IDENTITY_SERVICE);
    }

    @Test
    void shouldAutoClosePositionByTakeProfitThroughGatewayAndKafka() throws Exception {
        AuthenticatedGatewayUser user = registerDepositActivateAndLogin(
                IDENTITY_SERVICE,
                TRADING_SERVICE,
                new BigDecimal("2000.00000000")
        );
        preheatTradingForMarketOrder(TRADING_SERVICE);

        long positionId = placeMarketOrderThroughGateway(
                user.accessToken(),
                "gw-tp-order-" + shortRandomSuffix(12),
                new BigDecimal("10100.0"),
                new BigDecimal("9800.0")
        );

        JsonNode accountAfterOrder = waitForGatewayAccountSnapshot(
                user.accessToken(),
                snapshot -> snapshot.path("data").path("openPositions").isArray()
                        && snapshot.path("data").path("openPositions").size() == 1,
                "下单后 gateway 账户视图未出现 OPEN 持仓"
        );
        BigDecimal balanceAfterOrder = new BigDecimal(accountAfterOrder.path("data").path("balance").asText());

        sendMarketPriceTick(TRADING_SERVICE, "evt-gw-tp-" + shortRandomSuffix(20), randomSuffix(), new MarketPriceTickEventPayload(
                "BTCUSDT",
                new BigDecimal("10095.00000000"),
                new BigDecimal("10105.00000000"),
                new BigDecimal("10100.00000000"),
                new BigDecimal("10100.00000000"),
                OffsetDateTime.now(),
                "gateway-stage7-tp",
                false
        ));

        waitForPositionStatus(TRADING_SERVICE, positionId, 2);
        JsonNode accountAfterTp = waitForGatewayAccountSnapshot(
                user.accessToken(),
                snapshot -> snapshot.path("data").path("openPositions").isArray()
                        && snapshot.path("data").path("openPositions").size() == 0
                        && new BigDecimal(snapshot.path("data").path("marginUsed").asText())
                        .compareTo(BigDecimal.ZERO) == 0,
                "TP 自动平仓后 gateway 账户视图未收敛到空持仓"
        );

        BigDecimal finalBalance = new BigDecimal(accountAfterTp.path("data").path("balance").asText());
        Assertions.assertTrue(finalBalance.compareTo(balanceAfterOrder) > 0);

        Assertions.assertEquals(1L, countRows(
                TRADING_SERVICE.getBean(javax.sql.DataSource.class),
                "SELECT COUNT(1) FROM t_position WHERE id = ? AND status = 2 AND close_reason = 2",
                positionId
        ));
        Assertions.assertEquals(1L, countRows(
                TRADING_SERVICE.getBean(javax.sql.DataSource.class),
                "SELECT COUNT(1) FROM t_trade WHERE position_id = ? AND trade_type = 2",
                positionId
        ));
        Assertions.assertEquals(1L, countRows(
                TRADING_SERVICE.getBean(javax.sql.DataSource.class),
                "SELECT COUNT(1) FROM t_ledger WHERE user_id = ? AND biz_type = 8",
                user.userId()
        ));
        Assertions.assertEquals(0, decimalValue(
                TRADING_SERVICE.getBean(javax.sql.DataSource.class),
                "SELECT net_exposure FROM t_risk_exposure WHERE symbol = ?",
                "BTCUSDT"
        ).compareTo(BigDecimal.ZERO));
        Assertions.assertEquals(1L, countRows(
                TRADING_SERVICE.getBean(javax.sql.DataSource.class),
                "SELECT COUNT(1) FROM t_outbox WHERE event_type = ?",
                "trading.position.closed"
        ));
    }
}
