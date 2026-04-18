package com.falconx.trading.repository.mapper.test;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * trading-core-service 测试专用 Mapper。
 *
 * <p>该 Mapper 只存在于测试源码中，用来完成 Stage 5 集成测试的环境清理和结果断言。
 * 这样测试代码本身也不再使用字符串 SQL，而是继续遵循 `MyBatis + XML` 规范。
 */
@Mapper
public interface TradingTestSupportMapper {

    /**
     * 依赖关系从弱到强清空 owner 表，避免外键或唯一键残留影响下一条用例。
     */
    default void clearOwnerTables() {
        deleteInbox();
        deleteOutbox();
        deleteRiskExposure();
        deleteTrade();
        deletePosition();
        deleteOrder();
        deleteDeposit();
        deleteLedger();
        deleteAccount();
    }

    int deleteInbox();

    int deleteOutbox();

    int deleteRiskExposure();

    int deleteTrade();

    int deletePosition();

    int deleteOrder();

    int deleteDeposit();

    int deleteLedger();

    int deleteAccount();

    Integer countAccountsByUserId(@Param("userId") Long userId);

    Integer countDepositsByUserId(@Param("userId") Long userId);

    Integer countDepositsWithWalletTxIdByUserId(@Param("userId") Long userId);

    Integer countDepositsByWalletTxId(@Param("walletTxId") Long walletTxId);

    Integer selectDepositStatusCodeByWalletTxId(@Param("walletTxId") Long walletTxId);

    Integer countLedgerByUserId(@Param("userId") Long userId);

    String selectAccountBalanceByUserId(@Param("userId") Long userId);

    Integer countOrdersByUserId(@Param("userId") Long userId);

    Integer countOpenPositionsByUserId(@Param("userId") Long userId);

    Integer countTradesByUserId(@Param("userId") Long userId);

    Integer countOutbox();

    Integer countOutboxByEventType(@Param("eventType") String eventType);

    Integer countInboxByEventId(@Param("eventId") String eventId);

    Integer countInboxByEventType(@Param("eventType") String eventType);

    Integer countRiskExposureBySymbol(@Param("symbol") String symbol);

    String selectRiskExposureNetBySymbol(@Param("symbol") String symbol);
}
