package com.falconx.trading.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 持仓实体。
 *
 * <p>该对象对应 `falconx_trading.t_position` 的内存骨架表达。
 * 当前阶段只保留“一次开仓生成一条持仓”的最小模型，
 * 用于支撑订单、保证金和强平价的最小链路测试。
 */
public record TradingPosition(
        Long positionId,
        Long openingOrderId,
        Long userId,
        String symbol,
        TradingOrderSide side,
        BigDecimal quantity,
        BigDecimal entryPrice,
        BigDecimal leverage,
        BigDecimal margin,
        TradingMarginMode marginMode,
        BigDecimal liquidationPrice,
        BigDecimal takeProfitPrice,
        BigDecimal stopLossPrice,
        BigDecimal closePrice,
        TradingPositionCloseReason closeReason,
        BigDecimal realizedPnl,
        TradingPositionStatus status,
        OffsetDateTime openedAt,
        OffsetDateTime closedAt,
        OffsetDateTime updatedAt
) {

    public boolean isOpen() {
        return status == TradingPositionStatus.OPEN;
    }

    public boolean isTerminal() {
        return !isOpen();
    }

    /**
     * 生成更新 TP/SL 后的 OPEN 持仓快照。
     */
    public TradingPosition updateRiskControls(BigDecimal nextTakeProfitPrice,
                                              BigDecimal nextStopLossPrice,
                                              OffsetDateTime occurredAt) {
        return new TradingPosition(
                positionId,
                openingOrderId,
                userId,
                symbol,
                side,
                quantity,
                entryPrice,
                leverage,
                margin,
                marginMode,
                liquidationPrice,
                nextTakeProfitPrice,
                nextStopLossPrice,
                closePrice,
                closeReason,
                realizedPnl,
                status,
                openedAt,
                closedAt,
                occurredAt
        );
    }

    /**
     * 生成退出持仓后的终态快照。
     *
     * @param targetStatus 终态状态
     * @param targetCloseReason 终态原因
     * @param finalPrice 平仓/强平价
     * @param pnl 已实现盈亏
     * @param occurredAt 完成时间
     * @return 终态持仓
     */
    public TradingPosition close(TradingPositionStatus targetStatus,
                                 TradingPositionCloseReason targetCloseReason,
                                 BigDecimal finalPrice,
                                 BigDecimal pnl,
                                 OffsetDateTime occurredAt) {
        return new TradingPosition(
                positionId,
                openingOrderId,
                userId,
                symbol,
                side,
                quantity,
                entryPrice,
                leverage,
                margin,
                marginMode,
                liquidationPrice,
                takeProfitPrice,
                stopLossPrice,
                finalPrice,
                targetCloseReason,
                pnl,
                targetStatus,
                openedAt,
                occurredAt,
                occurredAt
        );
    }
}
