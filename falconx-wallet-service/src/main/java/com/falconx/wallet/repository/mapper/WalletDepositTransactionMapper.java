package com.falconx.wallet.repository.mapper;

import com.falconx.wallet.repository.mapper.record.WalletDepositTransactionRecord;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 原始链上入金 MyBatis Mapper。
 *
 * <p>该 Mapper 负责 `t_wallet_deposit_tx` 的 SQL 声明。
 */
@Mapper
public interface WalletDepositTransactionMapper {

    WalletDepositTransactionRecord selectByChainAndTxHashAndLogIndex(@Param("chain") String chain,
                                                                     @Param("txHash") String txHash,
                                                                     @Param("logIndex") Integer logIndex);

    List<WalletDepositTransactionRecord> selectByChainAndBlockRange(@Param("chain") String chain,
                                                                    @Param("fromBlock") Long fromBlock,
                                                                    @Param("toBlock") Long toBlock);

    int insertWalletDepositTransaction(WalletDepositTransactionRecord record);

    int updateWalletDepositTransaction(WalletDepositTransactionRecord record);
}
