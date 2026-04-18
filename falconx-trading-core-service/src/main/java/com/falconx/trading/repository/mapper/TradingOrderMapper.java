package com.falconx.trading.repository.mapper;

import com.falconx.trading.repository.mapper.record.TradingOrderRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 交易订单 MyBatis Mapper。
 *
 * <p>该 Mapper 负责 `t_order` 的 SQL 声明。
 */
@Mapper
public interface TradingOrderMapper {

    /**
     * 插入订单记录。
     *
     * @param record 订单记录
     * @return 影响行数
     */
    int insertTradingOrder(TradingOrderRecord record);

    /**
     * 更新订单记录。
     *
     * @param record 订单记录
     * @return 影响行数
     */
    int updateTradingOrder(TradingOrderRecord record);

    /**
     * 按用户和客户端订单号查询订单。
     *
     * @param userId 用户 ID
     * @param clientOrderId 客户端幂等键
     * @return 订单记录
     */
    TradingOrderRecord selectByUserIdAndClientOrderId(@Param("userId") Long userId,
                                                      @Param("clientOrderId") String clientOrderId);
}
