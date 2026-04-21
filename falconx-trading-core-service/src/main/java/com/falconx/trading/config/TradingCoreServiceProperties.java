package com.falconx.trading.config;

import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * trading-core-service 配置属性。
 *
 * <p>该配置类用于冻结 Stage 3B 交易核心骨架在本地运行时所需的最小业务参数，
 * 包括结算币种、手续费率与维持保证金率。
 * 品种支持校验不再使用本地静态白名单，而是依赖 market-service 写入的 Redis 交易时间快照，
 * 避免交易核心与 market owner 的产品配置发生分叉。
 */
@ConfigurationProperties(prefix = "falconx.trading")
public class TradingCoreServiceProperties {

    private String settlementToken = "USDT";
    private BigDecimal defaultFeeRate = new BigDecimal("0.0005");
    private BigDecimal maintenanceMarginRate = new BigDecimal("0.005");
    private BigDecimal maxLeverage = new BigDecimal("100");
    private final Cache cache = new Cache();
    private final Stale stale = new Stale();
    private final Swap swap = new Swap();
    private final Kafka kafka = new Kafka();

    public String getSettlementToken() {
        return settlementToken;
    }

    public void setSettlementToken(String settlementToken) {
        this.settlementToken = settlementToken;
    }

    public BigDecimal getDefaultFeeRate() {
        return defaultFeeRate;
    }

    public void setDefaultFeeRate(BigDecimal defaultFeeRate) {
        this.defaultFeeRate = defaultFeeRate;
    }

    public BigDecimal getMaintenanceMarginRate() {
        return maintenanceMarginRate;
    }

    public void setMaintenanceMarginRate(BigDecimal maintenanceMarginRate) {
        this.maintenanceMarginRate = maintenanceMarginRate;
    }

    public BigDecimal getMaxLeverage() {
        return maxLeverage;
    }

    public void setMaxLeverage(BigDecimal maxLeverage) {
        this.maxLeverage = maxLeverage;
    }

    public Stale getStale() {
        return stale;
    }

    public Cache getCache() {
        return cache;
    }

    public Kafka getKafka() {
        return kafka;
    }

    public Swap getSwap() {
        return swap;
    }

    /**
     * trading-core Redis 缓存约束。
     *
     * <p>当前阶段只冻结“最新报价快照”这一类 Redis 缓存的 TTL，
     * 让报价写入路径具备可回收的生命周期，而不是永久残留历史 key。
     */
    public static class Cache {
        private Duration quoteTtl = Duration.ofSeconds(10);

        public Duration getQuoteTtl() {
            return quoteTtl;
        }

        public void setQuoteTtl(Duration quoteTtl) {
            this.quoteTtl = quoteTtl;
        }
    }

    /**
     * 交易域报价时效配置。
     *
     * <p>trading-core 读取 Redis 最新价时，不能只依赖 market 写入瞬间计算出的 `stale` 布尔值。
     * 若外部源中断，原本 fresh 的报价随着时间推移也必须在读取时自动转为 stale，
     * 这样下单和风控链路才能持续遵守“超过 5 秒不可用”的统一规则。
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
     * 交易域隔夜利息结算调度配置。
     *
     * <p>一期 `Swap` 结算固定由 `trading-core-service` 本地定时扫描触发，
     * 调度默认按 `UTC` 每秒执行一次，尽量贴近 `rollover` 时点抓取 fresh 价格，
     * 避免使用超出时效窗口的后续报价误算 `Swap`。
     */
    public static class Swap {
        private boolean enabled = true;
        private String settlementCron = "*/1 * * * * *";
        private String settlementZone = "UTC";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getSettlementCron() {
            return settlementCron;
        }

        public void setSettlementCron(String settlementCron) {
            this.settlementCron = settlementCron;
        }

        public String getSettlementZone() {
            return settlementZone;
        }

        public void setSettlementZone(String settlementZone) {
            this.settlementZone = settlementZone;
        }
    }

    /**
     * trading-core Kafka 主题与消费组配置。
     *
     * <p>交易核心既消费市场和钱包事件，也通过 Outbox 对外发布低频关键业务事件，
     * 因此这里统一固定输入 topic、输出 topic 与消费组名称，避免字符串散落在 listener / producer 中。
     */
    public static class Kafka {
        private String marketPriceTickTopic = "falconx.market.price.tick";
        private String marketKlineUpdateTopic = "falconx.market.kline.update";
        private String walletDepositConfirmedTopic = "falconx.wallet.deposit.confirmed";
        private String walletDepositReversedTopic = "falconx.wallet.deposit.reversed";
        private String depositCreditedTopic = "falconx.trading.deposit.credited";
        private String consumerGroupId = "falconx-trading-core-service";

        public String getMarketPriceTickTopic() {
            return marketPriceTickTopic;
        }

        public void setMarketPriceTickTopic(String marketPriceTickTopic) {
            this.marketPriceTickTopic = marketPriceTickTopic;
        }

        public String getMarketKlineUpdateTopic() {
            return marketKlineUpdateTopic;
        }

        public void setMarketKlineUpdateTopic(String marketKlineUpdateTopic) {
            this.marketKlineUpdateTopic = marketKlineUpdateTopic;
        }

        public String getWalletDepositConfirmedTopic() {
            return walletDepositConfirmedTopic;
        }

        public void setWalletDepositConfirmedTopic(String walletDepositConfirmedTopic) {
            this.walletDepositConfirmedTopic = walletDepositConfirmedTopic;
        }

        public String getWalletDepositReversedTopic() {
            return walletDepositReversedTopic;
        }

        public void setWalletDepositReversedTopic(String walletDepositReversedTopic) {
            this.walletDepositReversedTopic = walletDepositReversedTopic;
        }

        public String getDepositCreditedTopic() {
            return depositCreditedTopic;
        }

        public void setDepositCreditedTopic(String depositCreditedTopic) {
            this.depositCreditedTopic = depositCreditedTopic;
        }

        public String getConsumerGroupId() {
            return consumerGroupId;
        }

        public void setConsumerGroupId(String consumerGroupId) {
            this.consumerGroupId = consumerGroupId;
        }
    }
}
