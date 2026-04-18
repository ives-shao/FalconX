package com.falconx.trading.entity;

/**
 * 账本业务类型枚举。
 *
 * <p>该枚举用于区分账户余额、冻结金额和保证金占用变化的来源，
 * 便于后续对账、审计和流水回放。
 */
public enum TradingLedgerBizType {
    DEPOSIT_CREDIT,
    DEPOSIT_REVERSAL,
    ORDER_MARGIN_RESERVED,
    ORDER_FEE_CHARGED,
    ORDER_MARGIN_CONFIRMED,
    SWAP_CHARGE,
    SWAP_INCOME,
    REALIZED_PNL,
    LIQUIDATION_LOSS
}
