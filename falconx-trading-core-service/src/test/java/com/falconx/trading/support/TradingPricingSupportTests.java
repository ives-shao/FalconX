package com.falconx.trading.support;

import com.falconx.trading.entity.TradingMarginMode;
import com.falconx.trading.entity.TradingOrderSide;
import com.falconx.trading.entity.TradingPosition;
import com.falconx.trading.entity.TradingPositionStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * `TC-TRD-040` 浮盈浮亏计算口径单元测试。
 */
class TradingPricingSupportTests {

    @ParameterizedTest
    @MethodSource("positionPnlCases")
    void shouldCalculatePositionPnlWithDirectionalEffectiveMarkPrice(TradingOrderSide side,
                                                                     String entryPrice,
                                                                     String effectiveMarkPrice,
                                                                     String quantity,
                                                                     String expectedPnl) {
        TradingPosition position = new TradingPosition(
                1L,
                1L,
                1L,
                "BTCUSDT",
                side,
                new BigDecimal(quantity),
                new BigDecimal(entryPrice),
                new BigDecimal("10.00000000"),
                new BigDecimal("1000.00000000"),
                TradingMarginMode.ISOLATED,
                new BigDecimal("9000.00000000"),
                new BigDecimal("11000.00000000"),
                new BigDecimal("8000.00000000"),
                null,
                null,
                null,
                TradingPositionStatus.OPEN,
                OffsetDateTime.now(),
                null,
                OffsetDateTime.now()
        );

        BigDecimal actual = TradingPricingSupport.calculatePositionPnl(
                position,
                new BigDecimal(effectiveMarkPrice)
        );

        Assertions.assertEquals(new BigDecimal(expectedPnl), actual);
    }

    private static Stream<Arguments> positionPnlCases() {
        return Stream.of(
                Arguments.of(TradingOrderSide.BUY, "10000.00000000", "10500.00000000", "1.00000000", "500.00000000"),
                Arguments.of(TradingOrderSide.BUY, "10000.00000000", "9500.00000000", "1.00000000", "-500.00000000"),
                Arguments.of(TradingOrderSide.SELL, "10000.00000000", "9500.00000000", "1.00000000", "500.00000000"),
                Arguments.of(TradingOrderSide.SELL, "10000.00000000", "10500.00000000", "1.00000000", "-500.00000000")
        );
    }
}
