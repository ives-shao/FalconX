package com.falconx.market.analytics.mapper;

import com.falconx.market.analytics.mapper.record.MarketQuoteTickRecord;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/**
 * ClickHouse 报价 Tick MyBatis Mapper。
 *
 * <p>该 Mapper 负责 `falconx_market_analytics.quote_tick` 的 SQL 声明。
 */
@Mapper
public interface MarketQuoteTickMapper {

    int insertQuoteTick(MarketQuoteTickRecord record);

    int insertQuoteTicks(List<MarketQuoteTickRecord> records);
}
