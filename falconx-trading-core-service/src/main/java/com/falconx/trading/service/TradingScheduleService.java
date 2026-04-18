package com.falconx.trading.service;

import java.time.OffsetDateTime;

/**
 * 交易时间校验服务。
 *
 * <p>该服务负责按方案 B 规则判断某个 `symbol` 在某一时刻是否允许交易。
 */
public interface TradingScheduleService {

    /**
     * 判断当前时刻是否可交易。
     *
     * @param symbol 品种代码
     * @param now 当前时间
     * @return `true` 表示允许交易
     */
    boolean isTradable(String symbol, OffsetDateTime now);
}
