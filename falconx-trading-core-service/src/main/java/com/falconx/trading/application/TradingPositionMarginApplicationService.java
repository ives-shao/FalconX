package com.falconx.trading.application;

import com.falconx.trading.calculator.LiquidationPriceCalculator;
import com.falconx.trading.command.AddIsolatedMarginCommand;
import com.falconx.trading.config.TradingCoreServiceProperties;
import com.falconx.trading.dto.AddIsolatedMarginResult;
import com.falconx.trading.engine.OpenPositionSnapshotStore;
import com.falconx.trading.entity.TradingAccount;
import com.falconx.trading.entity.TradingPosition;
import com.falconx.trading.error.TradingBusinessException;
import com.falconx.trading.error.TradingErrorCode;
import com.falconx.trading.repository.TradingPositionRepository;
import com.falconx.trading.service.TradingAccountService;
import com.falconx.trading.support.TradingPricingSupport;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 逐仓保证金追加应用服务。
 */
@Service
public class TradingPositionMarginApplicationService {

    private static final Logger log = LoggerFactory.getLogger(TradingPositionMarginApplicationService.class);

    private final TradingPositionRepository tradingPositionRepository;
    private final TradingAccountService tradingAccountService;
    private final LiquidationPriceCalculator liquidationPriceCalculator;
    private final TradingCoreServiceProperties properties;
    private final OpenPositionSnapshotStore openPositionSnapshotStore;

    public TradingPositionMarginApplicationService(TradingPositionRepository tradingPositionRepository,
                                                   TradingAccountService tradingAccountService,
                                                   LiquidationPriceCalculator liquidationPriceCalculator,
                                                   TradingCoreServiceProperties properties,
                                                   OpenPositionSnapshotStore openPositionSnapshotStore) {
        this.tradingPositionRepository = tradingPositionRepository;
        this.tradingAccountService = tradingAccountService;
        this.liquidationPriceCalculator = liquidationPriceCalculator;
        this.properties = properties;
        this.openPositionSnapshotStore = openPositionSnapshotStore;
    }

    /**
     * 为 OPEN 持仓追加逐仓保证金，并重算强平价。
     */
    @Transactional
    public AddIsolatedMarginResult addIsolatedMargin(AddIsolatedMarginCommand command) {
        BigDecimal amount = TradingPricingSupport.scaleAmount(command.amount());
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("additional isolated margin amount must be positive");
        }
        log.info("trading.position.margin.request userId={} positionId={} amount={}",
                command.userId(),
                command.positionId(),
                amount);

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

        TradingAccount account = tradingAccountService.getExistingAccountForUpdate(command.userId(), properties.getSettlementToken());
        if (account.available().compareTo(amount) < 0) {
            throw new TradingBusinessException(
                    TradingErrorCode.INSUFFICIENT_MARGIN,
                    Map.of(
                            "userId", command.userId(),
                            "positionId", command.positionId(),
                            "symbol", position.symbol(),
                            "rejectionReason", TradingErrorCode.INSUFFICIENT_MARGIN.name()
                    )
            );
        }

        OffsetDateTime occurredAt = OffsetDateTime.now();
        BigDecimal nextMargin = position.margin().add(amount);
        BigDecimal nextLiquidationPrice = liquidationPriceCalculator.calculate(
                position.side(),
                position.entryPrice(),
                position.quantity(),
                nextMargin,
                properties.getMaintenanceMarginRate()
        );
        TradingAccount updatedAccount = tradingAccountService.supplementIsolatedMargin(
                account,
                amount,
                "ims:" + position.positionId() + ":" + UUID.randomUUID(),
                "isolated-margin-supplement:" + position.positionId(),
                occurredAt
        );
        TradingPosition updatedPosition = tradingPositionRepository.save(position.supplementMargin(
                amount,
                nextLiquidationPrice,
                occurredAt
        ));
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                openPositionSnapshotStore.upsert(updatedPosition);
            }
        });

        log.info("trading.position.margin.supplemented userId={} positionId={} amount={} margin={} liquidationPrice={}",
                command.userId(),
                updatedPosition.positionId(),
                amount,
                updatedPosition.margin(),
                updatedPosition.liquidationPrice());
        return new AddIsolatedMarginResult(updatedPosition, updatedAccount);
    }
}
