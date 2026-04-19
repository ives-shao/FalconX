package com.falconx.trading.service.impl;

import com.falconx.trading.calculator.RiskExposureCalculator;
import com.falconx.trading.entity.TradingHedgeLog;
import com.falconx.trading.entity.TradingHedgeLogStatus;
import com.falconx.trading.entity.TradingRiskExposure;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * B-book 风险观测判断器。
 *
 * <p>该组件只负责纯业务判断，不关心日志、仓储和事件发布，
 * 便于用单元测试覆盖净 USD 敞口换算与阈值状态机。
 */
@Component
class TradingRiskObservabilityDecider {

    private final RiskExposureCalculator riskExposureCalculator;

    TradingRiskObservabilityDecider(RiskExposureCalculator riskExposureCalculator) {
        this.riskExposureCalculator = riskExposureCalculator;
    }

    TradingRiskObservabilityDecision evaluate(TradingRiskExposure exposure,
                                              BigDecimal hedgeThresholdUsd,
                                              BigDecimal markPrice,
                                              TradingHedgeLog latestLog) {
        BigDecimal netExposureUsd = riskExposureCalculator.calculateNetExposureUsd(
                exposure.totalLongQty(),
                exposure.totalShortQty(),
                markPrice
        );
        if (hedgeThresholdUsd == null || hedgeThresholdUsd.signum() <= 0) {
            return new TradingRiskObservabilityDecision(null, netExposureUsd, false);
        }

        boolean activeAlert = latestLog != null && latestLog.actionStatus() == TradingHedgeLogStatus.ALERT_ONLY;
        boolean breached = exposure.netExposure().signum() != 0
                && netExposureUsd.abs().compareTo(hedgeThresholdUsd) >= 0;
        if (breached) {
            boolean directionChanged = latestLog != null
                    && latestLog.netExposureUsd() != null
                    && latestLog.netExposureUsd().signum() != netExposureUsd.signum();
            if (!activeAlert || directionChanged) {
                return new TradingRiskObservabilityDecision(TradingHedgeLogStatus.ALERT_ONLY, netExposureUsd, true);
            }
            return new TradingRiskObservabilityDecision(null, netExposureUsd, false);
        }

        if (activeAlert) {
            return new TradingRiskObservabilityDecision(TradingHedgeLogStatus.RECOVERED, netExposureUsd, false);
        }
        return new TradingRiskObservabilityDecision(null, netExposureUsd, false);
    }
}
