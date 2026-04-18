package com.falconx.trading.entity;

/**
 * 业务入金状态枚举。
 *
 * <p>该状态与状态机规范保持一致，表示 trading-core-service 已接受的钱包确认事件
 * 是否已经完成业务入账，或是否被后续回滚事件标记为反转。
 */
public enum TradingDepositStatus {
    CREDITED,
    REVERSED
}
