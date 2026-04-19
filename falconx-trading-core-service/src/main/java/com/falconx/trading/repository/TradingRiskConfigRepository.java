package com.falconx.trading.repository;

import com.falconx.trading.entity.TradingRiskConfig;
import java.util.Optional;

/**
 * 风控参数仓储接口。
 *
 * <p>该仓储负责按品种读取 owner 维护的 `t_risk_config`，
 * 供交易核心在下单后判断 B-book 净美元敞口是否已经超过对冲告警阈值。
 */
public interface TradingRiskConfigRepository {

    /**
     * 按品种查询风控参数。
     *
     * @param symbol 交易品种
     * @return 风控参数
     */
    Optional<TradingRiskConfig> findBySymbol(String symbol);
}
