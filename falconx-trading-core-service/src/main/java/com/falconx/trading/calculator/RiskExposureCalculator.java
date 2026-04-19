package com.falconx.trading.calculator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/**
 * 净敞口估值计算器。
 *
 * <p>该组件把 B-book 风险观测里会反复出现的数量聚合与美元口径换算收口成可单测逻辑，
 * 避免把 `(多头 - 空头) * mark_price` 公式散落在服务实现和测试里。
 */
@Component
public class RiskExposureCalculator {

    /**
     * 计算数量口径净敞口。
     *
     * @param totalLongQty 多头总量
     * @param totalShortQty 空头总量
     * @return 净敞口 = 多头 - 空头
     */
    public BigDecimal calculateNetExposure(BigDecimal totalLongQty, BigDecimal totalShortQty) {
        return totalLongQty.subtract(totalShortQty).setScale(8, RoundingMode.DOWN);
    }

    /**
     * 按聚合后数量口径和最新标记价换算美元敞口。
     *
     * @param totalLongQty 多头总量
     * @param totalShortQty 空头总量
     * @param markPrice 最新标记价
     * @return 净美元敞口
     */
    public BigDecimal calculateNetExposureUsd(BigDecimal totalLongQty,
                                              BigDecimal totalShortQty,
                                              BigDecimal markPrice) {
        return calculateNetExposureUsd(calculateNetExposure(totalLongQty, totalShortQty), markPrice);
    }

    /**
     * 按已有净敞口和最新标记价换算美元敞口。
     *
     * @param netExposure 净敞口
     * @param markPrice 最新标记价
     * @return 净美元敞口
     */
    public BigDecimal calculateNetExposureUsd(BigDecimal netExposure, BigDecimal markPrice) {
        return netExposure.multiply(markPrice).setScale(8, RoundingMode.DOWN);
    }
}
