package com.falconx.trading.repository;

import com.falconx.trading.service.model.TradingSwapRateSnapshot;
import java.util.Optional;

/**
 * 隔夜利息共享快照读仓储。
 *
 * <p>`trading-core-service` 只通过该接口读取 market owner 写入的 Redis 快照，
 * 不跨服务访问 `falconx_market`。
 */
public interface TradingSwapRateSnapshotRepository {

    /**
     * 按 symbol 读取共享快照。
     *
     * @param symbol 品种代码
     * @return 快照可选结果
     */
    Optional<TradingSwapRateSnapshot> findBySymbol(String symbol);
}
