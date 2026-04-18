package com.falconx.trading.repository;

import com.falconx.trading.entity.TradingDeposit;
import java.util.Optional;

/**
 * 业务入金仓储接口。
 *
 * <p>该接口负责保存 `t_deposit` 业务事实，
 * 并以 `walletTxId` 作为最小业务幂等查询入口。
 */
public interface TradingDepositRepository {

    /**
     * 按 wallet owner 主键查询业务入金。
     *
     * @param walletTxId wallet owner 产出的稳定原始交易主键
     * @return 入金事实
     */
    Optional<TradingDeposit> findByWalletTxId(Long walletTxId);

    /**
     * 保存业务入金事实。
     *
     * @param deposit 入金对象
     * @return 持久化后的入金对象
     */
    TradingDeposit save(TradingDeposit deposit);
}
