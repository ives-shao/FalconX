package com.falconx.market.analytics.mapper;

import com.falconx.market.analytics.mapper.record.MarketKlineRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * ClickHouse K 线 MyBatis Mapper。
 *
 * <p>该 Mapper 负责 `falconx_market_analytics.kline` 的 SQL 声明。
 */
@Mapper
public interface MarketKlineMapper {

    int insertKline(MarketKlineRecord record);
}
