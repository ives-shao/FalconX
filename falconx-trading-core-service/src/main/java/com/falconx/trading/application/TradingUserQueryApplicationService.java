package com.falconx.trading.application;

import com.falconx.trading.command.ListTradingLedgerEntriesCommand;
import com.falconx.trading.command.ListTradingLiquidationsCommand;
import com.falconx.trading.command.ListTradingOrdersCommand;
import com.falconx.trading.command.ListTradingPositionsCommand;
import com.falconx.trading.command.ListTradingTradesCommand;
import com.falconx.trading.dto.TradingLedgerItemResponse;
import com.falconx.trading.dto.TradingLedgerListResponse;
import com.falconx.trading.dto.TradingLiquidationItemResponse;
import com.falconx.trading.dto.TradingLiquidationListResponse;
import com.falconx.trading.dto.TradingOrderItemResponse;
import com.falconx.trading.dto.TradingOrderListResponse;
import com.falconx.trading.dto.TradingPositionItemResponse;
import com.falconx.trading.dto.TradingPositionListResponse;
import com.falconx.trading.dto.TradingTradeItemResponse;
import com.falconx.trading.dto.TradingTradeListResponse;
import com.falconx.trading.entity.TradingLedgerEntry;
import com.falconx.trading.entity.TradingLiquidationLog;
import com.falconx.trading.entity.TradingOrder;
import com.falconx.trading.entity.TradingPosition;
import com.falconx.trading.entity.TradingPositionStatus;
import com.falconx.trading.entity.TradingQuoteSnapshot;
import com.falconx.trading.entity.TradingTrade;
import com.falconx.trading.repository.TradingLedgerRepository;
import com.falconx.trading.repository.TradingLiquidationLogRepository;
import com.falconx.trading.repository.TradingOrderRepository;
import com.falconx.trading.repository.TradingPositionRepository;
import com.falconx.trading.repository.TradingQuoteSnapshotRepository;
import com.falconx.trading.repository.TradingTradeRepository;
import com.falconx.trading.support.TradingPricingSupport;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 用户视角查询应用服务。
 */
@Service
public class TradingUserQueryApplicationService {

    private final TradingOrderRepository tradingOrderRepository;
    private final TradingTradeRepository tradingTradeRepository;
    private final TradingPositionRepository tradingPositionRepository;
    private final TradingLedgerRepository tradingLedgerRepository;
    private final TradingLiquidationLogRepository tradingLiquidationLogRepository;
    private final TradingQuoteSnapshotRepository tradingQuoteSnapshotRepository;

    public TradingUserQueryApplicationService(TradingOrderRepository tradingOrderRepository,
                                              TradingTradeRepository tradingTradeRepository,
                                              TradingPositionRepository tradingPositionRepository,
                                              TradingLedgerRepository tradingLedgerRepository,
                                              TradingLiquidationLogRepository tradingLiquidationLogRepository,
                                              TradingQuoteSnapshotRepository tradingQuoteSnapshotRepository) {
        this.tradingOrderRepository = tradingOrderRepository;
        this.tradingTradeRepository = tradingTradeRepository;
        this.tradingPositionRepository = tradingPositionRepository;
        this.tradingLedgerRepository = tradingLedgerRepository;
        this.tradingLiquidationLogRepository = tradingLiquidationLogRepository;
        this.tradingQuoteSnapshotRepository = tradingQuoteSnapshotRepository;
    }

    public TradingOrderListResponse listOrders(ListTradingOrdersCommand command) {
        int offset = offset(command.page(), command.pageSize());
        List<TradingOrderItemResponse> items = tradingOrderRepository.findByUserIdPaginated(
                        command.userId(),
                        offset,
                        command.pageSize()
                ).stream()
                .map(this::toOrderResponse)
                .toList();
        return new TradingOrderListResponse(
                command.page(),
                command.pageSize(),
                tradingOrderRepository.countByUserId(command.userId()),
                items
        );
    }

    public TradingTradeListResponse listTrades(ListTradingTradesCommand command) {
        int offset = offset(command.page(), command.pageSize());
        List<TradingTradeItemResponse> items = tradingTradeRepository.findByUserIdPaginated(
                        command.userId(),
                        offset,
                        command.pageSize()
                ).stream()
                .map(this::toTradeResponse)
                .toList();
        return new TradingTradeListResponse(
                command.page(),
                command.pageSize(),
                tradingTradeRepository.countByUserId(command.userId()),
                items
        );
    }

    public TradingPositionListResponse listPositions(ListTradingPositionsCommand command) {
        int offset = offset(command.page(), command.pageSize());
        List<TradingPositionItemResponse> items = tradingPositionRepository.findByUserIdPaginated(
                        command.userId(),
                        offset,
                        command.pageSize()
                ).stream()
                .map(this::toPositionResponse)
                .toList();
        return new TradingPositionListResponse(
                command.page(),
                command.pageSize(),
                tradingPositionRepository.countByUserId(command.userId()),
                items
        );
    }

    public TradingLedgerListResponse listLedgerEntries(ListTradingLedgerEntriesCommand command) {
        int offset = offset(command.page(), command.pageSize());
        List<TradingLedgerItemResponse> items = tradingLedgerRepository.findByUserIdPaginated(
                        command.userId(),
                        offset,
                        command.pageSize()
                ).stream()
                .map(this::toLedgerResponse)
                .toList();
        return new TradingLedgerListResponse(
                command.page(),
                command.pageSize(),
                tradingLedgerRepository.countByUserId(command.userId()),
                items
        );
    }

    public TradingLiquidationListResponse listLiquidations(ListTradingLiquidationsCommand command) {
        int offset = offset(command.page(), command.pageSize());
        List<TradingLiquidationItemResponse> items = tradingLiquidationLogRepository.findByUserIdPaginated(
                        command.userId(),
                        offset,
                        command.pageSize()
                ).stream()
                .map(this::toLiquidationResponse)
                .toList();
        return new TradingLiquidationListResponse(
                command.page(),
                command.pageSize(),
                tradingLiquidationLogRepository.countByUserId(command.userId()),
                items
        );
    }

    private int offset(int page, int pageSize) {
        return (page - 1) * pageSize;
    }

    private TradingOrderItemResponse toOrderResponse(TradingOrder order) {
        return new TradingOrderItemResponse(
                order.orderId(),
                order.orderNo(),
                order.symbol(),
                order.side().name(),
                order.orderType().name(),
                order.quantity(),
                order.requestedPrice(),
                order.filledPrice(),
                order.leverage(),
                order.margin(),
                order.fee(),
                order.clientOrderId(),
                order.status().name(),
                order.rejectReason(),
                order.createdAt(),
                order.updatedAt()
        );
    }

    private TradingTradeItemResponse toTradeResponse(TradingTrade trade) {
        return new TradingTradeItemResponse(
                trade.tradeId(),
                trade.orderId(),
                trade.positionId(),
                trade.symbol(),
                trade.side().name(),
                trade.tradeType().name(),
                trade.quantity(),
                trade.price(),
                trade.fee(),
                trade.realizedPnl(),
                trade.tradedAt()
        );
    }

    private TradingPositionItemResponse toPositionResponse(TradingPosition position) {
        TradingQuoteSnapshot quote = position.status() == TradingPositionStatus.OPEN
                ? tradingQuoteSnapshotRepository.findBySymbol(position.symbol()).orElse(null)
                : null;
        BigDecimal markPrice = TradingPricingSupport.resolvePositionMarkPrice(quote, position.side());
        BigDecimal unrealizedPnl = TradingPricingSupport.calculatePositionPnl(position, markPrice);
        return new TradingPositionItemResponse(
                position.positionId(),
                position.openingOrderId(),
                position.symbol(),
                position.side().name(),
                position.quantity(),
                position.entryPrice(),
                position.leverage(),
                position.margin(),
                position.marginMode().name(),
                position.liquidationPrice(),
                position.takeProfitPrice(),
                position.stopLossPrice(),
                markPrice,
                unrealizedPnl,
                position.closePrice(),
                position.closeReason() == null ? null : position.closeReason().name(),
                position.realizedPnl(),
                position.status().name(),
                quote == null ? null : quote.stale(),
                quote == null ? null : quote.ts(),
                quote == null ? null : quote.source(),
                position.openedAt(),
                position.closedAt(),
                position.updatedAt()
        );
    }

    private TradingLedgerItemResponse toLedgerResponse(TradingLedgerEntry entry) {
        return new TradingLedgerItemResponse(
                entry.ledgerId(),
                entry.bizType().name(),
                entry.amount(),
                entry.idempotencyKey(),
                entry.referenceNo(),
                entry.balanceBefore(),
                entry.balanceAfter(),
                entry.frozenBefore(),
                entry.frozenAfter(),
                entry.marginUsedBefore(),
                entry.marginUsedAfter(),
                entry.createdAt()
        );
    }

    private TradingLiquidationItemResponse toLiquidationResponse(TradingLiquidationLog liquidationLog) {
        return new TradingLiquidationItemResponse(
                liquidationLog.liquidationLogId(),
                liquidationLog.positionId(),
                liquidationLog.symbol(),
                liquidationLog.side().name(),
                liquidationLog.quantity(),
                liquidationLog.entryPrice(),
                liquidationLog.liquidationPrice(),
                liquidationLog.markPrice(),
                liquidationLog.priceTs(),
                liquidationLog.priceSource(),
                liquidationLog.loss(),
                liquidationLog.fee(),
                liquidationLog.marginReleased(),
                liquidationLog.platformCoveredLoss(),
                liquidationLog.createdAt()
        );
    }
}
