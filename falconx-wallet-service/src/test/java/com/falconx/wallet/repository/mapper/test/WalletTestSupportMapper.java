package com.falconx.wallet.repository.mapper.test;

import java.time.OffsetDateTime;
import org.apache.ibatis.annotations.Mapper;

/**
 * wallet-service 测试专用 Mapper。
 *
 * <p>该 Mapper 只在测试源码中存在，用来完成 Stage 5 集成测试的清表和结果断言。
 */
@Mapper
public interface WalletTestSupportMapper {

    default void clearOwnerTables() {
        deleteOutbox();
        deleteWalletChainCursor();
        deleteWalletDepositTransaction();
        deleteWalletAddress();
    }

    int deleteOutbox();

    int deleteWalletChainCursor();

    int deleteWalletDepositTransaction();

    int deleteWalletAddress();

    Integer countWalletAddressByUserId(long userId);

    Integer countWalletDepositByUserId(long userId);

    Integer countConfirmedDepositByTxHash(String txHash);

    Integer selectWalletDepositStatusByTxHash(String txHash);

    OffsetDateTime selectConfirmedAtByTxHash(String txHash);

    Integer countOutbox();

    Integer countOutboxByEventType(String eventType);
}
