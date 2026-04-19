package com.falconx.trading.error;

import com.falconx.common.error.ErrorCode;

/**
 * trading-core-service 业务错误码。
 *
 * <p>本轮只补齐手动平仓最小链路直接使用到的错误码，
 * 其余交易业务码继续按既有 controller 返回语义保留。
 */
public enum TradingErrorCode implements ErrorCode {
    QUOTE_NOT_AVAILABLE("30003", "Quote Not Available"),
    PRICE_SOURCE_STALE_OR_DISCONNECTED("30002", "Price Source Stale Or Disconnected"),
    POSITION_NOT_FOUND("40004", "Position Not Found"),
    POSITION_ALREADY_CLOSED("40007", "Position Already Closed");

    private final String code;
    private final String message;

    TradingErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
