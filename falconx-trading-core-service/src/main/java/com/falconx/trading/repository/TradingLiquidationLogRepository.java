package com.falconx.trading.repository;

import com.falconx.trading.entity.TradingLiquidationLog;

/**
 * 强平日志仓储接口。
 *
 * <p>该仓储负责把 `t_liquidation_log` 作为 owner 审计事实正式落库。
 */
public interface TradingLiquidationLogRepository {

    /**
     * 保存一条强平日志。
     *
     * @param liquidationLog 强平日志
     * @return 持久化后的日志
     */
    TradingLiquidationLog save(TradingLiquidationLog liquidationLog);
}
