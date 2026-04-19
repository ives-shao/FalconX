package com.falconx.trading.application;

import com.falconx.trading.command.CloseTradingPositionCommand;
import com.falconx.trading.config.TradingCoreServiceProperties;
import com.falconx.trading.dto.PositionCloseResult;
import com.falconx.trading.engine.OpenPositionSnapshotStore;
import com.falconx.trading.entity.TradingAccount;
import com.falconx.trading.entity.TradingOutboxMessage;
import com.falconx.trading.entity.TradingOutboxStatus;
import com.falconx.trading.entity.TradingPosition;
import com.falconx.trading.entity.TradingPositionStatus;
import com.falconx.trading.entity.TradingQuoteSnapshot;
import com.falconx.trading.entity.TradingTrade;
import com.falconx.trading.entity.TradingTradeType;
import com.falconx.trading.error.TradingBusinessException;
import com.falconx.trading.error.TradingErrorCode;
import com.falconx.trading.repository.TradingOutboxRepository;
import com.falconx.trading.repository.TradingPositionRepository;
import com.falconx.trading.repository.TradingQuoteSnapshotRepository;
import com.falconx.trading.repository.TradingRiskExposureRepository;
import com.falconx.trading.repository.TradingTradeRepository;
import com.falconx.trading.service.TradingAccountService;
import com.falconx.trading.service.TradingScheduleService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 手动平仓应用服务。
 *
 * <p>本轮只实现 Stage 7 的最小手动平仓模板：
 *
 * <ol>
 *   <li>按 `positionId + userId` 锁定持仓</li>
 *   <li>不再受开仓交易时间窗口阻塞</li>
 *   <li>按 Redis 最新标记价结算已实现盈亏</li>
 *   <li>同事务更新账户、账本、持仓、成交和净敞口</li>
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
    private final TradingRiskExposureRepository tradingRiskExposureRepository;
    private final TradingOutboxRepository tradingOutboxRepository;
    private final TradingScheduleService tradingScheduleService;
    private final OpenPositionSnapshotStore openPositionSnapshotStore;

    public TradingPositionCloseApplicationService(TradingCoreServiceProperties properties,
                                                  TradingPositionRepository tradingPositionRepository,
                                                  TradingTradeRepository tradingTradeRepository,
                                                  TradingQuoteSnapshotRepository tradingQuoteSnapshotRepository,
                                                  TradingAccountService tradingAccountService,
                                                  TradingRiskExposureRepository tradingRiskExposureRepository,
                                                  TradingOutboxRepository tradingOutboxRepository,
                                                  TradingScheduleService tradingScheduleService,
                                                  OpenPositionSnapshotStore openPositionSnapshotStore) {
        this.properties = properties;
        this.tradingPositionRepository = tradingPositionRepository;
        this.tradingTradeRepository = tradingTradeRepository;
        this.tradingQuoteSnapshotRepository = tradingQuoteSnapshotRepository;
        this.tradingAccountService = tradingAccountService;
        this.tradingRiskExposureRepository = tradingRiskExposureRepository;
        this.tradingOutboxRepository = tradingOutboxRepository;
        this.tradingScheduleService = tradingScheduleService;
        this.openPositionSnapshotStore = openPositionSnapshotStore;
    }

    /**
     * 执行手动平仓。
     *
     * @param command 平仓命令
     * @return 平仓结果
     */
    @Transactional
    public PositionCloseResult closePosition(CloseTradingPositionCommand command) {
        OffsetDateTime now = OffsetDateTime.now();
        TradingPosition position = tradingPositionRepository.findByIdAndUserIdForUpdate(command.positionId(), command.userId())
                .orElseThrow(() -> new TradingBusinessException(TradingErrorCode.POSITION_NOT_FOUND));
        if (position.status() != TradingPositionStatus.OPEN) {
            throw new TradingBusinessException(TradingErrorCode.POSITION_ALREADY_CLOSED);
        }
        if (!tradingScheduleService.isCloseAllowed(position.symbol(), now)) {
            throw new IllegalStateException("Manual close unexpectedly blocked by trading schedule");
        }

        TradingQuoteSnapshot quote = tradingQuoteSnapshotRepository.findBySymbol(position.symbol())
                .orElseThrow(() -> new TradingBusinessException(TradingErrorCode.QUOTE_NOT_AVAILABLE));
        if (quote.stale()) {
            throw new TradingBusinessException(TradingErrorCode.PRICE_SOURCE_STALE_OR_DISCONNECTED);
        }
        if (quote.mark() == null) {
            throw new TradingBusinessException(TradingErrorCode.QUOTE_NOT_AVAILABLE);
        }

        BigDecimal realizedPnl = calculateRealizedPnl(position, quote.mark());
        TradingAccount settlementAccount = tradingAccountService.getExistingAccountForUpdate(
                command.userId(),
                properties.getSettlementToken()
        );
        TradingAccount account = tradingAccountService.settleClosedPosition(
                settlementAccount,
                position.margin(),
                realizedPnl,
                "position-close:" + position.positionId(),
                String.valueOf(position.positionId()),
                now
        );
        TradingPosition closedPosition = tradingPositionRepository.save(position.closeManually(quote.mark(), realizedPnl, now));
        TradingTrade trade = tradingTradeRepository.save(new TradingTrade(
                null,
                position.openingOrderId(),
                position.positionId(),
                position.userId(),
                position.symbol(),
                position.side(),
                TradingTradeType.CLOSE,
                position.quantity(),
                quote.mark(),
                BigDecimal.ZERO.setScale(8),
                realizedPnl,
                now
        ));
        tradingRiskExposureRepository.applyClosePosition(
                position.symbol(),
                position.side(),
                position.quantity(),
                now
        );
        tradingOutboxRepository.save(new TradingOutboxMessage(
                null,
                "position-closed:" + position.positionId(),
                "trading.position.closed",
                String.valueOf(position.userId()),
                Map.of(
                        "positionId", closedPosition.positionId(),
                        "userId", closedPosition.userId(),
                        "symbol", closedPosition.symbol(),
                        "side", closedPosition.side().name(),
                        "tradeId", trade.tradeId(),
                        "closePrice", closedPosition.closePrice(),
                        "closeReason", closedPosition.closeReason().name(),
                        "realizedPnl", closedPosition.realizedPnl(),
                        "closedAt", closedPosition.closedAt()
                ),
                TradingOutboxStatus.PENDING,
                now,
                null,
                0,
                now,
                null
        ));

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                openPositionSnapshotStore.remove(closedPosition.symbol(), closedPosition.positionId());
            }
        });

        log.info("trading.position.close.completed userId={} positionId={} symbol={} closePrice={} realizedPnl={} quoteSource={} quoteTs={}",
                command.userId(),
                position.positionId(),
                position.symbol(),
                quote.mark(),
                realizedPnl,
                quote.source(),
                quote.ts());
        return new PositionCloseResult(closedPosition, trade, account);
    }

    private BigDecimal calculateRealizedPnl(TradingPosition position, BigDecimal markPrice) {
        BigDecimal delta = position.side() == com.falconx.trading.entity.TradingOrderSide.BUY
                ? markPrice.subtract(position.entryPrice())
                : position.entryPrice().subtract(markPrice);
        return delta.multiply(position.quantity()).setScale(8, RoundingMode.HALF_UP);
    }
}
