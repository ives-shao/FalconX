package com.falconx.trading.repository;

import com.falconx.trading.entity.TradingOrderSide;
import com.falconx.trading.entity.TradingRiskExposure;
import com.falconx.trading.repository.mapper.TradingRiskExposureMapper;
import com.falconx.trading.repository.mapper.record.TradingRiskExposureRecord;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 品种净敞口 Repository 的 MyBatis 实现。
 *
 * <p>该实现把开仓方向映射为多头或空头增量，
 * 并在同一本地事务里完成 `t_risk_exposure` 更新。
 */
@Repository
public class MybatisTradingRiskExposureRepository implements TradingRiskExposureRepository {

    private final TradingRiskExposureMapper tradingRiskExposureMapper;

    public MybatisTradingRiskExposureRepository(TradingRiskExposureMapper tradingRiskExposureMapper) {
        this.tradingRiskExposureMapper = tradingRiskExposureMapper;
    }

    @Override
    public void applyOpenPosition(String symbol,
                                  TradingOrderSide side,
                                  BigDecimal quantity,
                                  BigDecimal markPrice,
                                  OffsetDateTime occurredAt) {
        if (side == TradingOrderSide.BUY) {
            tradingRiskExposureMapper.applyLongDelta(
                    symbol,
                    quantity,
                    markPrice,
                    TradingMybatisSupport.toLocalDateTime(occurredAt)
            );
            return;
        }
        tradingRiskExposureMapper.applyShortDelta(
                symbol,
                quantity,
                markPrice,
                TradingMybatisSupport.toLocalDateTime(occurredAt)
        );
    }

    @Override
    public void applyClosePosition(String symbol,
                                   TradingOrderSide side,
                                   BigDecimal quantity,
                                   BigDecimal markPrice,
                                   OffsetDateTime occurredAt) {
        int affectedRows = side == TradingOrderSide.BUY
                ? tradingRiskExposureMapper.reduceLongDelta(
                symbol,
                quantity,
                markPrice,
                TradingMybatisSupport.toLocalDateTime(occurredAt)
        )
                : tradingRiskExposureMapper.reduceShortDelta(
                symbol,
                quantity,
                markPrice,
                TradingMybatisSupport.toLocalDateTime(occurredAt)
        );
        if (affectedRows != 1) {
            throw new IllegalStateException("Risk exposure not found or inconsistent for symbol=" + symbol);
        }
    }

    @Override
    public void refreshNetExposureUsd(String symbol, BigDecimal markPrice, OffsetDateTime occurredAt) {
        tradingRiskExposureMapper.refreshNetExposureUsd(
                symbol,
                markPrice,
                TradingMybatisSupport.toLocalDateTime(occurredAt)
        );
    }

    @Override
    public Optional<TradingRiskExposure> findBySymbol(String symbol) {
        return Optional.ofNullable(toDomain(tradingRiskExposureMapper.selectBySymbol(symbol)));
    }

    private TradingRiskExposure toDomain(TradingRiskExposureRecord record) {
        if (record == null) {
            return null;
        }
        return new TradingRiskExposure(
                record.symbol(),
                record.totalLongQty(),
                record.totalShortQty(),
                record.netExposure(),
                record.netExposureUsd(),
                TradingMybatisSupport.toOffsetDateTime(record.updatedAt())
        );
    }
}
