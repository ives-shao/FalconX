package com.falconx.trading.repository;

import com.falconx.trading.entity.TradingRiskConfig;
import com.falconx.trading.repository.mapper.TradingRiskConfigMapper;
import com.falconx.trading.repository.mapper.record.TradingRiskConfigRecord;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 风险阈值配置 Repository 的 MyBatis 实现。
 *
 * <p>该实现只读取 FX-026 当前所需的 `hedge_threshold_usd`，
 * 不改动其他风控参数的既有使用口径。
 */
@Repository
public class MybatisTradingRiskConfigRepository implements TradingRiskConfigRepository {

    private final TradingRiskConfigMapper tradingRiskConfigMapper;

    public MybatisTradingRiskConfigRepository(TradingRiskConfigMapper tradingRiskConfigMapper) {
        this.tradingRiskConfigMapper = tradingRiskConfigMapper;
    }

    @Override
    public Optional<TradingRiskConfig> findBySymbol(String symbol) {
        return Optional.ofNullable(toDomain(tradingRiskConfigMapper.selectBySymbol(symbol)));
    }

    private TradingRiskConfig toDomain(TradingRiskConfigRecord record) {
        if (record == null) {
            return null;
        }
        return new TradingRiskConfig(
                record.id(),
                record.symbol(),
                record.maxPositionPerUser(),
                record.maxPositionTotal(),
                record.maintenanceMarginRate(),
                record.maxLeverage(),
                record.hedgeThresholdUsd(),
                TradingMybatisSupport.toOffsetDateTime(record.createdAt()),
                TradingMybatisSupport.toOffsetDateTime(record.updatedAt())
        );
    }
}
