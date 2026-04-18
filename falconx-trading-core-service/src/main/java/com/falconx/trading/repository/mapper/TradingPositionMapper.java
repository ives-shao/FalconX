package com.falconx.trading.repository.mapper;

import com.falconx.trading.repository.mapper.record.TradingPositionRecord;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 交易持仓 MyBatis Mapper。
 *
 * <p>该 Mapper 负责 `t_position` 的 SQL 声明。
 */
@Mapper
public interface TradingPositionMapper {

    /**
     * 插入持仓记录。
     *
     * @param record 持仓记录
     * @return 影响行数
     */
    int insertTradingPosition(TradingPositionRecord record);

    /**
     * 更新持仓记录。
     *
     * @param record 持仓记录
     * @return 影响行数
     */
    int updateTradingPosition(TradingPositionRecord record);

    /**
     * 按开仓订单查询持仓。
     *
     * @param openingOrderId 开仓订单 ID
     * @return 持仓记录
     */
    TradingPositionRecord selectByOpeningOrderId(@Param("openingOrderId") Long openingOrderId);

    /**
     * 按用户查询 OPEN 持仓。
     *
     * @param userId 用户 ID
     * @return OPEN 持仓记录
     */
    List<TradingPositionRecord> selectOpenByUserId(@Param("userId") Long userId);

    /**
     * 查询全部 OPEN 持仓。
     *
     * @return OPEN 持仓记录
     */
    List<TradingPositionRecord> selectAllOpenPositions();
}
