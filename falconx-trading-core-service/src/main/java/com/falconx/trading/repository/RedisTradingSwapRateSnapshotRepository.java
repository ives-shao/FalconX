package com.falconx.trading.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.falconx.trading.service.model.TradingSwapRateSnapshot;
import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * 隔夜利息共享快照的 Redis 读取实现。
 *
 * <p>该实现与 `market-service` 约定统一的 Redis key，
 * 让交易核心直接读取 owner 快照，不跨服务查库。
 *
 * <p>cache miss 策略固定为返回空，
 * 由上游跳过本次结算并在下一轮调度时重试。
 */
@Repository
public class RedisTradingSwapRateSnapshotRepository implements TradingSwapRateSnapshotRepository {

    private static final String KEY_PREFIX = "falconx:market:swap-rate:";
    private static final Duration TEST_SEED_TTL = Duration.ofHours(25);

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public RedisTradingSwapRateSnapshotRepository(StringRedisTemplate stringRedisTemplate,
                                                  ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<TradingSwapRateSnapshot> findBySymbol(String symbol) {
        String payload = stringRedisTemplate.opsForValue().get(key(symbol));
        if (payload == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(payload, TradingSwapRateSnapshot.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to deserialize trading swap rate snapshot", exception);
        }
    }

    /**
     * 仅供集成测试使用：向 Redis 写入隔夜利息共享快照种子数据。
     *
     * @param snapshot 测试快照
     */
    public void saveForTest(TradingSwapRateSnapshot snapshot) {
        try {
            stringRedisTemplate.opsForValue().set(
                    key(snapshot.symbol()),
                    objectMapper.writeValueAsString(snapshot),
                    TEST_SEED_TTL
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize trading swap rate snapshot", exception);
        }
    }

    private String key(String symbol) {
        return KEY_PREFIX + symbol;
    }
}
