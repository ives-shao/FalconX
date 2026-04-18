package com.falconx.market;

import com.falconx.market.support.MarketTestDatabaseInitializer;
import com.falconx.market.support.MarketMybatisTestSupportConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/**
 * market-service 最小上下文测试。
 *
 * <p>该测试用于确保市场服务在接入真实 MySQL、Redis 和 ClickHouse 后，
 * 仍然可以稳定完成基础装配。
 */
@ActiveProfiles("stage5")
@ContextConfiguration(initializers = MarketTestDatabaseInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(
        classes = {
                MarketServiceApplication.class,
                MarketMybatisTestSupportConfiguration.class
        },
        properties = {
                "spring.datasource.url=jdbc:mysql://localhost:3306/falconx_market_it?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                "spring.datasource.username=root",
                "spring.datasource.password=root",
                "spring.data.redis.host=localhost",
                "spring.data.redis.port=6379",
                "falconx.market.analytics.jdbc-url=jdbc:clickhouse://localhost:8123/falconx_market_analytics",
                "falconx.market.analytics.username=default",
                "falconx.market.analytics.password="
        }
)
class MarketServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
