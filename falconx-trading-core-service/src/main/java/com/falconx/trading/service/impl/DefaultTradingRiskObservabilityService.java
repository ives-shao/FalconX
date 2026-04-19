package com.falconx.trading.service.impl;

import com.falconx.trading.entity.TradingHedgeLog;
import com.falconx.trading.entity.TradingHedgeLogStatus;
import com.falconx.trading.entity.TradingHedgeTriggerSource;
import com.falconx.trading.entity.TradingOrderSide;
import com.falconx.trading.entity.TradingPositionCloseReason;
import com.falconx.trading.entity.TradingQuoteSnapshot;
import com.falconx.trading.entity.TradingRiskConfig;
import com.falconx.trading.entity.TradingRiskExposure;
import com.falconx.trading.event.TradingHedgeAlertEvent;
import com.falconx.trading.producer.TradingHedgeAlertEventPublisher;
import com.falconx.trading.repository.TradingHedgeLogRepository;
import com.falconx.trading.repository.TradingRiskConfigRepository;
import com.falconx.trading.repository.TradingRiskExposureRepository;
import com.falconx.trading.service.TradingRiskObservabilityService;
import com.falconx.trading.support.TradingPricingSupport;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * B-book 风险可观测性默认实现。
 *
 * <p>该实现把数量敞口、美元敞口、阈值判断和 `t_hedge_log` 追踪收敛到同一处，
 * 避免开仓、平仓和行情刷新三条链路各自维护一套分叉逻辑。
 */
@Service
public class DefaultTradingRiskObservabilityService implements TradingRiskObservabilityService {

    private static final Logger log = LoggerFactory.getLogger(DefaultTradingRiskObservabilityService.class);

    private final TradingRiskExposureRepository tradingRiskExposureRepository;
    private final TradingRiskConfigRepository tradingRiskConfigRepository;
    private final TradingHedgeLogRepository tradingHedgeLogRepository;
    private final TradingRiskObservabilityDecider tradingRiskObservabilityDecider;
    private final TradingHedgeAlertEventPublisher tradingHedgeAlertEventPublisher;

    public DefaultTradingRiskObservabilityService(TradingRiskExposureRepository tradingRiskExposureRepository,
                                                  TradingRiskConfigRepository tradingRiskConfigRepository,
                                                  TradingHedgeLogRepository tradingHedgeLogRepository,
                                                  TradingRiskObservabilityDecider tradingRiskObservabilityDecider,
                                                  TradingHedgeAlertEventPublisher tradingHedgeAlertEventPublisher) {
        this.tradingRiskExposureRepository = tradingRiskExposureRepository;
        this.tradingRiskConfigRepository = tradingRiskConfigRepository;
        this.tradingHedgeLogRepository = tradingHedgeLogRepository;
        this.tradingRiskObservabilityDecider = tradingRiskObservabilityDecider;
        this.tradingHedgeAlertEventPublisher = tradingHedgeAlertEventPublisher;
    }

    @Override
    @Transactional
    public void applyOpenPosition(String symbol,
                                  TradingOrderSide side,
                                  BigDecimal quantity,
                                  TradingQuoteSnapshot quote,
                                  OffsetDateTime occurredAt,
                                  Long positionId) {
        TradingQuoteSnapshot freshQuote = requireFreshQuote(quote, symbol);
        tradingRiskExposureRepository.applyOpenPosition(
                symbol,
                side,
                quantity,
                TradingPricingSupport.scaleAmount(freshQuote.bid()),
                TradingPricingSupport.scaleAmount(freshQuote.ask()),
                occurredAt
        );
        observeThreshold(symbol, freshQuote, occurredAt, TradingHedgeTriggerSource.OPEN_POSITION, positionId);
    }

    @Override
    @Transactional
    public void applyClosePosition(String symbol,
                                   TradingOrderSide side,
                                   BigDecimal quantity,
                                   TradingQuoteSnapshot quote,
                                   OffsetDateTime occurredAt,
                                   TradingPositionCloseReason closeReason,
                                   Long positionId) {
        TradingQuoteSnapshot freshQuote = requireFreshQuote(quote, symbol);
        tradingRiskExposureRepository.applyClosePosition(
                symbol,
                side,
                quantity,
                TradingPricingSupport.scaleAmount(freshQuote.bid()),
                TradingPricingSupport.scaleAmount(freshQuote.ask()),
                occurredAt
        );
        observeThreshold(symbol, freshQuote, occurredAt, resolveTriggerSource(closeReason), positionId);
    }

    @Override
    @Transactional
    public void refreshExposureFromQuote(TradingQuoteSnapshot quote, OffsetDateTime occurredAt) {
        TradingQuoteSnapshot freshQuote = requireFreshQuote(quote, quote == null ? null : quote.symbol());
        tradingRiskExposureRepository.refreshNetExposureUsd(
                freshQuote.symbol(),
                TradingPricingSupport.scaleAmount(freshQuote.bid()),
                TradingPricingSupport.scaleAmount(freshQuote.ask()),
                occurredAt
        );
        observeThreshold(freshQuote.symbol(), freshQuote, occurredAt, TradingHedgeTriggerSource.PRICE_TICK, null);
    }

    private void observeThreshold(String symbol,
                                  TradingQuoteSnapshot quote,
                                  OffsetDateTime occurredAt,
                                  TradingHedgeTriggerSource triggerSource,
                                  Long positionId) {
        TradingRiskConfig riskConfig = tradingRiskConfigRepository.findBySymbol(symbol).orElse(null);
        if (riskConfig == null || riskConfig.hedgeThresholdUsd() == null || riskConfig.hedgeThresholdUsd().signum() <= 0) {
            return;
        }
        TradingRiskExposure exposure = tradingRiskExposureRepository.findBySymbol(symbol).orElse(null);
        if (exposure == null) {
            return;
        }
        BigDecimal exposureMarkPrice = TradingPricingSupport.resolveExposureMarkPrice(quote, exposure.netExposure());
        if (exposureMarkPrice == null) {
            return;
        }
        TradingHedgeLog latestLog = tradingHedgeLogRepository.findLatestBySymbol(symbol).orElse(null);
        TradingRiskObservabilityDecision decision = tradingRiskObservabilityDecider.evaluate(
                exposure,
                riskConfig.hedgeThresholdUsd(),
                exposureMarkPrice,
                latestLog
        );
        if (!decision.shouldWriteHedgeLog()) {
            return;
        }

        TradingHedgeLog hedgeLog = tradingHedgeLogRepository.save(new TradingHedgeLog(
                null,
                symbol,
                positionId,
                triggerSource,
                decision.actionStatus(),
                exposure.netExposure(),
                decision.netExposureUsd(),
                riskConfig.hedgeThresholdUsd(),
                exposureMarkPrice,
                quote.ts(),
                quote.source(),
                occurredAt
        ));
        if (decision.actionStatus() == TradingHedgeLogStatus.ALERT_ONLY) {
            log.warn("trading.risk.hedge.alert symbol={} positionId={} triggerSource={} netExposure={} netExposureUsd={} hedgeThresholdUsd={} markPrice={} priceSource={} quoteTs={} hedgeLogId={}",
                    symbol,
                    positionId,
                    triggerSource,
                    exposure.netExposure(),
                    decision.netExposureUsd(),
                    riskConfig.hedgeThresholdUsd(),
                    exposureMarkPrice,
                    quote.source(),
                    quote.ts(),
                    hedgeLog.hedgeLogId());
            tradingHedgeAlertEventPublisher.publishAfterCommit(new TradingHedgeAlertEvent(
                    occurredAt,
                    symbol,
                    decision.netExposureUsd(),
                    riskConfig.hedgeThresholdUsd(),
                    positionId,
                    triggerSource,
                    exposureMarkPrice,
                    quote.ts(),
                    quote.source(),
                    hedgeLog.hedgeLogId()
            ));
            return;
        }

        if (decision.actionStatus() == TradingHedgeLogStatus.RECOVERED) {
            log.info("trading.risk.hedge.recovered symbol={} positionId={} triggerSource={} netExposure={} netExposureUsd={} hedgeThresholdUsd={} markPrice={} priceSource={} quoteTs={} hedgeLogId={}",
                    symbol,
                    positionId,
                    triggerSource,
                    exposure.netExposure(),
                    decision.netExposureUsd(),
                    riskConfig.hedgeThresholdUsd(),
                    exposureMarkPrice,
                    quote.source(),
                    quote.ts(),
                    hedgeLog.hedgeLogId());
        }
    }

    private TradingQuoteSnapshot requireFreshQuote(TradingQuoteSnapshot quote, String symbol) {
        if (quote == null || quote.bid() == null || quote.ask() == null || quote.stale()) {
            throw new IllegalStateException("Risk observability requires a fresh mark price, symbol=" + symbol);
        }
        return quote;
    }

    private TradingHedgeTriggerSource resolveTriggerSource(TradingPositionCloseReason closeReason) {
        return switch (closeReason) {
            case MANUAL -> TradingHedgeTriggerSource.MANUAL_CLOSE;
            case TAKE_PROFIT -> TradingHedgeTriggerSource.TAKE_PROFIT;
            case STOP_LOSS -> TradingHedgeTriggerSource.STOP_LOSS;
            case LIQUIDATION -> TradingHedgeTriggerSource.LIQUIDATION;
        };
    }
}
