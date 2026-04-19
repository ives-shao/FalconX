package com.falconx.trading.engine;

import com.falconx.market.contract.event.MarketPriceTickEventPayload;
import com.falconx.trading.application.TradingPositionCloseApplicationService;
import com.falconx.trading.dto.PriceTickProcessingResult;
import com.falconx.trading.entity.TradingPosition;
import com.falconx.trading.entity.TradingPositionCloseReason;
import com.falconx.trading.entity.TradingQuoteSnapshot;
import com.falconx.trading.repository.TradingQuoteSnapshotRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 报价驱动引擎。
 *
 * <p>该引擎承接 `market-service` 推来的最新价格事件，并在保存 Redis 最新价后，
 * 只基于 `OpenPositionSnapshotStore` 判定 TP / SL / 强平，不按 tick 扫 MySQL。
 */
@Component
public class QuoteDrivenEngine {

    private static final Logger log = LoggerFactory.getLogger(QuoteDrivenEngine.class);

    private final TradingQuoteSnapshotRepository tradingQuoteSnapshotRepository;
    private final OpenPositionSnapshotStore openPositionSnapshotStore;
    private final PositionTriggerRuleEvaluator positionTriggerRuleEvaluator;
    private final TradingPositionCloseApplicationService tradingPositionCloseApplicationService;

    public QuoteDrivenEngine(TradingQuoteSnapshotRepository tradingQuoteSnapshotRepository,
                             OpenPositionSnapshotStore openPositionSnapshotStore,
                             PositionTriggerRuleEvaluator positionTriggerRuleEvaluator,
                             TradingPositionCloseApplicationService tradingPositionCloseApplicationService) {
        this.tradingQuoteSnapshotRepository = tradingQuoteSnapshotRepository;
        this.openPositionSnapshotStore = openPositionSnapshotStore;
        this.positionTriggerRuleEvaluator = positionTriggerRuleEvaluator;
        this.tradingPositionCloseApplicationService = tradingPositionCloseApplicationService;
    }

    /**
     * 处理一条价格 tick。
     *
     * @param payload 市场价格事件
     * @return 处理结果
     */
    public PriceTickProcessingResult processTick(MarketPriceTickEventPayload payload) {
        log.info("trading.quote.tick.received symbol={} ts={} stale={}",
                payload.symbol(),
                payload.ts(),
                payload.stale());
        tradingQuoteSnapshotRepository.save(new TradingQuoteSnapshot(
                payload.symbol(),
                payload.bid(),
                payload.ask(),
                payload.mark(),
                payload.ts(),
                payload.source(),
                payload.stale()
        ));
        TradingQuoteSnapshot snapshot = tradingQuoteSnapshotRepository.findBySymbol(payload.symbol()).orElseThrow();
        if (snapshot.stale()) {
            log.warn("trading.quote.tick.stale symbol={} source={} ts={} action=snapshot-only",
                    snapshot.symbol(),
                    snapshot.source(),
                    snapshot.ts());
            return new PriceTickProcessingResult(snapshot, 0);
        }

        List<TradingPosition> openPositions = openPositionSnapshotStore.listOpenBySymbol(snapshot.symbol());
        int triggeredActions = 0;
        RuntimeException firstFailure = null;
        for (TradingPosition position : openPositions) {
            TradingPositionCloseReason closeReason = positionTriggerRuleEvaluator.evaluate(position, snapshot.mark());
            if (closeReason == null) {
                continue;
            }
            try {
                if (tradingPositionCloseApplicationService.closePositionByTrigger(position.positionId(), closeReason, snapshot) != null) {
                    triggeredActions++;
                }
            } catch (RuntimeException exception) {
                log.error("trading.quote.tick.position-close.failed symbol={} positionId={} requestedReason={} markPrice={} message={}",
                        snapshot.symbol(),
                        position.positionId(),
                        closeReason,
                        snapshot.mark(),
                        exception.getMessage(),
                        exception);
                if (firstFailure == null) {
                    firstFailure = exception;
                }
            }
        }

        if (firstFailure != null) {
            log.warn("trading.quote.tick.completed.with-failures symbol={} source={} stale={} openPositions={} triggeredActions={} firstFailure={}",
                    snapshot.symbol(),
                    snapshot.source(),
                    snapshot.stale(),
                    openPositions.size(),
                    triggeredActions,
                    firstFailure.getMessage());
            throw firstFailure;
        }

        log.info("trading.quote.tick.applied symbol={} source={} stale={} openPositions={} triggeredActions={}",
                snapshot.symbol(),
                snapshot.source(),
                snapshot.stale(),
                openPositions.size(),
                triggeredActions);
        return new PriceTickProcessingResult(snapshot, triggeredActions);
    }
}
