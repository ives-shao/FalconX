package com.falconx.trading.repository;

import com.falconx.trading.entity.TradingTrade;
import java.util.Optional;

/**
 * 成交仓储接口。
 *
 * <p>当前阶段提供最小保存和按订单查询能力，
 * 用于支撑“下单 -> 成交”链路测试。
 */
public interface TradingTradeRepository {

    /**
     * 保存成交。
     *
     * @param trade 成交对象
     * @return 持久化后的成交对象
     */
    TradingTrade save(TradingTrade trade);

    /**
     * 按订单 ID 查询成交。
     *
     * @param orderId 订单 ID
     * @return 成交可选结果
     */
    Optional<TradingTrade> findByOrderId(Long orderId);
}
