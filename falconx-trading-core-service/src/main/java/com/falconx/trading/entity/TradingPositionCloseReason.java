package com.falconx.trading.entity;

/**
 * 持仓终态原因。
 *
 * <p>本轮只真正实现 `MANUAL`，其余枚举值用于冻结 TP/SL 与强平的持久化编码。
 */
public enum TradingPositionCloseReason {
    MANUAL,
    TAKE_PROFIT,
    STOP_LOSS,
    LIQUIDATION
}
