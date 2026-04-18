package com.falconx.trading.repository;

import com.falconx.trading.config.TradingCoreServiceProperties;
import com.falconx.trading.entity.TradingQuoteSnapshot;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * 交易内部最新价格快照 Repository 的 Redis 实现。
 *
 * <p>该实现负责把高频价格快照落到 Redis，
 * 让同步下单和报价驱动引擎共享统一的最新标记价视图。
 */
@Repository
public class RedisTradingQuoteSnapshotRepository implements TradingQuoteSnapshotRepository {

    private final StringRedisTemplate stringRedisTemplate;
    private final Duration maxQuoteAge;

    public RedisTradingQuoteSnapshotRepository(StringRedisTemplate stringRedisTemplate,
                                              TradingCoreServiceProperties properties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.maxQuoteAge = properties.getStale().getMaxAge();
    }

    @Override
    public TradingQuoteSnapshot save(TradingQuoteSnapshot snapshot) {
        String key = key(snapshot.symbol());
        stringRedisTemplate.opsForHash().put(key, "bid", snapshot.bid().toPlainString());
        stringRedisTemplate.opsForHash().put(key, "ask", snapshot.ask().toPlainString());
        stringRedisTemplate.opsForHash().put(key, "mark", snapshot.mark().toPlainString());
        stringRedisTemplate.opsForHash().put(key, "ts", String.valueOf(snapshot.ts().toInstant().toEpochMilli()));
        stringRedisTemplate.opsForHash().put(key, "source", snapshot.source());
        stringRedisTemplate.opsForHash().put(key, "stale", String.valueOf(snapshot.stale()));
        return snapshot;
    }

    @Override
    public Optional<TradingQuoteSnapshot> findBySymbol(String symbol) {
        Map<Object, Object> values = stringRedisTemplate.opsForHash().entries(key(symbol));
        if (values == null || values.isEmpty()) {
            return Optional.empty();
        }
        OffsetDateTime quoteTimestamp = OffsetDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(Long.parseLong((String) values.get("ts"))),
                ZoneOffset.UTC
        );
        boolean stale = quoteTimestamp.plus(maxQuoteAge).isBefore(OffsetDateTime.now(ZoneOffset.UTC));
        return Optional.of(new TradingQuoteSnapshot(
                symbol,
                new BigDecimal((String) values.get("bid")),
                new BigDecimal((String) values.get("ask")),
                new BigDecimal((String) values.get("mark")),
                quoteTimestamp,
                (String) values.get("source"),
                stale
        ));
    }

    private String key(String symbol) {
        return "falconx:trading:quote:snapshot:" + symbol;
    }
}
