package com.falconx.trading;

import com.falconx.trading.entity.TradingQuoteSnapshot;
import com.falconx.trading.repository.TradingQuoteSnapshotRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 交易域最新价格 stale 语义集成测试。
 *
 * <p>该测试验证 trading-core-service 读取 Redis 快照时会重新按当前时间计算 stale，
 * 防止过期价格在 Tiingo 断流后仍被误判为可下单价格。
 */
@ActiveProfiles("stage5")
@SpringBootTest(
        classes = TradingCoreServiceApplication.class,
        properties = {
                "spring.datasource.url=jdbc:mysql://localhost:3306/falconx_trading_it?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                "spring.datasource.username=root",
                "spring.datasource.password=root",
                "spring.data.redis.host=localhost",
                "spring.data.redis.port=6379",
                "falconx.trading.stale.max-age=100ms"
        }
)
class TradingQuoteSnapshotStaleIntegrationTests {

    @Autowired
    private TradingQuoteSnapshotRepository tradingQuoteSnapshotRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void clearQuoteSnapshot() {
        stringRedisTemplate.delete("falconx:trading:quote:snapshot:ETHUSDT");
    }

    @Test
    void shouldMarkTradingQuoteStaleWhenReadHappensAfterMaxAge() throws InterruptedException {
        tradingQuoteSnapshotRepository.save(new TradingQuoteSnapshot(
                "ETHUSDT",
                new BigDecimal("1990.00000000"),
                new BigDecimal("2000.00000000"),
                new BigDecimal("1995.00000000"),
                OffsetDateTime.now(),
                "integration-test",
                false
        ));

        TradingQuoteSnapshot freshSnapshot = tradingQuoteSnapshotRepository.findBySymbol("ETHUSDT").orElseThrow();
        Thread.sleep(180L);
        TradingQuoteSnapshot staleSnapshot = tradingQuoteSnapshotRepository.findBySymbol("ETHUSDT").orElseThrow();

        Assertions.assertFalse(freshSnapshot.stale());
        Assertions.assertTrue(staleSnapshot.stale());
    }
}
