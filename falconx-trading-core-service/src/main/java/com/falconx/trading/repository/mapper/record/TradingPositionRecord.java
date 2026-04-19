package com.falconx.trading.repository.mapper.record;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 交易持仓 MyBatis 记录对象。
 *
 * <p>该记录对象对应 `t_position` 的数据库结构。
 *
 * @param id 主键 ID
 * @param openingOrderId 开仓订单 ID
 * @param userId 用户 ID
 * @param symbol 品种
 * @param sideCode 方向码
 * @param quantity 持仓数量
 * @param entryPrice 开仓均价
 * @param leverage 杠杆
 * @param margin 占用保证金
 * @param marginModeCode 保证金模式码
 * @param liquidationPrice 强平价
 * @param takeProfitPrice 止盈触发价
 * @param stopLossPrice 止损触发价
 * @param closePrice 平仓价
 * @param closeReasonCode 平仓原因码
 * @param realizedPnl 已实现盈亏
 * @param statusCode 持仓状态码
 * @param openedAt 开仓时间
 * @param closedAt 平仓时间
 * @param updatedAt 更新时间
 */
public record TradingPositionRecord(
        Long id,
        Long openingOrderId,
        Long userId,
        String symbol,
        Integer sideCode,
        BigDecimal quantity,
        BigDecimal entryPrice,
        BigDecimal leverage,
        BigDecimal margin,
        Integer marginModeCode,
        BigDecimal liquidationPrice,
        BigDecimal takeProfitPrice,
        BigDecimal stopLossPrice,
        BigDecimal closePrice,
        Integer closeReasonCode,
        BigDecimal realizedPnl,
        Integer statusCode,
        LocalDateTime openedAt,
        LocalDateTime closedAt,
        LocalDateTime updatedAt
) {
}
