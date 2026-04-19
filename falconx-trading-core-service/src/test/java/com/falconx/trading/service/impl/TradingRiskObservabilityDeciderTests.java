package com.falconx.trading.service.impl;

import com.falconx.trading.calculator.RiskExposureCalculator;
import com.falconx.trading.entity.TradingHedgeLog;
import com.falconx.trading.entity.TradingHedgeLogStatus;
import com.falconx.trading.entity.TradingHedgeTriggerSource;
import com.falconx.trading.entity.TradingRiskExposure;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TradingRiskObservabilityDeciderTests {

    private final TradingRiskObservabilityDecider decider = new TradingRiskObservabilityDecider(new RiskExposureCalculator());

    @Test
    void shouldNotTriggerWhenThresholdIsMissingOrDisabled() {
        TradingRiskObservabilityDecision missingThreshold = decider.evaluate(
                exposure("BTCUSDT", "2.00000000", "0.00000000"),
                null,
                new BigDecimal("9995.00000000"),
                null
        );
        TradingRiskObservabilityDecision disabledThreshold = decider.evaluate(
                exposure("BTCUSDT", "2.00000000", "0.00000000"),
                BigDecimal.ZERO,
                new BigDecimal("9995.00000000"),
                null
        );

        Assertions.assertFalse(missingThreshold.shouldWriteHedgeLog());
        Assertions.assertEquals(new BigDecimal("19990.00000000"), missingThreshold.netExposureUsd());
        Assertions.assertFalse(disabledThreshold.shouldWriteHedgeLog());
    }

    @Test
    void shouldCreateAlertWhenExposureFirstBreachesThreshold() {
        TradingRiskObservabilityDecision decision = decider.evaluate(
                exposure("BTCUSDT", "2.00000000", "0.00000000"),
                new BigDecimal("15000.00000000"),
                new BigDecimal("9995.00000000"),
                null
        );

        Assertions.assertEquals(TradingHedgeLogStatus.ALERT_ONLY, decision.actionStatus());
        Assertions.assertEquals(new BigDecimal("19990.00000000"), decision.netExposureUsd());
        Assertions.assertTrue(decision.publishAlertEvent());
    }

    @Test
    void shouldDeduplicateWhileExposureStaysAboveThresholdInSameDirection() {
        TradingRiskObservabilityDecision decision = decider.evaluate(
                exposure("BTCUSDT", "2.00000000", "0.00000000"),
                new BigDecimal("15000.00000000"),
                new BigDecimal("9995.00000000"),
                latestLog(TradingHedgeLogStatus.ALERT_ONLY, "19990.00000000")
        );

        Assertions.assertFalse(decision.shouldWriteHedgeLog());
        Assertions.assertFalse(decision.publishAlertEvent());
        Assertions.assertEquals(new BigDecimal("19990.00000000"), decision.netExposureUsd());
    }

    @Test
    void shouldCreateRecoveredWhenExposureFallsBackWithinThreshold() {
        TradingRiskObservabilityDecision decision = decider.evaluate(
                exposure("BTCUSDT", "2.00000000", "0.00000000"),
                new BigDecimal("15000.00000000"),
                new BigDecimal("6995.00000000"),
                latestLog(TradingHedgeLogStatus.ALERT_ONLY, "19990.00000000")
        );

        Assertions.assertEquals(TradingHedgeLogStatus.RECOVERED, decision.actionStatus());
        Assertions.assertEquals(new BigDecimal("13990.00000000"), decision.netExposureUsd());
        Assertions.assertFalse(decision.publishAlertEvent());
    }

    @Test
    void shouldCreateSecondAlertWhenDirectionChangesAcrossThreshold() {
        TradingRiskObservabilityDecision decision = decider.evaluate(
                exposure("BTCUSDT", "0.00000000", "2.00000000"),
                new BigDecimal("15000.00000000"),
                new BigDecimal("9995.00000000"),
                latestLog(TradingHedgeLogStatus.ALERT_ONLY, "19990.00000000")
        );

        Assertions.assertEquals(TradingHedgeLogStatus.ALERT_ONLY, decision.actionStatus());
        Assertions.assertEquals(new BigDecimal("-19990.00000000"), decision.netExposureUsd());
        Assertions.assertTrue(decision.publishAlertEvent());
    }

    private TradingRiskExposure exposure(String symbol, String totalLongQty, String totalShortQty) {
        BigDecimal longQty = new BigDecimal(totalLongQty);
        BigDecimal shortQty = new BigDecimal(totalShortQty);
        return new TradingRiskExposure(
                symbol,
                longQty,
                shortQty,
                longQty.subtract(shortQty),
                BigDecimal.ZERO,
                OffsetDateTime.now()
        );
    }

    private TradingHedgeLog latestLog(TradingHedgeLogStatus actionStatus, String netExposureUsd) {
        return new TradingHedgeLog(
                1L,
                "BTCUSDT",
                1001L,
                TradingHedgeTriggerSource.OPEN_POSITION,
                actionStatus,
                new BigDecimal("2.00000000"),
                new BigDecimal(netExposureUsd),
                new BigDecimal("15000.00000000"),
                new BigDecimal("9995.00000000"),
                OffsetDateTime.now(),
                "risk-observability-unit-test",
                OffsetDateTime.now()
        );
    }
}
