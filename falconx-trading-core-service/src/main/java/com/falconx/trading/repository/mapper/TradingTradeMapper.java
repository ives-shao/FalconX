package com.falconx.trading.repository.mapper;

import com.falconx.trading.repository.mapper.record.TradingTradeRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 交易成交 MyBatis Mapper。
 *
 * <p>该 Mapper 负责 `t_trade` 的 SQL 声明。
 */
@Mapper
public interface TradingTradeMapper {

    /**
     * 插入成交记录。
     *
     * @param record 成交记录
     * @return 影响行数
     */
    int insertTradingTrade(TradingTradeRecord record);

    /**
     * 按订单 ID 查询成交。
     *
     * @param orderId 订单主键
     * @return 成交记录
     */
    TradingTradeRecord selectByOrderId(@Param("orderId") Long orderId);
}
