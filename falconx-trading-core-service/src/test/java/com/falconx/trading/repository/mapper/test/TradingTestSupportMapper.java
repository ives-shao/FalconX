package com.falconx.trading.repository.mapper.test;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.math.BigDecimal;
import java.time.LocalDateTime;

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
        deleteHedgeLog();
        deleteLiquidationLog();
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

    int deleteHedgeLog();

    int deleteLiquidationLog();

    int deleteRiskExposure();

    int deleteTrade();

    int deletePosition();

    int deleteOrder();

    int deleteDeposit();

    int deleteLedger();

    int deleteAccount();

    int deleteAccountByUserIdAndCurrency(@Param("userId") Long userId,
                                         @Param("currency") String currency);

    Integer countAccountsByUserId(@Param("userId") Long userId);

    Integer countDepositsByUserId(@Param("userId") Long userId);

    Integer countDepositsWithWalletTxIdByUserId(@Param("userId") Long userId);

    Integer countDepositsByWalletTxId(@Param("walletTxId") Long walletTxId);

    Integer selectDepositStatusCodeByWalletTxId(@Param("walletTxId") Long walletTxId);

    Integer countLedgerByUserId(@Param("userId") Long userId);

    Integer countLedgerByUserIdAndBizType(@Param("userId") Long userId,
                                          @Param("bizTypeCode") Integer bizTypeCode);

    String selectAccountBalanceByUserId(@Param("userId") Long userId);

    String selectAccountFrozenByUserId(@Param("userId") Long userId);

    String selectAccountMarginUsedByUserId(@Param("userId") Long userId);

    Integer countOrdersByUserId(@Param("userId") Long userId);

    Integer countOpenPositionsByUserId(@Param("userId") Long userId);

    Long selectLatestPositionIdByUserId(@Param("userId") Long userId);

    int updatePositionOpenedAt(@Param("positionId") Long positionId,
                               @Param("openedAt") LocalDateTime openedAt);

    Integer selectPositionStatusCodeById(@Param("positionId") Long positionId);

    String selectPositionClosePriceById(@Param("positionId") Long positionId);

    String selectPositionTakeProfitPriceById(@Param("positionId") Long positionId);

    String selectPositionStopLossPriceById(@Param("positionId") Long positionId);

    String selectPositionLiquidationPriceById(@Param("positionId") Long positionId);

    Integer selectPositionCloseReasonCodeById(@Param("positionId") Long positionId);

    String selectPositionRealizedPnlById(@Param("positionId") Long positionId);

    String selectPositionClosedAtById(@Param("positionId") Long positionId);

    Integer countTradesByUserId(@Param("userId") Long userId);

    Integer countTradesByPositionId(@Param("positionId") Long positionId);

    Integer countTradesByPositionIdAndTradeType(@Param("positionId") Long positionId,
                                                @Param("tradeTypeCode") Integer tradeTypeCode);

    String selectTradePriceByPositionIdAndTradeType(@Param("positionId") Long positionId,
                                                    @Param("tradeTypeCode") Integer tradeTypeCode);

    String selectTradeRealizedPnlByPositionIdAndTradeType(@Param("positionId") Long positionId,
                                                          @Param("tradeTypeCode") Integer tradeTypeCode);

    String selectTradeFeeByPositionIdAndTradeType(@Param("positionId") Long positionId,
                                                  @Param("tradeTypeCode") Integer tradeTypeCode);

    Integer countOutbox();

    Integer countOutboxByEventType(@Param("eventType") String eventType);

    Integer countInboxByEventId(@Param("eventId") String eventId);

    Integer countInboxByEventType(@Param("eventType") String eventType);

    Integer countRiskExposureBySymbol(@Param("symbol") String symbol);

    String selectRiskExposureNetBySymbol(@Param("symbol") String symbol);

    String selectRiskExposureNetUsdBySymbol(@Param("symbol") String symbol);

    String selectRiskExposureTotalLongQtyBySymbol(@Param("symbol") String symbol);

    String selectRiskExposureTotalShortQtyBySymbol(@Param("symbol") String symbol);

    String selectLatestLedgerAmountByUserIdAndBizType(@Param("userId") Long userId,
                                                      @Param("bizTypeCode") Integer bizTypeCode);

    String selectLatestLedgerBalanceSnapshotByUserIdAndBizType(@Param("userId") Long userId,
                                                               @Param("bizTypeCode") Integer bizTypeCode);

    String selectLatestLedgerMarginUsedSnapshotByUserIdAndBizType(@Param("userId") Long userId,
                                                                  @Param("bizTypeCode") Integer bizTypeCode);

    Integer countLiquidationLogsByPositionId(@Param("positionId") Long positionId);

    String selectLiquidationLogPriceByPositionId(@Param("positionId") Long positionId);

    String selectLiquidationLogPlatformCoveredLossByPositionId(@Param("positionId") Long positionId);

    String selectLiquidationLogMarginReleasedByPositionId(@Param("positionId") Long positionId);

    Integer countHedgeLogsBySymbol(@Param("symbol") String symbol);

    Integer selectLatestHedgeLogActionStatusCodeBySymbol(@Param("symbol") String symbol);

    Integer selectLatestHedgeLogTriggerSourceCodeBySymbol(@Param("symbol") String symbol);

    String selectLatestHedgeLogNetExposureUsdBySymbol(@Param("symbol") String symbol);

    String selectLatestHedgeLogThresholdUsdBySymbol(@Param("symbol") String symbol);

    String selectLatestHedgeLogMarkPriceBySymbol(@Param("symbol") String symbol);

    int deleteRiskExposureBySymbol(@Param("symbol") String symbol);

    int updateRiskExposureQuantities(@Param("symbol") String symbol,
                                     @Param("totalLongQty") BigDecimal totalLongQty,
                                     @Param("totalShortQty") BigDecimal totalShortQty);

    int updateRiskConfigHedgeThresholdUsd(@Param("symbol") String symbol,
                                          @Param("hedgeThresholdUsd") BigDecimal hedgeThresholdUsd);
}
