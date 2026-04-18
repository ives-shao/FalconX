package com.falconx.trading.repository;

import com.falconx.domain.enums.ChainType;
import com.falconx.trading.entity.TradingDeposit;
import java.util.Optional;

/**
 * 业务入金仓储接口。
 *
 * <p>该接口负责保存 `t_deposit` 业务事实，
 * 并以 `(chain, txHash)` 作为最小业务幂等查询入口。
 */
public interface TradingDepositRepository {

    /**
     * 按链和交易哈希查询业务入金。
     *
     * @param chain 链类型
     * @param txHash 交易哈希
     * @return 入金事实
     */
    Optional<TradingDeposit> findByChainAndTxHash(ChainType chain, String txHash);

    /**
     * 保存业务入金事实。
     *
     * @param deposit 入金对象
     * @return 持久化后的入金对象
     */
    TradingDeposit save(TradingDeposit deposit);
}
