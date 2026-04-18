package com.falconx.trading.repository;

import com.falconx.trading.entity.TradingQuoteSnapshot;
import java.util.Optional;

/**
 * 交易内部最新价格仓储接口。
 *
 * <p>该接口用于承接高频 `price.tick` 事件后的最新价格状态，
 * 让同步下单和实时引擎都能读取到统一快照。
 */
public interface TradingQuoteSnapshotRepository {

    /**
     * 保存最新行情快照。
     *
     * @param snapshot 行情快照
     * @return 持久化后的快照
     */
    TradingQuoteSnapshot save(TradingQuoteSnapshot snapshot);

    /**
     * 按品种查询最新行情快照。
     *
     * @param symbol 品种
     * @return 行情快照
     */
    Optional<TradingQuoteSnapshot> findBySymbol(String symbol);
}
