package com.falconx.trading.service;

import java.time.OffsetDateTime;

/**
 * 交易时间校验服务。
 *
 * <p>该服务负责按方案 B 规则判断某个 `symbol` 在某一时刻是否允许交易。
 */
public interface TradingScheduleService {

    /**
     * 判断当前时刻是否允许开仓。
     *
     * @param symbol 品种代码
     * @param now 当前时间
     * @return `true` 表示允许开仓
     */
    boolean isOpenAllowed(String symbol, OffsetDateTime now);

    /**
     * 判断当前时刻是否允许手动平仓。
     *
     * <p>Stage 7 起，手动平仓不再受开仓交易时间窗口阻塞；
     * 因此该方法只表达“关闭动作本身不依赖 market schedule 开放”这一冻结语义。
     *
     * @param symbol 品种代码
     * @param now 当前时间
     * @return `true` 表示允许继续执行平仓链路
     */
    boolean isCloseAllowed(String symbol, OffsetDateTime now);

    /**
     * 兼容既有调用方的旧方法。
     */
    default boolean isTradable(String symbol, OffsetDateTime now) {
        return isOpenAllowed(symbol, now);
    }
}
