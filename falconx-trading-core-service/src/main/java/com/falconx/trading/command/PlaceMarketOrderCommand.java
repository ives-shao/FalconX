package com.falconx.trading.command;

import com.falconx.trading.entity.TradingOrderSide;
import java.math.BigDecimal;

/**
 * 市价单下单命令。
 *
 * <p>当前阶段该命令先只覆盖最小开仓输入：
 * 用户、品种、方向、数量、杠杆、持仓级 TP/SL 和 `clientOrderId`。
 */
public record PlaceMarketOrderCommand(
        Long userId,
        String symbol,
        TradingOrderSide side,
        BigDecimal quantity,
        BigDecimal leverage,
        BigDecimal takeProfitPrice,
        BigDecimal stopLossPrice,
        String clientOrderId
) {
}
