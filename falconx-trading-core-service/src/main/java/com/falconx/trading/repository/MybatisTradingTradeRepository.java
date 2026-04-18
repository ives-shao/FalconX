package com.falconx.trading.repository;

import com.falconx.infrastructure.id.IdGenerator;
import com.falconx.trading.entity.TradingTrade;
import com.falconx.trading.repository.mapper.TradingTradeMapper;
import com.falconx.trading.repository.mapper.record.TradingTradeRecord;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 交易成交 Repository 的 MyBatis 实现。
 *
 * <p>该实现负责成交领域对象与 `t_trade` 记录之间的转换。
 */
@Repository
public class MybatisTradingTradeRepository implements TradingTradeRepository {

    private final TradingTradeMapper tradingTradeMapper;
    private final IdGenerator idGenerator;

    public MybatisTradingTradeRepository(TradingTradeMapper tradingTradeMapper, IdGenerator idGenerator) {
        this.tradingTradeMapper = tradingTradeMapper;
        this.idGenerator = idGenerator;
    }

    @Override
    public TradingTrade save(TradingTrade trade) {
        if (trade.tradeId() == null) {
            long id = idGenerator.nextId();
            TradingTrade persisted = new TradingTrade(
                    id,
                    trade.orderId(),
                    trade.positionId(),
                    trade.userId(),
                    trade.symbol(),
                    trade.side(),
                    trade.quantity(),
                    trade.price(),
                    trade.fee(),
                    trade.realizedPnl(),
                    trade.tradedAt() == null ? OffsetDateTime.now() : trade.tradedAt()
            );
            tradingTradeMapper.insertTradingTrade(toRecord(persisted));
            return persisted;
        }
        return trade;
    }

    @Override
    public Optional<TradingTrade> findByOrderId(Long orderId) {
        return Optional.ofNullable(toDomain(tradingTradeMapper.selectByOrderId(orderId)));
    }

    private TradingTradeRecord toRecord(TradingTrade trade) {
        return new TradingTradeRecord(
                trade.tradeId(),
                trade.orderId(),
                trade.positionId(),
                trade.userId(),
                trade.symbol(),
                TradingMybatisSupport.toSideCode(trade.side()),
                trade.quantity(),
                trade.price(),
                trade.fee(),
                trade.realizedPnl(),
                TradingMybatisSupport.toLocalDateTime(trade.tradedAt())
        );
    }

    private TradingTrade toDomain(TradingTradeRecord record) {
        if (record == null) {
            return null;
        }
        return new TradingTrade(
                record.id(),
                record.orderId(),
                record.positionId(),
                record.userId(),
                record.symbol(),
                TradingMybatisSupport.toSide(record.sideCode()),
                record.quantity(),
                record.price(),
                record.fee(),
                record.realizedPnl(),
                TradingMybatisSupport.toOffsetDateTime(record.tradedAt())
        );
    }
}
