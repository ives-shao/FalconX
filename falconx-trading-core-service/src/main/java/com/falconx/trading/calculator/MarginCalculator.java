package com.falconx.trading.calculator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/**
 * 保证金与手续费计算器。
 *
 * <p>该组件负责承载最小、可测试的数值计算逻辑，
 * 避免把精度处理和公式散落在应用服务中。
 */
@Component
public class MarginCalculator {

    /**
     * 计算开仓所需初始保证金。
     *
     * @param fillPrice 成交价
     * @param quantity 数量
     * @param leverage 杠杆倍数
     * @return 所需保证金
     */
    public BigDecimal calculateInitialMargin(BigDecimal fillPrice, BigDecimal quantity, BigDecimal leverage) {
        return fillPrice.multiply(quantity)
                .divide(leverage, 8, RoundingMode.DOWN);
    }

    /**
     * 计算手续费。
     *
     * @param fillPrice 成交价
     * @param quantity 数量
     * @param feeRate 手续费率
     * @return 手续费金额
     */
    public BigDecimal calculateFee(BigDecimal fillPrice, BigDecimal quantity, BigDecimal feeRate) {
        return fillPrice.multiply(quantity)
                .multiply(feeRate)
                .setScale(8, RoundingMode.DOWN);
    }
}
