package com.falconx.trading.repository.mapper;

import com.falconx.trading.repository.mapper.record.TradingDepositRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 业务入金 MyBatis Mapper。
 *
 * <p>该 Mapper 负责 `t_deposit` 的 SQL 声明。
 */
@Mapper
public interface TradingDepositMapper {

    /**
     * 按链和交易哈希查询业务入金。
     *
     * @param chain 链标识
     * @param txHash 交易哈希
     * @return 入金记录
     */
    TradingDepositRecord selectByChainAndTxHash(@Param("chain") String chain, @Param("txHash") String txHash);

    /**
     * 插入业务入金记录。
     *
     * @param record 入金记录
     * @return 影响行数
     */
    int insertTradingDeposit(TradingDepositRecord record);

    /**
     * 更新业务入金记录。
     *
     * @param record 入金记录
     * @return 影响行数
     */
    int updateTradingDeposit(TradingDepositRecord record);
}
