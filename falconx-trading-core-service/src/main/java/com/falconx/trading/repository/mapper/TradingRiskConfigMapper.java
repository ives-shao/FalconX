package com.falconx.trading.repository.mapper;

import com.falconx.trading.repository.mapper.record.TradingRiskConfigRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 风险阈值配置 MyBatis Mapper。
 */
@Mapper
public interface TradingRiskConfigMapper {

    /**
     * 按 symbol 查询 FX-026 所需的对冲阈值。
     *
     * @param symbol 交易品种
     * @return 风险配置记录
     */
    TradingRiskConfigRecord selectBySymbol(@Param("symbol") String symbol);
}
