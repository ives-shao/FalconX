package com.falconx.trading.repository;

import com.falconx.trading.entity.TradingAccount;
import java.util.Optional;

/**
 * 交易账户仓储接口。
 *
 * <p>该仓储负责承载 `t_account` owner 数据的最小读写能力。
 * 当前正式实现统一通过 `MyBatis Mapper + XML + Repository` 链路访问数据库。
 */
public interface TradingAccountRepository {

    /**
     * 按用户和币种查询账户。
     *
     * @param userId 用户 ID
     * @param currency 账户币种
     * @return 账户可选结果
     */
    Optional<TradingAccount> findByUserIdAndCurrency(Long userId, String currency);

    /**
     * 按用户和币种查询账户并对结果行加悲观锁。
     *
     * <p>该方法用于“先风控、再扣减”的交易写路径，避免并发请求在同一账户上超额占用保证金。
     *
     * @param userId 用户 ID
     * @param currency 账户币种
     * @return 加锁后的账户可选结果
     */
    Optional<TradingAccount> findByUserIdAndCurrencyForUpdate(Long userId, String currency);

    /**
     * 保存账户。
     *
     * @param account 账户对象
     * @return 持久化后的账户对象
     */
    TradingAccount save(TradingAccount account);
}
