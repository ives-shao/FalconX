package com.falconx.trading.repository.mapper;

import com.falconx.trading.repository.mapper.record.TradingRiskExposureRecord;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 品种净敞口 MyBatis Mapper。
 *
 * <p>该 Mapper 负责 `t_risk_exposure` 的增量更新和查询。
 */
@Mapper
public interface TradingRiskExposureMapper {

    /**
     * 对多头总量做增量更新。
     *
     * @param symbol 品种
     * @param quantity 增量数量
     * @param updatedAt 更新时间
     * @return 影响行数
     */
    int applyLongDelta(@Param("symbol") String symbol,
                       @Param("quantity") BigDecimal quantity,
                       @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 对空头总量做增量更新。
     *
     * @param symbol 品种
     * @param quantity 增量数量
     * @param updatedAt 更新时间
     * @return 影响行数
     */
    int applyShortDelta(@Param("symbol") String symbol,
                        @Param("quantity") BigDecimal quantity,
                        @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 按品种查询净敞口。
     *
     * @param symbol 品种
     * @return 敞口记录
     */
    TradingRiskExposureRecord selectBySymbol(@Param("symbol") String symbol);
}
