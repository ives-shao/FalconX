package com.falconx.trading.calculator;

import java.math.BigDecimal;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class RiskExposureCalculatorTests {

    private final RiskExposureCalculator calculator = new RiskExposureCalculator();

    @Test
    void shouldCalculateNetExposureUsdFromLongShortAggregation() {
        BigDecimal netExposureUsd = calculator.calculateNetExposureUsd(
                new BigDecimal("3.00000000"),
                new BigDecimal("1.00000000"),
                new BigDecimal("9995.00000000")
        );

        Assertions.assertEquals(new BigDecimal("19990.00000000"), netExposureUsd);
    }

    @Test
    void shouldCalculateNegativeNetExposureUsdWhenShortSideDominates() {
        BigDecimal netExposureUsd = calculator.calculateNetExposureUsd(
                new BigDecimal("1.50000000"),
                new BigDecimal("2.00000000"),
                new BigDecimal("2500.00000000")
        );

        Assertions.assertEquals(new BigDecimal("-1250.00000000"), netExposureUsd);
    }

    @Test
    void shouldReturnZeroNetExposureUsdWhenLongAndShortOffsetEachOther() {
        BigDecimal netExposureUsd = calculator.calculateNetExposureUsd(
                new BigDecimal("2.00000000"),
                new BigDecimal("2.00000000"),
                new BigDecimal("8888.00000000")
        );

        Assertions.assertEquals(new BigDecimal("0E-8"), netExposureUsd);
    }
}
