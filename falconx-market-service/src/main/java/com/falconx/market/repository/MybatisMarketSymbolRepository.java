package com.falconx.market.repository;

import com.falconx.market.entity.MarketSymbol;
import com.falconx.market.repository.mapper.MarketSymbolMapper;
import com.falconx.market.repository.mapper.record.MarketSymbolRecord;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 市场品种仓储的 MyBatis 实现。
 *
 * <p>该实现负责 `t_symbol` 记录与领域对象之间的转换。
 */
@Repository
public class MybatisMarketSymbolRepository implements MarketSymbolRepository {

    private final MarketSymbolMapper marketSymbolMapper;

    public MybatisMarketSymbolRepository(MarketSymbolMapper marketSymbolMapper) {
        this.marketSymbolMapper = marketSymbolMapper;
    }

    @Override
    public List<MarketSymbol> findAllTradingSymbols() {
        return marketSymbolMapper.selectAllTradingSymbols().stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<MarketSymbol> findBySymbol(String symbol) {
        return Optional.ofNullable(toDomain(marketSymbolMapper.selectBySymbol(symbol)));
    }

    @Override
    public int appendIfAbsent(List<MarketSymbol> symbols) {
        List<MarketSymbolRecord> records = symbols == null ? List.of() : symbols.stream()
                .map(this::toRecord)
                .toList();
        if (records.isEmpty()) {
            return 0;
        }
        return marketSymbolMapper.insertIgnoreSymbols(records);
    }

    private MarketSymbol toDomain(MarketSymbolRecord record) {
        if (record == null) {
            return null;
        }
        return new MarketSymbol(
                record.id(),
                record.symbol(),
                record.category(),
                record.marketCode(),
                record.baseCurrency(),
                record.quoteCurrency(),
                record.pricePrecision(),
                record.qtyPrecision(),
                record.minQty(),
                record.maxQty(),
                record.minNotional(),
                record.maxLeverage(),
                record.takerFeeRate(),
                record.spread(),
                record.status()
        );
    }

    private MarketSymbolRecord toRecord(MarketSymbol symbol) {
        return new MarketSymbolRecord(
                symbol.id(),
                symbol.symbol(),
                symbol.category(),
                symbol.marketCode(),
                symbol.baseCurrency(),
                symbol.quoteCurrency(),
                symbol.pricePrecision(),
                symbol.qtyPrecision(),
                symbol.minQty(),
                symbol.maxQty(),
                symbol.minNotional(),
                symbol.maxLeverage(),
                symbol.takerFeeRate(),
                symbol.spread(),
                symbol.status()
        );
    }
}
