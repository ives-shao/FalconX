package com.falconx.trading.error;

/**
 * trading-core-service 业务异常。
 *
 * <p>该异常用于把平仓等可预期业务失败统一交给全局异常处理器映射为标准响应结构。
 */
public class TradingBusinessException extends RuntimeException {

    private final TradingErrorCode errorCode;

    public TradingBusinessException(TradingErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public TradingErrorCode getErrorCode() {
        return errorCode;
    }
}
