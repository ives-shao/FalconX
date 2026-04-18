package com.falconx.trading.repository.mapper;

import com.falconx.trading.repository.mapper.record.TradingLedgerRecord;
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
}
