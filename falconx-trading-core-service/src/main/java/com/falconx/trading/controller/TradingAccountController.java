package com.falconx.trading.controller;

import com.falconx.common.api.ApiResponse;
import com.falconx.infrastructure.trace.TraceIdConstants;
import com.falconx.trading.config.TradingCoreServiceProperties;
import com.falconx.trading.dto.TradingAccountPositionResponse;
import com.falconx.trading.dto.TradingAccountResponse;
import com.falconx.trading.engine.OpenPositionSnapshotStore;
import com.falconx.trading.entity.TradingAccount;
import com.falconx.trading.entity.TradingOrderSide;
import com.falconx.trading.entity.TradingPosition;
import com.falconx.trading.entity.TradingQuoteSnapshot;
import com.falconx.trading.repository.TradingQuoteSnapshotRepository;
import com.falconx.trading.service.TradingAccountService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 交易账户查询控制器。
 *
 * <p>该控制器是 Stage 4 对外最小交易查询入口之一。
 * 它固定读取 gateway 注入的 `X-User-Id`，
 * 并返回当前用户在默认结算币种下的账户快照。
 */
@RestController
@RequestMapping("/api/v1/trading/accounts")
public class TradingAccountController {

    private static final Logger log = LoggerFactory.getLogger(TradingAccountController.class);

    private final TradingAccountService tradingAccountService;
    private final TradingCoreServiceProperties tradingCoreServiceProperties;
    private final OpenPositionSnapshotStore openPositionSnapshotStore;
    private final TradingQuoteSnapshotRepository tradingQuoteSnapshotRepository;

    public TradingAccountController(TradingAccountService tradingAccountService,
                                    TradingCoreServiceProperties tradingCoreServiceProperties,
                                    OpenPositionSnapshotStore openPositionSnapshotStore,
                                    TradingQuoteSnapshotRepository tradingQuoteSnapshotRepository) {
        this.tradingAccountService = tradingAccountService;
        this.tradingCoreServiceProperties = tradingCoreServiceProperties;
        this.openPositionSnapshotStore = openPositionSnapshotStore;
        this.tradingQuoteSnapshotRepository = tradingQuoteSnapshotRepository;
    }

    /**
     * 查询当前登录用户的交易账户快照。
     *
     * @param userId gateway 注入的用户主键
     * @return 统一响应结构下的账户快照
     */
    @GetMapping("/me")
    public ApiResponse<TradingAccountResponse> getCurrentAccount(@RequestHeader("X-User-Id") Long userId) {
        log.info("trading.http.account.received userId={}", userId);
        TradingAccount account = tradingAccountService.getOrCreateAccount(
                userId,
                tradingCoreServiceProperties.getSettlementToken()
        );
        return new ApiResponse<>(
                "0",
                "success",
                toResponse(account),
                OffsetDateTime.now(),
                MDC.get(TraceIdConstants.TRACE_ID_MDC_KEY)
        );
    }

    private TradingAccountResponse toResponse(TradingAccount account) {
        List<TradingAccountPositionResponse> openPositions = openPositionSnapshotStore.listOpenByUserId(account.userId())
                .stream()
                .map(this::toPositionResponse)
                .toList();
        return new TradingAccountResponse(
                account.accountId(),
                account.userId(),
                account.currency(),
                account.balance(),
                account.frozen(),
                account.marginUsed(),
                account.available(),
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
