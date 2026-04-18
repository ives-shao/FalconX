package com.falconx.trading.repository.mapper;

import com.falconx.trading.repository.mapper.record.TradingAccountRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 交易账户 MyBatis Mapper。
 *
 * <p>该 Mapper 负责 `t_account` 的 SQL 声明，Repository 负责领域语义转换。
 */
@Mapper
public interface TradingAccountMapper {

    /**
     * 按用户和币种查询账户记录。
     *
     * @param userId 用户 ID
     * @param currency 币种
     * @return 账户记录
     */
    TradingAccountRecord selectByUserIdAndCurrency(@Param("userId") Long userId, @Param("currency") String currency);

    /**
     * 按用户和币种查询账户记录，并对该行加悲观锁。
     *
     * @param userId 用户 ID
     * @param currency 币种
     * @return 被锁定的账户记录
     */
    TradingAccountRecord selectByUserIdAndCurrencyForUpdate(@Param("userId") Long userId, @Param("currency") String currency);

    /**
     * 插入新账户。
     *
     * @param record 账户记录
     * @return 影响行数
     */
    int insertTradingAccount(TradingAccountRecord record);

    /**
     * 更新账户快照。
     *
     * @param record 账户记录
     * @return 影响行数
     */
    int updateTradingAccount(TradingAccountRecord record);
}
