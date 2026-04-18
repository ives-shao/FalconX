package com.falconx.trading.dto;

import java.math.BigDecimal;

/**
 * 交易账户响应 DTO。
 *
 * <p>该对象用于对外暴露交易账户的稳定查询结构，
 * 除核心余额视图外，还会返回当前 OPEN 持仓及基于 Redis 最新价动态计算的未实现盈亏。
 */
public record TradingAccountResponse(
        Long accountId,
        Long userId,
        String currency,
        BigDecimal balance,
        BigDecimal frozen,
        BigDecimal marginUsed,
        BigDecimal available,
        java.util.List<TradingAccountPositionResponse> openPositions
) {
}
