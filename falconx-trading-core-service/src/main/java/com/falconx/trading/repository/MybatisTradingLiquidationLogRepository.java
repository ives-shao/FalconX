package com.falconx.trading.repository;

import com.falconx.infrastructure.id.IdGenerator;
import com.falconx.trading.entity.TradingLiquidationLog;
import com.falconx.trading.repository.mapper.TradingLiquidationLogMapper;
import com.falconx.trading.repository.mapper.record.TradingLiquidationLogRecord;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Repository;

/**
 * 强平日志 Repository 的 MyBatis 实现。
 */
@Repository
public class MybatisTradingLiquidationLogRepository implements TradingLiquidationLogRepository {

    private final TradingLiquidationLogMapper tradingLiquidationLogMapper;
    private final IdGenerator idGenerator;

    public MybatisTradingLiquidationLogRepository(TradingLiquidationLogMapper tradingLiquidationLogMapper,
                                                  IdGenerator idGenerator) {
        this.tradingLiquidationLogMapper = tradingLiquidationLogMapper;
        this.idGenerator = idGenerator;
    }

    @Override
    public TradingLiquidationLog save(TradingLiquidationLog liquidationLog) {
        long id = liquidationLog.liquidationLogId() == null ? idGenerator.nextId() : liquidationLog.liquidationLogId();
        TradingLiquidationLog persisted = new TradingLiquidationLog(
                id,
                liquidationLog.userId(),
                liquidationLog.positionId(),
                liquidationLog.symbol(),
                liquidationLog.side(),
                liquidationLog.quantity(),
                liquidationLog.entryPrice(),
                liquidationLog.liquidationPrice(),
                liquidationLog.markPrice(),
                liquidationLog.priceTs(),
                liquidationLog.priceSource(),
                liquidationLog.loss(),
                liquidationLog.fee(),
                liquidationLog.marginReleased(),
                liquidationLog.platformCoveredLoss(),
                liquidationLog.createdAt() == null ? OffsetDateTime.now() : liquidationLog.createdAt()
        );
        tradingLiquidationLogMapper.insertTradingLiquidationLog(toRecord(persisted));
        return persisted;
    }

    private TradingLiquidationLogRecord toRecord(TradingLiquidationLog liquidationLog) {
        return new TradingLiquidationLogRecord(
                liquidationLog.liquidationLogId(),
                liquidationLog.userId(),
                liquidationLog.positionId(),
                liquidationLog.symbol(),
                TradingMybatisSupport.toSideCode(liquidationLog.side()),
                liquidationLog.quantity(),
                liquidationLog.entryPrice(),
                liquidationLog.liquidationPrice(),
                liquidationLog.markPrice(),
                TradingMybatisSupport.toLocalDateTime(liquidationLog.priceTs()),
                liquidationLog.priceSource(),
                liquidationLog.loss(),
                liquidationLog.fee(),
                liquidationLog.marginReleased(),
                liquidationLog.platformCoveredLoss(),
                TradingMybatisSupport.toLocalDateTime(liquidationLog.createdAt())
        );
    }
}
