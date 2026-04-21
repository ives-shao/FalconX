package com.falconx.market.config;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * market-service 配置属性。
 *
 * <p>当前配置类用于冻结 Tiingo、Redis、ClickHouse、Kafka 相关的最小参数形态，
 * 让 Stage 2A 的代码骨架拥有统一配置入口。当前阶段不直接接入真实客户端，
 * 但后续实现必须沿用这些属性结构，避免实现和文档再次分叉。
 */
@ConfigurationProperties(prefix = "falconx.market")
public class MarketServiceProperties {

    private final Tiingo tiingo = new Tiingo();
    private final Redis redis = new Redis();
    private final Analytics analytics = new Analytics();
    private final Kafka kafka = new Kafka();
    private final Stale stale = new Stale();
    private final Kline kline = new Kline();

    public Tiingo getTiingo() {
        return tiingo;
    }

    public Redis getRedis() {
        return redis;
    }

    public Analytics getAnalytics() {
        return analytics;
    }

    public Kafka getKafka() {
        return kafka;
    }

    public Stale getStale() {
        return stale;
    }

    public Kline getKline() {
        return kline;
    }

    /**
     * Tiingo 外部报价源配置。
     */
    public static class Tiingo {
        private boolean enabled = true;
        private URI websocketUrl = URI.create("wss://api.tiingo.com/fx");
        private String apiKey = "CHANGE_ME";
        private Duration connectTimeout = Duration.ofSeconds(10);
        private Duration reconnectInterval = Duration.ofSeconds(5);
        private String trustStoreLocation;
        private String trustStorePassword;
        private String trustStoreType = "PKCS12";
        private String symbolWhitelistRefreshCron = "0 */1 * * * *";
        private String symbolWhitelistRefreshZone = "UTC";
        private final CryptoSymbolImport cryptoSymbolImport = new CryptoSymbolImport();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public URI getWebsocketUrl() {
            return websocketUrl;
        }

        public void setWebsocketUrl(URI websocketUrl) {
            this.websocketUrl = websocketUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getReconnectInterval() {
            return reconnectInterval;
        }

        public void setReconnectInterval(Duration reconnectInterval) {
            this.reconnectInterval = reconnectInterval;
        }

        public String getTrustStoreLocation() {
            return trustStoreLocation;
        }

        public void setTrustStoreLocation(String trustStoreLocation) {
            this.trustStoreLocation = trustStoreLocation;
        }

        public String getTrustStorePassword() {
            return trustStorePassword;
        }

        public void setTrustStorePassword(String trustStorePassword) {
            this.trustStorePassword = trustStorePassword;
        }

        public String getTrustStoreType() {
            return trustStoreType;
        }

        public void setTrustStoreType(String trustStoreType) {
            this.trustStoreType = trustStoreType;
        }

        public String getSymbolWhitelistRefreshCron() {
            return symbolWhitelistRefreshCron;
        }

        public void setSymbolWhitelistRefreshCron(String symbolWhitelistRefreshCron) {
            this.symbolWhitelistRefreshCron = symbolWhitelistRefreshCron;
        }

        public String getSymbolWhitelistRefreshZone() {
            return symbolWhitelistRefreshZone;
        }

        public void setSymbolWhitelistRefreshZone(String symbolWhitelistRefreshZone) {
            this.symbolWhitelistRefreshZone = symbolWhitelistRefreshZone;
        }

        public CryptoSymbolImport getCryptoSymbolImport() {
            return cryptoSymbolImport;
        }
    }

    /**
     * Tiingo crypto 源的一次性 symbol 采样导入配置。
     *
     * <p>Tiingo `crypto` 当前真实流量主要是成交流（`data[0]=T`），并不直接满足
     * 平台标准报价对象要求的 `bid/ask` 语义。因此这里把它约束为“symbol 发现能力”：
     *
     * <ul>
     *   <li>默认关闭，不影响现有 `fx` 报价主链路</li>
     *   <li>按固定窗口采样</li>
     *   <li>只接收允许的中心化交易所成交流</li>
     *   <li>追加到 `t_symbol` 时默认写成 `status=2 suspended`，避免把未经人工审核的 symbol
     *       直接放入可交易白名单</li>
     * </ul>
     */
    public static class CryptoSymbolImport {
        private boolean enabled;
        private URI websocketUrl = URI.create("wss://api.tiingo.com/crypto");
        private Duration connectTimeout = Duration.ofSeconds(10);
        private Duration sampleWindow = Duration.ofSeconds(10);
        private List<String> allowedExchanges = new ArrayList<>(List.of(
                "binance",
                "bitfinex",
                "bitstamp",
                "bybit",
                "cryptodotcom",
                "gdax",
                "gemini",
                "huobi",
                "kraken",
                "kucoin",
                "mexc",
                "okex",
                "poloniex"
        ));
        private List<String> quoteCurrencies = new ArrayList<>(List.of(
                "USDT",
                "USDC",
                "USD",
                "EUR"
        ));
        private int minBaseLength = 2;
        private int maxBaseLength = 12;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public URI getWebsocketUrl() {
            return websocketUrl;
        }

        public void setWebsocketUrl(URI websocketUrl) {
            this.websocketUrl = websocketUrl;
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getSampleWindow() {
            return sampleWindow;
        }

        public void setSampleWindow(Duration sampleWindow) {
            this.sampleWindow = sampleWindow;
        }

        public List<String> getAllowedExchanges() {
            return allowedExchanges;
        }

        public void setAllowedExchanges(List<String> allowedExchanges) {
            this.allowedExchanges = allowedExchanges == null ? new ArrayList<>() : new ArrayList<>(allowedExchanges);
        }

        public List<String> getQuoteCurrencies() {
            return quoteCurrencies;
        }

        public void setQuoteCurrencies(List<String> quoteCurrencies) {
            this.quoteCurrencies = quoteCurrencies == null ? new ArrayList<>() : new ArrayList<>(quoteCurrencies);
        }

        public int getMinBaseLength() {
            return minBaseLength;
        }

        public void setMinBaseLength(int minBaseLength) {
            this.minBaseLength = minBaseLength;
        }

        public int getMaxBaseLength() {
            return maxBaseLength;
        }

        public void setMaxBaseLength(int maxBaseLength) {
            this.maxBaseLength = maxBaseLength;
        }
    }

    /**
     * Redis 行情缓存约束。
     */
    public static class Redis {
        private Duration quoteTtl = Duration.ofSeconds(10);
        private Duration tradingScheduleTtl = Duration.ofHours(25);
        private String tradingScheduleRefreshCron = "0 0 0 * * *";
        private String tradingScheduleRefreshZone = "UTC";
        private Duration swapRateTtl = Duration.ofHours(25);
        private String swapRateRefreshCron = "0 0 0 * * *";
        private String swapRateRefreshZone = "UTC";

        public Duration getQuoteTtl() {
            return quoteTtl;
        }

        public void setQuoteTtl(Duration quoteTtl) {
            this.quoteTtl = quoteTtl;
        }

        public Duration getTradingScheduleTtl() {
            return tradingScheduleTtl;
        }

        public void setTradingScheduleTtl(Duration tradingScheduleTtl) {
            this.tradingScheduleTtl = tradingScheduleTtl;
        }

        public String getTradingScheduleRefreshCron() {
            return tradingScheduleRefreshCron;
        }

        public void setTradingScheduleRefreshCron(String tradingScheduleRefreshCron) {
            this.tradingScheduleRefreshCron = tradingScheduleRefreshCron;
        }

        public String getTradingScheduleRefreshZone() {
            return tradingScheduleRefreshZone;
        }

        public void setTradingScheduleRefreshZone(String tradingScheduleRefreshZone) {
            this.tradingScheduleRefreshZone = tradingScheduleRefreshZone;
        }

        public Duration getSwapRateTtl() {
            return swapRateTtl;
        }

        public void setSwapRateTtl(Duration swapRateTtl) {
            this.swapRateTtl = swapRateTtl;
        }

        public String getSwapRateRefreshCron() {
            return swapRateRefreshCron;
        }

        public void setSwapRateRefreshCron(String swapRateRefreshCron) {
            this.swapRateRefreshCron = swapRateRefreshCron;
        }

        public String getSwapRateRefreshZone() {
            return swapRateRefreshZone;
        }

        public void setSwapRateRefreshZone(String swapRateRefreshZone) {
            this.swapRateRefreshZone = swapRateRefreshZone;
        }
    }

    /**
     * ClickHouse 分析存储相关配置。
     */
    public static class Analytics {
        private String database = "falconx_market_analytics";
        private int quoteBatchSize = 200;
        private Duration quoteFlushInterval = Duration.ofMillis(500);
        private String jdbcUrl = "jdbc:clickhouse://localhost:8123/falconx_market_analytics";
        private String username = "default";
        private String password;

        public String getDatabase() {
            return database;
        }

        public void setDatabase(String database) {
            this.database = database;
        }

        public int getQuoteBatchSize() {
            return quoteBatchSize;
        }

        public void setQuoteBatchSize(int quoteBatchSize) {
            this.quoteBatchSize = quoteBatchSize;
        }

        public Duration getQuoteFlushInterval() {
            return quoteFlushInterval;
        }

        public void setQuoteFlushInterval(Duration quoteFlushInterval) {
            this.quoteFlushInterval = quoteFlushInterval;
        }

        public String getJdbcUrl() {
            return jdbcUrl;
        }

        public void setJdbcUrl(String jdbcUrl) {
            this.jdbcUrl = jdbcUrl;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    /**
     * Kafka 主题配置。
     */
    public static class Kafka {
        private String priceTickTopic = "falconx.market.price.tick";
        private String klineUpdateTopic = "falconx.market.kline.update";

        public String getPriceTickTopic() {
            return priceTickTopic;
        }

        public void setPriceTickTopic(String priceTickTopic) {
            this.priceTickTopic = priceTickTopic;
        }

        public String getKlineUpdateTopic() {
            return klineUpdateTopic;
        }

        public void setKlineUpdateTopic(String klineUpdateTopic) {
            this.klineUpdateTopic = klineUpdateTopic;
        }
    }

    /**
     * stale 判定参数。
     */
    public static class Stale {
        private Duration maxAge = Duration.ofSeconds(5);

        public Duration getMaxAge() {
            return maxAge;
        }

        public void setMaxAge(Duration maxAge) {
            this.maxAge = maxAge;
        }
    }

    /**
     * K 线聚合参数。
     */
    public static class Kline {
        private List<String> intervals = new ArrayList<>(List.of("1m", "5m", "15m", "1h", "4h", "1d"));
        private String timezone = "UTC";

        public List<String> getIntervals() {
            return intervals;
        }

        public void setIntervals(List<String> intervals) {
            this.intervals = intervals;
        }

        public String getTimezone() {
            return timezone;
        }

        public void setTimezone(String timezone) {
            this.timezone = timezone;
        }
    }
}
