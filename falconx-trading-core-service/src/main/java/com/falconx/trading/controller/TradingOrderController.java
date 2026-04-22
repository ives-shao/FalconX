package com.falconx.trading.controller;

import com.falconx.common.api.ApiResponse;
import com.falconx.infrastructure.trace.TraceIdConstants;
import com.falconx.trading.application.TradingOrderPlacementApplicationService;
import com.falconx.trading.command.PlaceMarketOrderCommand;
import com.falconx.trading.dto.OrderPlacementResult;
import com.falconx.trading.dto.PlaceMarketOrderRequest;
import com.falconx.trading.dto.PlaceMarketOrderResponse;
import com.falconx.trading.dto.TradingAccountResponse;
import com.falconx.trading.entity.TradingOrder;
import com.falconx.trading.entity.TradingPosition;
import com.falconx.trading.entity.TradingTrade;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 市价单控制器。
 *
 * <p>该控制器负责暴露 Stage 4 最小交易写接口：
 * 当前只支持市价开仓骨架，用于打通 gateway -> trading-core-service 的外部闭环。
 */
@RestController
@RequestMapping("/api/v1/trading/orders")
public class TradingOrderController {

    private static final Logger log = LoggerFactory.getLogger(TradingOrderController.class);

    private final TradingOrderPlacementApplicationService tradingOrderPlacementApplicationService;

    public TradingOrderController(TradingOrderPlacementApplicationService tradingOrderPlacementApplicationService) {
        this.tradingOrderPlacementApplicationService = tradingOrderPlacementApplicationService;
    }

    /**
     * 提交一笔市价单。
     *
     * <p>该接口使用 gateway 注入的 `X-User-Id` 作为交易主体，
     * 不允许客户端直接在请求体内传递 `userId`，以避免越权和多来源歧义。
     *
     * @param userId gateway 注入的用户主键
     * @param request 市价单请求体
     * @return 下单结果；若被风控拒绝，业务码返回 `40002`
     */
    @PostMapping("/market")
    public ApiResponse<PlaceMarketOrderResponse> placeMarketOrder(@RequestHeader("X-User-Id") Long userId,
                                                                  @Valid @RequestBody PlaceMarketOrderRequest request) {
        log.info("trading.http.order.received userId={} symbol={} clientOrderId={}",
                userId,
                request.symbol(),
                request.clientOrderId());
        OrderPlacementResult result = tradingOrderPlacementApplicationService.placeMarketOrder(new PlaceMarketOrderCommand(
                userId,
                request.symbol(),
                request.side(),
                request.quantity(),
                request.leverage(),
                request.marginMode(),
                request.takeProfitPrice(),
                request.stopLossPrice(),
                request.clientOrderId()
        ));

        PlaceMarketOrderResponse response = toResponse(result);
        if (result.rejectionReason() != null) {
            String code = "40002";
            String message = "Order Rejected";
            if ("SYMBOL_TRADING_SUSPENDED".equals(result.rejectionReason())) {
                code = "40008";
                message = "Symbol Trading Suspended";
            } else if ("MARGIN_MODE_NOT_SUPPORTED".equals(result.rejectionReason())) {
                code = "40010";
                message = "Margin Mode Not Supported";
            } else if ("INSUFFICIENT_AVAILABLE_BALANCE".equals(result.rejectionReason())) {
                code = "40001";
                message = "Insufficient Margin";
            }
            return new ApiResponse<>(
                    code,
                    message,
                    response,
                    OffsetDateTime.now(),
                    MDC.get(TraceIdConstants.TRACE_ID_MDC_KEY)
            );
        }
        return new ApiResponse<>(
                "0",
                "success",
                response,
                OffsetDateTime.now(),
                MDC.get(TraceIdConstants.TRACE_ID_MDC_KEY)
        );
    }

    private PlaceMarketOrderResponse toResponse(OrderPlacementResult result) {
        TradingOrder order = result.order();
        TradingPosition position = result.position();
        TradingTrade trade = result.trade();
        return new PlaceMarketOrderResponse(
                order.orderNo(),
                order.status().name(),
                result.rejectionReason(),
                result.duplicate(),
                order.symbol(),
                order.side().name(),
                order.quantity(),
                order.requestedPrice(),
                order.filledPrice(),
                order.leverage(),
                position == null || position.marginMode() == null ? null : position.marginMode().name(),
                order.margin(),
                order.fee(),
                position == null ? null : position.positionId(),
                position == null ? null : position.status().name(),
                position == null ? null : position.takeProfitPrice(),
                position == null ? null : position.stopLossPrice(),
                trade == null ? null : trade.tradeId(),
                new TradingAccountResponse(
                        result.account().accountId(),
                        result.account().userId(),
                        result.account().currency(),
                        result.account().balance(),
                        result.account().frozen(),
                        result.account().marginUsed(),
                        result.account().available(),
                        java.util.List.of()
                )
        );
    }
}
