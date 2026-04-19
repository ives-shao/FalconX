package com.falconx.trading.controller;

import com.falconx.common.api.ApiResponse;
import com.falconx.infrastructure.trace.TraceIdConstants;
import com.falconx.trading.application.TradingPositionCloseApplicationService;
import com.falconx.trading.command.CloseTradingPositionCommand;
import com.falconx.trading.dto.CloseTradingPositionResponse;
import com.falconx.trading.dto.PositionCloseResult;
import com.falconx.trading.dto.TradingAccountPositionResponse;
import com.falconx.trading.dto.TradingAccountResponse;
import com.falconx.trading.engine.OpenPositionSnapshotStore;
import com.falconx.trading.entity.TradingOrderSide;
import com.falconx.trading.entity.TradingQuoteSnapshot;
import com.falconx.trading.entity.TradingPosition;
import com.falconx.trading.entity.TradingTrade;
import com.falconx.trading.repository.TradingQuoteSnapshotRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 持仓控制器。
 *
 * <p>Stage 7 第一刀只开放最小手动平仓接口，不在本轮扩展 TP/SL 修改与追加保证金。
 */
@RestController
@RequestMapping("/api/v1/trading/positions")
public class TradingPositionController {

    private static final Logger log = LoggerFactory.getLogger(TradingPositionController.class);

    private final TradingPositionCloseApplicationService tradingPositionCloseApplicationService;
    private final OpenPositionSnapshotStore openPositionSnapshotStore;
    private final TradingQuoteSnapshotRepository tradingQuoteSnapshotRepository;

    public TradingPositionController(TradingPositionCloseApplicationService tradingPositionCloseApplicationService,
                                     OpenPositionSnapshotStore openPositionSnapshotStore,
                                     TradingQuoteSnapshotRepository tradingQuoteSnapshotRepository) {
        this.tradingPositionCloseApplicationService = tradingPositionCloseApplicationService;
        this.openPositionSnapshotStore = openPositionSnapshotStore;
        this.tradingQuoteSnapshotRepository = tradingQuoteSnapshotRepository;
    }

    /**
     * 手动平掉一笔 OPEN 持仓。
     *
     * @param userId gateway 注入的用户主键
     * @param positionId 持仓主键
     * @return 平仓后的持仓、成交与账户快照
     */
    @PostMapping("/{positionId}/close")
    public ApiResponse<CloseTradingPositionResponse> closePosition(@RequestHeader("X-User-Id") Long userId,
                                                                   @PathVariable("positionId") Long positionId) {
        log.info("trading.http.position.close.received userId={} positionId={}", userId, positionId);
        PositionCloseResult result = tradingPositionCloseApplicationService.closePosition(
                new CloseTradingPositionCommand(userId, positionId)
        );
        return new ApiResponse<>(
                "0",
                "success",
                toResponse(result),
                OffsetDateTime.now(),
                MDC.get(TraceIdConstants.TRACE_ID_MDC_KEY)
        );
    }

    private CloseTradingPositionResponse toResponse(PositionCloseResult result) {
        TradingPosition position = result.position();
        TradingTrade trade = result.trade();
        return new CloseTradingPositionResponse(
                position.positionId(),
                position.status().name(),
                position.closePrice(),
                position.closeReason().name(),
                position.realizedPnl(),
                position.closedAt(),
                trade.tradeId(),
                toAccountResponse(result)
        );
    }

    private TradingAccountResponse toAccountResponse(PositionCloseResult result) {
        List<TradingAccountPositionResponse> openPositions = openPositionSnapshotStore.listOpenByUserId(result.account().userId())
                .stream()
                .map(this::toPositionResponse)
                .toList();
        return new TradingAccountResponse(
                result.account().accountId(),
                result.account().userId(),
                result.account().currency(),
                result.account().balance(),
                result.account().frozen(),
                result.account().marginUsed(),
                result.account().available(),
                openPositions
        );
    }

    private TradingAccountPositionResponse toPositionResponse(TradingPosition position) {
        TradingQuoteSnapshot quote = tradingQuoteSnapshotRepository.findBySymbol(position.symbol()).orElse(null);
        BigDecimal markPrice = quote == null ? null : quote.mark();
        return new TradingAccountPositionResponse(
                position.positionId(),
                position.symbol(),
                position.side().name(),
                position.quantity(),
                position.entryPrice(),
                markPrice,
                calculateUnrealizedPnl(position, markPrice),
                position.liquidationPrice(),
                position.takeProfitPrice(),
                position.stopLossPrice(),
                quote != null && quote.stale(),
                quote == null ? null : quote.ts(),
                quote == null ? null : quote.source()
        );
    }

    private BigDecimal calculateUnrealizedPnl(TradingPosition position, BigDecimal markPrice) {
        if (markPrice == null) {
            return null;
        }
        BigDecimal delta = position.side() == TradingOrderSide.BUY
                ? markPrice.subtract(position.entryPrice())
                : position.entryPrice().subtract(markPrice);
        return delta.multiply(position.quantity()).setScale(8, RoundingMode.HALF_UP);
    }
}
