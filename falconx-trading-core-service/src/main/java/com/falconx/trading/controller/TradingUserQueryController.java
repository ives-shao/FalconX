package com.falconx.trading.controller;

import com.falconx.common.api.ApiResponse;
import com.falconx.infrastructure.trace.TraceIdConstants;
import com.falconx.trading.application.TradingUserQueryApplicationService;
import com.falconx.trading.command.ListTradingLedgerEntriesCommand;
import com.falconx.trading.command.ListTradingLiquidationsCommand;
import com.falconx.trading.command.ListTradingOrdersCommand;
import com.falconx.trading.command.ListTradingPositionsCommand;
import com.falconx.trading.command.ListTradingTradesCommand;
import com.falconx.trading.dto.TradingLedgerListResponse;
import com.falconx.trading.dto.TradingLiquidationListResponse;
import com.falconx.trading.dto.TradingOrderListResponse;
import com.falconx.trading.dto.TradingPositionListResponse;
import com.falconx.trading.dto.TradingTradeListResponse;
import com.falconx.trading.error.TradingRequestValidationException;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户视角查询控制器。
 */
@RestController
@RequestMapping("/api/v1/trading")
public class TradingUserQueryController {

    private static final Logger log = LoggerFactory.getLogger(TradingUserQueryController.class);

    private final TradingUserQueryApplicationService tradingUserQueryApplicationService;

    public TradingUserQueryController(TradingUserQueryApplicationService tradingUserQueryApplicationService) {
        this.tradingUserQueryApplicationService = tradingUserQueryApplicationService;
    }

    @GetMapping("/orders")
    public ApiResponse<TradingOrderListResponse> listOrders(@RequestHeader("X-User-Id") Long userId,
                                                            @RequestParam(defaultValue = "1") int page,
                                                            @RequestParam(defaultValue = "20") int pageSize) {
        validatePage(page, pageSize);
        log.info("trading.http.orders.received userId={} page={} pageSize={}", userId, page, pageSize);
        TradingOrderListResponse response = tradingUserQueryApplicationService.listOrders(
                new ListTradingOrdersCommand(userId, page, pageSize)
        );
        return successResponse(response);
    }

    @GetMapping("/trades")
    public ApiResponse<TradingTradeListResponse> listTrades(@RequestHeader("X-User-Id") Long userId,
                                                            @RequestParam(defaultValue = "1") int page,
                                                            @RequestParam(defaultValue = "20") int pageSize) {
        validatePage(page, pageSize);
        log.info("trading.http.trades.received userId={} page={} pageSize={}", userId, page, pageSize);
        TradingTradeListResponse response = tradingUserQueryApplicationService.listTrades(
                new ListTradingTradesCommand(userId, page, pageSize)
        );
        return successResponse(response);
    }

    @GetMapping("/positions")
    public ApiResponse<TradingPositionListResponse> listPositions(@RequestHeader("X-User-Id") Long userId,
                                                                  @RequestParam(defaultValue = "1") int page,
                                                                  @RequestParam(defaultValue = "20") int pageSize) {
        validatePage(page, pageSize);
        log.info("trading.http.positions.received userId={} page={} pageSize={}", userId, page, pageSize);
        TradingPositionListResponse response = tradingUserQueryApplicationService.listPositions(
                new ListTradingPositionsCommand(userId, page, pageSize)
        );
        return successResponse(response);
    }

    @GetMapping("/ledger")
    public ApiResponse<TradingLedgerListResponse> listLedgerEntries(@RequestHeader("X-User-Id") Long userId,
                                                                    @RequestParam(defaultValue = "1") int page,
                                                                    @RequestParam(defaultValue = "20") int pageSize) {
        validatePage(page, pageSize);
        log.info("trading.http.ledger.received userId={} page={} pageSize={}", userId, page, pageSize);
        TradingLedgerListResponse response = tradingUserQueryApplicationService.listLedgerEntries(
                new ListTradingLedgerEntriesCommand(userId, page, pageSize)
        );
        return successResponse(response);
    }

    @GetMapping("/liquidations")
    public ApiResponse<TradingLiquidationListResponse> listLiquidations(@RequestHeader("X-User-Id") Long userId,
                                                                        @RequestParam(defaultValue = "1") int page,
                                                                        @RequestParam(defaultValue = "20") int pageSize) {
        validatePage(page, pageSize);
        log.info("trading.http.liquidations.received userId={} page={} pageSize={}", userId, page, pageSize);
        TradingLiquidationListResponse response = tradingUserQueryApplicationService.listLiquidations(
                new ListTradingLiquidationsCommand(userId, page, pageSize)
        );
        return successResponse(response);
    }

    private void validatePage(int page, int pageSize) {
        if (page < 1) {
            throw new TradingRequestValidationException("page must be greater than or equal to 1");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new TradingRequestValidationException("pageSize must be between 1 and 100");
        }
    }

    private <T> ApiResponse<T> successResponse(T data) {
        return new ApiResponse<>(
                "0",
                "success",
                data,
                OffsetDateTime.now(),
                MDC.get(TraceIdConstants.TRACE_ID_MDC_KEY)
        );
    }
}
