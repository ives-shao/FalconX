package com.falconx.trading.support;

import com.falconx.trading.entity.TradingOrderSide;
import com.falconx.trading.entity.TradingPosition;
import com.falconx.trading.entity.TradingQuoteSnapshot;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 交易价格与金额口径辅助工具。
 *
 * <p>当前阶段冻结两条高风险规则：
 *
 * <ul>
 *   <li>金额、盈亏、保证金统一保留 8 位小数并按截断处理</li>
 *   <li>逐仓估值与强平相关的有效标记价按持仓方向取 `bid / ask`</li>
 * </ul>
 */
public final class TradingPricingSupport {

    private static final int AMOUNT_SCALE = 8;

    private TradingPricingSupport() {
    }

    /**
     * 统一金额截断口径。
     */
    public static BigDecimal scaleAmount(BigDecimal value) {
        return value == null ? null : value.setScale(AMOUNT_SCALE, RoundingMode.DOWN);
    }

    /**
     * 解析持仓视角下的有效标记价。
     *
     * <p>`BUY -> bid`，`SELL -> ask`。
     */
    public static BigDecimal resolvePositionMarkPrice(TradingQuoteSnapshot quote, TradingOrderSide side) {
        if (quote == null || side == null) {
            return null;
        }
        BigDecimal price = side == TradingOrderSide.BUY ? quote.bid() : quote.ask();
        return scaleAmount(price);
    }

    /**
     * 解析市价单在下单时的参考成交价。
     *
     * <p>`BUY -> ask`，`SELL -> bid`。
     */
    public static BigDecimal resolveOrderReferencePrice(TradingQuoteSnapshot quote, TradingOrderSide side) {
        if (quote == null || side == null) {
            return null;
        }
        BigDecimal price = side == TradingOrderSide.BUY ? quote.ask() : quote.bid();
        return scaleAmount(price);
    }

    /**
     * 按平台净敞口方向解析风险观测使用的估值价格。
     *
     * <p>`净多头 -> bid`，`净空头 -> ask`，零敞口保持 `bid` 以维持确定性。
     */
    public static BigDecimal resolveExposureMarkPrice(TradingQuoteSnapshot quote, BigDecimal netExposure) {
        if (quote == null) {
            return null;
        }
        BigDecimal price = netExposure != null && netExposure.signum() < 0 ? quote.ask() : quote.bid();
        return scaleAmount(price);
    }

    /**
     * 统一持仓浮盈亏 / 已实现盈亏计算口径。
     */
    public static BigDecimal calculatePositionPnl(TradingPosition position, BigDecimal effectiveMarkPrice) {
        if (position == null || effectiveMarkPrice == null) {
            return null;
        }
        BigDecimal delta = position.side() == TradingOrderSide.BUY
                ? effectiveMarkPrice.subtract(position.entryPrice())
                : position.entryPrice().subtract(effectiveMarkPrice);
        return scaleAmount(delta.multiply(position.quantity()));
    }
}
