package com.falconx.market.application;

import com.falconx.market.MarketServiceApplication;
import com.falconx.market.repository.mapper.test.MarketSymbolTestSupportMapper;
import com.falconx.market.entity.MarketSymbol;
import com.falconx.market.provider.TiingoQuoteProvider;
import com.falconx.market.provider.TiingoRawQuote;
import com.falconx.market.repository.MarketSymbolRepository;
import com.falconx.market.support.MarketMybatisTestSupportConfiguration;
import com.falconx.market.support.MarketTestDatabaseInitializer;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/**
 * 市场数据启动器集成测试。
 *
 * <p>该测试不再手写一组演示用 `MarketSymbol` 列表，而是直接依赖 market owner 的真实
 * `MyBatis + XML + Flyway` 初始化结果，验证启动器拿到的本地放行白名单与数据库中的启用品种完全一致。
 * 这样后续扩展 `t_symbol` 种子或生产库配置时，测试仍然围绕真实 owner 数据运行，不会再出现
 * “测试用产品清单”和“数据库实际产品清单”分叉的问题。
 */
@ActiveProfiles("stage5")
@ContextConfiguration(initializers = MarketTestDatabaseInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(
        classes = {
                MarketServiceApplication.class,
                MarketMybatisTestSupportConfiguration.class,
                MarketFeedBootstrapRunnerTests.RecordingTiingoProviderConfiguration.class
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
class MarketFeedBootstrapRunnerTests {

    @Autowired
    private MarketSymbolRepository marketSymbolRepository;

    @Autowired
    private RecordingTiingoQuoteProvider recordingTiingoQuoteProvider;

    @Autowired
    private MarketFeedBootstrapRunner marketFeedBootstrapRunner;

    @Autowired
    private MarketSymbolTestSupportMapper marketSymbolTestSupportMapper;

    @org.junit.jupiter.api.BeforeEach
    void resetSeedSymbols() {
        marketSymbolTestSupportMapper.updateSymbolStatus("XAUUSD", 1);
    }

    @Test
    void shouldUseAllEnabledSymbolsFromMarketOwnerRepository() {
        List<String> expectedSymbols = marketSymbolRepository.findAllTradingSymbols().stream()
                .map(MarketSymbol::symbol)
                .toList();

        Assertions.assertEquals(expectedSymbols, recordingTiingoQuoteProvider.startedWithSymbols());
    }

    @Test
    void shouldRefreshWhitelistWhenOwnerSymbolStatusChanges() {
        marketSymbolTestSupportMapper.updateSymbolStatus("XAUUSD", 2);

        marketFeedBootstrapRunner.refreshTiingoSymbolWhitelist();

        List<String> expectedSymbols = marketSymbolRepository.findAllTradingSymbols().stream()
                .map(MarketSymbol::symbol)
                .toList();

        Assertions.assertEquals(expectedSymbols, recordingTiingoQuoteProvider.lastRefreshedSymbols());
        Assertions.assertFalse(recordingTiingoQuoteProvider.lastRefreshedSymbols().contains("XAUUSD"));
    }

    /**
     * 测试专用 Tiingo Provider 配置。
     *
     * <p>这里使用 `@Primary` 覆盖运行时 Provider，让应用启动阶段仍然完整执行
     * `MarketFeedBootstrapRunner -> resolveTiingoSymbols -> TiingoQuoteProvider.start(...)`，
     * 但不会真的向外部 WebSocket 建连。测试只关心启动器最终传给 Provider 的 symbol 白名单。
     */
    @TestConfiguration
    static class RecordingTiingoProviderConfiguration {

        @Bean
        @Primary
        RecordingTiingoQuoteProvider recordingTiingoQuoteProvider() {
            return new RecordingTiingoQuoteProvider();
        }
    }

    /**
     * 录制型 Tiingo Provider。
     *
     * <p>该实现只记录启动器传入的 symbol 列表，不执行任何网络连接。
     * 这样可以在不引入内存仓储或硬编码产品清单的前提下，验证启动器是否正确依赖了 owner 数据源。
     */
    static final class RecordingTiingoQuoteProvider implements TiingoQuoteProvider {

        private volatile List<String> startedWithSymbols = List.of();
        private volatile List<String> lastRefreshedSymbols = List.of();

        @Override
        public void start(List<String> symbols, Consumer<TiingoRawQuote> quoteConsumer) {
            this.startedWithSymbols = List.copyOf(symbols);
        }

        @Override
        public void refreshSymbols(List<String> symbols) {
            this.lastRefreshedSymbols = List.copyOf(symbols);
        }

        List<String> startedWithSymbols() {
            return startedWithSymbols;
        }

        List<String> lastRefreshedSymbols() {
            return lastRefreshedSymbols;
        }
    }
}
