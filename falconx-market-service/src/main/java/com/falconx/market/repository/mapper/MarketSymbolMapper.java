package com.falconx.market.repository.mapper;

import com.falconx.market.repository.mapper.record.MarketSymbolRecord;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 市场品种 MyBatis Mapper。
 */
@Mapper
public interface MarketSymbolMapper {

    /**
     * 查询全部可交易品种。
     *
     * @return 品种记录列表
     */
    List<MarketSymbolRecord> selectAllTradingSymbols();

    /**
     * 按 symbol 查询品种。
     *
     * @param symbol 品种代码
     * @return 品种记录
     */
    MarketSymbolRecord selectBySymbol(@Param("symbol") String symbol);

    /**
     * 只追加不存在的 symbol。
     *
     * @param records 待写入记录
     * @return 实际插入行数
     */
    int insertIgnoreSymbols(@Param("records") List<MarketSymbolRecord> records);
}
