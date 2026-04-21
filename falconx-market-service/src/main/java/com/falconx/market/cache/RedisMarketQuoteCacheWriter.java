package com.falconx.market.cache;

import com.falconx.market.config.MarketServiceProperties;
import com.falconx.market.entity.StandardQuote;
import com.falconx.market.repository.MarketLatestQuoteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 最新价缓存真实写入实现。
 *
 * <p>该实现除了写入读模型仓储外，还负责给 Redis key 设置 TTL，
 * 使无更新行情可以按契约自然过期。
 */
@Component
@Profile("!stub")
public class RedisMarketQuoteCacheWriter implements MarketQuoteCacheWriter {

    private static final Logger log = LoggerFactory.getLogger(RedisMarketQuoteCacheWriter.class);

    private final MarketLatestQuoteRepository marketLatestQuoteRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final MarketServiceProperties properties;

    public RedisMarketQuoteCacheWriter(MarketLatestQuoteRepository marketLatestQuoteRepository,
                                       StringRedisTemplate stringRedisTemplate,
                                       MarketServiceProperties properties) {
        this.marketLatestQuoteRepository = marketLatestQuoteRepository;
        this.stringRedisTemplate = stringRedisTemplate;
        this.properties = properties;
    }

    @Override
    public void writeLatestQuote(StandardQuote quote) {
        marketLatestQuoteRepository.save(quote);
        stringRedisTemplate.expire("falconx:market:price:" + quote.symbol(), properties.getRedis().getQuoteTtl());
        log.info("market.redis.written symbol={} quoteTs={} stale={}", quote.symbol(), quote.ts(), quote.stale());
    }
}
