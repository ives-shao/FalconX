package com.falconx.trading.repository.mapper;

import com.falconx.trading.repository.mapper.record.TradingLedgerRecord;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 交易账本 MyBatis Mapper。
 *
 * <p>该 Mapper 负责 `t_ledger` 的 SQL 声明。
 */
@Mapper
public interface TradingLedgerMapper {

    /**
     * 插入账本流水。
     *
     * @param record 账本记录
     * @return 影响行数
     */
    int insertTradingLedger(TradingLedgerRecord record);

    /**
     * 查询用户全部账本流水。
     *
     * @param userId 用户 ID
     * @return 账本记录列表
     */
    List<TradingLedgerRecord> selectByUserId(@Param("userId") Long userId);

    /**
     * 分页查询用户账本流水。
     *
     * @param userId 用户 ID
     * @param offset 偏移量
     * @param limit 本页条数
     * @return 账本记录列表
     */
    List<TradingLedgerRecord> selectByUserIdPaginated(@Param("userId") Long userId,
                                                      @Param("offset") int offset,
                                                      @Param("limit") int limit);

    /**
     * 统计指定幂等键的账本记录数量。
     *
     * @param userId 用户 ID
     * @param idempotencyKey 账务幂等键
     * @return 命中数量
     */
    int countByUserIdAndIdempotencyKey(@Param("userId") Long userId,
                                       @Param("idempotencyKey") String idempotencyKey);

    /**
     * 查询某个持仓最近一次 `Swap` 结算时间。
     *
     * @param userId 用户 ID
     * @param positionId 持仓 ID
     * @return 最近一次账本时间
     */
    LocalDateTime selectLatestSwapSettlementAt(@Param("userId") Long userId,
                                               @Param("positionId") Long positionId);
}
