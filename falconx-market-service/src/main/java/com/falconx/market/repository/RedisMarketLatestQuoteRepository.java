package com.falconx.market.repository;

import com.falconx.market.entity.StandardQuote;
import com.falconx.market.config.MarketServiceProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * 最新报价 Redis 读模型实现。
 *
 * <p>该实现把 market-service 对北向查询暴露的“最新价读模型”切换到 Redis，
 * 与数据库设计里“最新价格以 Redis 为准”的规则保持一致。
 */
@Repository
@Profile("!stub")
public class RedisMarketLatestQuoteRepository implements MarketLatestQuoteRepository {

    private final StringRedisTemplate stringRedisTemplate;
    private final Duration maxQuoteAge;

    public RedisMarketLatestQuoteRepository(StringRedisTemplate stringRedisTemplate,
                                            MarketServiceProperties properties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.maxQuoteAge = properties.getStale().getMaxAge();
    }

    @Override
    public void save(StandardQuote quote) {
        String key = key(quote.symbol());
        stringRedisTemplate.opsForHash().put(key, "bid", quote.bid().toPlainString());
        stringRedisTemplate.opsForHash().put(key, "ask", quote.ask().toPlainString());
        stringRedisTemplate.opsForHash().put(key, "mid", quote.mid().toPlainString());
        stringRedisTemplate.opsForHash().put(key, "mark", quote.mark().toPlainString());
        stringRedisTemplate.opsForHash().put(key, "ts", String.valueOf(quote.ts().toInstant().toEpochMilli()));
        stringRedisTemplate.opsForHash().put(key, "source", quote.source());
        stringRedisTemplate.opsForHash().put(key, "stale", String.valueOf(quote.stale()));
    }

    @Override
    public Optional<StandardQuote> findBySymbol(String symbol) {
        Map<Object, Object> values = stringRedisTemplate.opsForHash().entries(key(symbol));
        if (values == null || values.isEmpty()) {
            return Optional.empty();
        }
        OffsetDateTime quoteTimestamp = OffsetDateTime.ofInstant(
                Instant.ofEpochMilli(Long.parseLong((String) values.get("ts"))),
                ZoneOffset.UTC
        );
        boolean stale = quoteTimestamp.plus(maxQuoteAge).isBefore(OffsetDateTime.now(ZoneOffset.UTC));
        return Optional.of(new StandardQuote(
                symbol,
                new BigDecimal((String) values.get("bid")),
                new BigDecimal((String) values.get("ask")),
                new BigDecimal((String) values.get("mid")),
                new BigDecimal((String) values.get("mark")),
                quoteTimestamp,
                (String) values.get("source"),
                stale
        ));
    }

    private String key(String symbol) {
        return "falconx:market:price:" + symbol;
    }
}
