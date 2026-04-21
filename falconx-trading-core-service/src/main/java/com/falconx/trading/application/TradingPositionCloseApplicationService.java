package com.falconx.trading.application;

import com.falconx.trading.command.CloseTradingPositionCommand;
import com.falconx.trading.config.TradingCoreServiceProperties;
import com.falconx.trading.dto.PositionCloseResult;
import com.falconx.trading.engine.OpenPositionSnapshotStore;
import com.falconx.trading.engine.PositionTriggerRuleEvaluator;
import com.falconx.trading.entity.TradingAccount;
import com.falconx.trading.entity.TradingLedgerBizType;
import com.falconx.trading.entity.TradingLiquidationLog;
import com.falconx.trading.entity.TradingOutboxMessage;
import com.falconx.trading.entity.TradingOutboxStatus;
import com.falconx.trading.entity.TradingPosition;
import com.falconx.trading.entity.TradingPositionCloseReason;
import com.falconx.trading.entity.TradingPositionStatus;
import com.falconx.trading.entity.TradingQuoteSnapshot;
import com.falconx.trading.entity.TradingTrade;
import com.falconx.trading.entity.TradingTradeType;
import com.falconx.trading.error.TradingBusinessException;
import com.falconx.trading.error.TradingErrorCode;
import com.falconx.trading.repository.TradingLiquidationLogRepository;
import com.falconx.trading.repository.TradingOutboxRepository;
import com.falconx.trading.repository.TradingPositionRepository;
import com.falconx.trading.repository.TradingQuoteSnapshotRepository;
import com.falconx.trading.repository.TradingTradeRepository;
import com.falconx.trading.service.TradingAccountService;
import com.falconx.trading.service.TradingRiskObservabilityService;
import com.falconx.trading.service.TradingScheduleService;
import com.falconx.trading.support.TradingPricingSupport;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 持仓退出应用服务。
 *
 * <p>该服务收敛手动平仓、TP、SL 和强平的共享 owner 写路径：
 *
 * <ol>
 *   <li>锁定 OPEN 持仓与真实结算账户</li>
 *   <li>基于 Redis 最新标记价计算真实已实现盈亏</li>
 *   <li>同事务更新账户、账本、持仓、成交、净敞口与 outbox</li>
 *   <li>强平额外写 `t_liquidation_log` 并执行负净值保护</li>
 *   <li>事务提交后移除 OPEN 持仓快照</li>
 * </ol>
 */
@Service
public class TradingPositionCloseApplicationService {

    private static final Logger log = LoggerFactory.getLogger(TradingPositionCloseApplicationService.class);

    private final TradingCoreServiceProperties properties;
    private final TradingPositionRepository tradingPositionRepository;
    private final TradingTradeRepository tradingTradeRepository;
    private final TradingQuoteSnapshotRepository tradingQuoteSnapshotRepository;
    private final TradingAccountService tradingAccountService;
    private final TradingRiskObservabilityService tradingRiskObservabilityService;
    private final TradingOutboxRepository tradingOutboxRepository;
    private final TradingLiquidationLogRepository tradingLiquidationLogRepository;
    private final TradingScheduleService tradingScheduleService;
    private final OpenPositionSnapshotStore openPositionSnapshotStore;
    private final PositionTriggerRuleEvaluator positionTriggerRuleEvaluator;

    public TradingPositionCloseApplicationService(TradingCoreServiceProperties properties,
                                                  TradingPositionRepository tradingPositionRepository,
                                                  TradingTradeRepository tradingTradeRepository,
                                                  TradingQuoteSnapshotRepository tradingQuoteSnapshotRepository,
                                                  TradingAccountService tradingAccountService,
                                                  TradingRiskObservabilityService tradingRiskObservabilityService,
                                                  TradingOutboxRepository tradingOutboxRepository,
                                                  TradingLiquidationLogRepository tradingLiquidationLogRepository,
                                                  TradingScheduleService tradingScheduleService,
                                                  OpenPositionSnapshotStore openPositionSnapshotStore,
                                                  PositionTriggerRuleEvaluator positionTriggerRuleEvaluator) {
        this.properties = properties;
        this.tradingPositionRepository = tradingPositionRepository;
        this.tradingTradeRepository = tradingTradeRepository;
        this.tradingQuoteSnapshotRepository = tradingQuoteSnapshotRepository;
        this.tradingAccountService = tradingAccountService;
        this.tradingRiskObservabilityService = tradingRiskObservabilityService;
        this.tradingOutboxRepository = tradingOutboxRepository;
        this.tradingLiquidationLogRepository = tradingLiquidationLogRepository;
        this.tradingScheduleService = tradingScheduleService;
        this.openPositionSnapshotStore = openPositionSnapshotStore;
        this.positionTriggerRuleEvaluator = positionTriggerRuleEvaluator;
    }

    /**
     * 执行手动平仓。
     */
    @Transactional
    public PositionCloseResult closePosition(CloseTradingPositionCommand command) {
        OffsetDateTime now = OffsetDateTime.now();
        TradingPosition position = tradingPositionRepository.findByIdAndUserIdForUpdate(command.positionId(), command.userId())
                .orElseThrow(() -> new TradingBusinessException(
                        TradingErrorCode.POSITION_NOT_FOUND,
                        Map.of(
                                "userId", command.userId(),
                                "positionId", command.positionId(),
                                "rejectionReason", TradingErrorCode.POSITION_NOT_FOUND.name()
                        )
                ));
        if (position.isTerminal()) {
            throw new TradingBusinessException(
                    TradingErrorCode.POSITION_ALREADY_CLOSED,
                    Map.of(
                            "userId", command.userId(),
                            "positionId", command.positionId(),
                            "symbol", position.symbol(),
                            "rejectionReason", TradingErrorCode.POSITION_ALREADY_CLOSED.name()
                    )
            );
        }
        if (!tradingScheduleService.isCloseAllowed(position.symbol(), now)) {
            throw new IllegalStateException("Manual close unexpectedly blocked by trading schedule");
        }
        TradingQuoteSnapshot quote = requireQuoteForManualClose(position);
        return settlePositionExit(position, quote, TradingPositionCloseReason.MANUAL, now);
    }

    /**
     * 执行 TP/SL / 强平退出。
     *
     * <p>该入口只服务系统内部触发路径，不走 HTTP owner 校验。
     *
     * @param positionId 持仓 ID
     * @param closeReason 触发原因，仅允许 `TAKE_PROFIT / STOP_LOSS / LIQUIDATION`
     * @param quote 当前有效报价
     * @return 退出结果；若持仓已被其他并发链路关闭，返回 `null`
     */
    @Transactional
    public PositionCloseResult closePositionByTrigger(Long positionId,
                                                      TradingPositionCloseReason closeReason,
                                                      TradingQuoteSnapshot quote) {
        if (closeReason == null || closeReason == TradingPositionCloseReason.MANUAL) {
            throw new IllegalArgumentException("Triggered close requires TAKE_PROFIT / STOP_LOSS / LIQUIDATION");
        }
        TradingPosition position = tradingPositionRepository.findByIdForUpdate(positionId)
                .orElseThrow(() -> new IllegalStateException("Open position snapshot exists but DB record is missing, positionId=" + positionId));
        if (position.isTerminal()) {
            log.info("trading.position.trigger.skip positionId={} status={} reason={}",
                    positionId,
                    position.status(),
                    closeReason);
            return null;
        }
        if (quote == null || quote.mark() == null || quote.stale()) {
            throw new IllegalStateException("Triggered close requires a fresh mark price, positionId=" + positionId);
        }
        BigDecimal effectiveMarkPrice = TradingPricingSupport.resolvePositionMarkPrice(quote, position.side());
        if (effectiveMarkPrice == null) {
            throw new IllegalStateException("Triggered close requires executable bid/ask quote, positionId=" + positionId);
        }
        TradingPositionCloseReason effectiveCloseReason = positionTriggerRuleEvaluator.evaluate(position, effectiveMarkPrice);
        if (effectiveCloseReason == null) {
            log.info("trading.position.trigger.skip.revalidated positionId={} requestedReason={} reason=CONDITION_NOT_MET currentTakeProfitPrice={} currentStopLossPrice={} currentLiquidationPrice={} markPrice={}",
                    positionId,
                    closeReason,
                    position.takeProfitPrice(),
                    position.stopLossPrice(),
                    position.liquidationPrice(),
                    effectiveMarkPrice);
            return null;
        }
        if (effectiveCloseReason != closeReason) {
            log.info("trading.position.trigger.revalidated positionId={} requestedReason={} effectiveReason={} currentTakeProfitPrice={} currentStopLossPrice={} currentLiquidationPrice={} markPrice={}",
                    positionId,
                    closeReason,
                    effectiveCloseReason,
                    position.takeProfitPrice(),
                    position.stopLossPrice(),
                    position.liquidationPrice(),
                    effectiveMarkPrice);
        }
        return settlePositionExit(position, quote, effectiveCloseReason, OffsetDateTime.now());
    }

    private PositionCloseResult settlePositionExit(TradingPosition position,
                                                   TradingQuoteSnapshot quote,
                                                   TradingPositionCloseReason closeReason,
                                                   OffsetDateTime occurredAt) {
        BigDecimal effectiveMarkPrice = TradingPricingSupport.resolvePositionMarkPrice(quote, position.side());
        if (effectiveMarkPrice == null) {
            throw new TradingBusinessException(TradingErrorCode.QUOTE_NOT_AVAILABLE);
        }
        BigDecimal realizedPnl = calculateRealizedPnl(position, effectiveMarkPrice);
        TradingAccount settlementAccount = tradingAccountService.getExistingAccountForUpdate(
                position.userId(),
                properties.getSettlementToken()
        );

        boolean liquidation = closeReason == TradingPositionCloseReason.LIQUIDATION;
        TradingAccountService.PositionSettlementResult settlement = tradingAccountService.settlePositionExit(
                settlementAccount,
                position.margin(),
                realizedPnl,
                liquidation ? TradingLedgerBizType.LIQUIDATION_PNL : TradingLedgerBizType.REALIZED_PNL,
                liquidation,
                "position-exit:" + position.positionId() + ":" + closeReason.name(),
                String.valueOf(position.positionId()),
                occurredAt
        );

        TradingPositionStatus nextStatus = liquidation ? TradingPositionStatus.LIQUIDATED : TradingPositionStatus.CLOSED;
        TradingTradeType tradeType = liquidation ? TradingTradeType.LIQUIDATION : TradingTradeType.CLOSE;
        TradingPosition exitedPosition = tradingPositionRepository.save(position.close(
                nextStatus,
                closeReason,
                effectiveMarkPrice,
                realizedPnl,
                occurredAt
        ));
        TradingTrade trade = tradingTradeRepository.save(new TradingTrade(
                null,
                position.openingOrderId(),
                position.positionId(),
                position.userId(),
                position.symbol(),
                position.side(),
                tradeType,
                position.quantity(),
                effectiveMarkPrice,
                BigDecimal.ZERO.setScale(8),
                realizedPnl,
                occurredAt
        ));
        tradingRiskObservabilityService.applyClosePosition(
                position.symbol(),
                position.side(),
                position.quantity(),
                quote,
                occurredAt,
                closeReason,
                position.positionId()
        );

        TradingLiquidationLog liquidationLog = null;
        if (liquidation) {
            liquidationLog = tradingLiquidationLogRepository.save(new TradingLiquidationLog(
                    null,
                    position.userId(),
                    position.positionId(),
                    position.symbol(),
                    position.side(),
                    position.quantity(),
                    position.entryPrice(),
                    position.liquidationPrice(),
                    effectiveMarkPrice,
                    quote.ts(),
                    quote.source(),
                    realizedPnl.signum() < 0 ? realizedPnl.abs() : BigDecimal.ZERO.setScale(8),
                    BigDecimal.ZERO.setScale(8),
                    position.margin(),
                    settlement.platformCoveredLoss(),
                    occurredAt
            ));
        }

        tradingOutboxRepository.save(liquidation
                ? buildLiquidationOutbox(exitedPosition, trade, settlement, liquidationLog, occurredAt)
                : buildPositionClosedOutbox(exitedPosition, trade, occurredAt));

        registerSnapshotRemoval(exitedPosition);
        if (liquidation && liquidationLog != null) {
            log.warn("trading.liquidation.executed userId={} positionId={} liquidationLogId={} closePrice={} realizedPnl={} platformCoveredLoss={} quoteTs={} quoteSource={}",
                    position.userId(),
                    position.positionId(),
                    liquidationLog.liquidationLogId(),
                    effectiveMarkPrice,
                    realizedPnl,
                    settlement.platformCoveredLoss(),
                    quote.ts(),
                    quote.source());
        }
        log.info("trading.position.exit.completed userId={} positionId={} reason={} status={} closePrice={} realizedPnl={} appliedPnl={} platformCoveredLoss={} quoteSource={} quoteTs={}",
                position.userId(),
                position.positionId(),
                closeReason,
                exitedPosition.status(),
                effectiveMarkPrice,
                realizedPnl,
                settlement.appliedPnl(),
                settlement.platformCoveredLoss(),
                quote.source(),
                quote.ts());
        return new PositionCloseResult(exitedPosition, trade, settlement.account(), liquidationLog);
    }

    private TradingQuoteSnapshot requireQuoteForManualClose(TradingPosition position) {
        TradingQuoteSnapshot quote = tradingQuoteSnapshotRepository.findBySymbol(position.symbol())
                .orElseThrow(() -> new TradingBusinessException(
                        TradingErrorCode.QUOTE_NOT_AVAILABLE,
                        Map.of(
                                "userId", position.userId(),
                                "positionId", position.positionId(),
                                "symbol", position.symbol(),
                                "rejectionReason", TradingErrorCode.QUOTE_NOT_AVAILABLE.name()
                        )
                ));
        if (quote.stale()) {
            throw new TradingBusinessException(
                    TradingErrorCode.PRICE_SOURCE_STALE_OR_DISCONNECTED,
                    Map.of(
                            "userId", position.userId(),
                            "positionId", position.positionId(),
                            "symbol", position.symbol(),
                            "rejectionReason", TradingErrorCode.PRICE_SOURCE_STALE_OR_DISCONNECTED.name()
                    )
            );
        }
        if (quote.bid() == null || quote.ask() == null) {
            throw new TradingBusinessException(
                    TradingErrorCode.QUOTE_NOT_AVAILABLE,
                    Map.of(
                            "userId", position.userId(),
                            "positionId", position.positionId(),
                            "symbol", position.symbol(),
                            "rejectionReason", TradingErrorCode.QUOTE_NOT_AVAILABLE.name()
                    )
            );
        }
        return quote;
    }

    private TradingOutboxMessage buildPositionClosedOutbox(TradingPosition position,
                                                           TradingTrade trade,
                                                           OffsetDateTime occurredAt) {
        return new TradingOutboxMessage(
                null,
                "position-closed:" + position.positionId(),
                "trading.position.closed",
                String.valueOf(position.userId()),
                Map.of(
                        "positionId", position.positionId(),
                        "userId", position.userId(),
                        "symbol", position.symbol(),
                        "side", position.side().name(),
                        "tradeId", trade.tradeId(),
                        "closePrice", position.closePrice(),
                        "closeReason", position.closeReason().name(),
                        "realizedPnl", position.realizedPnl(),
                        "closedAt", position.closedAt()
                ),
                TradingOutboxStatus.PENDING,
                occurredAt,
                null,
                0,
                occurredAt,
                null
        );
    }

    private TradingOutboxMessage buildLiquidationOutbox(TradingPosition position,
                                                        TradingTrade trade,
                                                        TradingAccountService.PositionSettlementResult settlement,
                                                        TradingLiquidationLog liquidationLog,
                                                        OffsetDateTime occurredAt) {
        return new TradingOutboxMessage(
                null,
                "liquidation-executed:" + position.positionId(),
                "trading.liquidation.executed",
                String.valueOf(position.userId()),
                Map.ofEntries(
                        Map.entry("positionId", position.positionId()),
                        Map.entry("userId", position.userId()),
                        Map.entry("symbol", position.symbol()),
                        Map.entry("side", position.side().name()),
                        Map.entry("tradeId", trade.tradeId()),
                        Map.entry("liquidationLogId", liquidationLog.liquidationLogId()),
                        Map.entry("closePrice", position.closePrice()),
                        Map.entry("closeReason", position.closeReason().name()),
                        Map.entry("realizedPnl", position.realizedPnl()),
                        Map.entry("appliedPnl", settlement.appliedPnl()),
                        Map.entry("platformCoveredLoss", settlement.platformCoveredLoss()),
                        Map.entry("closedAt", position.closedAt())
                ),
                TradingOutboxStatus.PENDING,
                occurredAt,
                null,
                0,
                occurredAt,
                null
        );
    }

    private void registerSnapshotRemoval(TradingPosition position) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                openPositionSnapshotStore.remove(position.symbol(), position.positionId());
            }
        });
    }

    private BigDecimal calculateRealizedPnl(TradingPosition position, BigDecimal markPrice) {
        return TradingPricingSupport.calculatePositionPnl(position, markPrice);
    }
}
