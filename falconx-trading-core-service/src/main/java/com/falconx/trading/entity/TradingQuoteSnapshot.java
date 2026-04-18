package com.falconx.trading.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 交易域内部行情快照。
 *
 * <p>该对象用于承接 `market-service` 推送过来的标准价格事件，
 * 作为交易核心服务后续下单、风控和强平计算的内部输入。
 * 当前默认由 owner 仓储持久化到真实 Redis 快照读写链路。
 */
public record TradingQuoteSnapshot(
        String symbol,
        BigDecimal bid,
        BigDecimal ask,
        BigDecimal mark,
        OffsetDateTime ts,
        String source,
        boolean stale
) {
}
