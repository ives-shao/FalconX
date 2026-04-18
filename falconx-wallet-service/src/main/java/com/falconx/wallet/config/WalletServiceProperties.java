package com.falconx.wallet.config;

import com.falconx.domain.enums.ChainType;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * wallet-service 配置属性。
 *
 * <p>该配置类用于冻结 Stage 2B 钱包事件底座的最小配置结构，
 * 让地址分配、链监听、确认推进与 Kafka 发布都从统一配置入口读取参数。
 * 当前阶段已经切换为真实链 SDK 驱动的监听器骨架，后续只允许继续补轮询与解析逻辑，
 * 不应再改动这里的字段语义。
 */
@ConfigurationProperties(prefix = "falconx.wallet")
public class WalletServiceProperties {

    private final Allocation allocation = new Allocation();
    private final Kafka kafka = new Kafka();
    private final Chains chains = new Chains();

    public Allocation getAllocation() {
        return allocation;
    }

    public Kafka getKafka() {
        return kafka;
    }

    public Chains getChains() {
        return chains;
    }

    /**
     * 按链类型返回对应链节点配置。
     *
     * @param chainType 链类型
     * @return 对应的链配置
     */
    public Chain chain(ChainType chainType) {
        return switch (chainType) {
            case ETH -> chains.getEth();
            case BSC -> chains.getBsc();
            case TRON -> chains.getTron();
            case SOL -> chains.getSol();
        };
    }

    /**
     * 地址分配相关配置。
     */
    public static class Allocation {
        private String addressPrefix = "stub";
        private int startIndex = 1;

        public String getAddressPrefix() {
            return addressPrefix;
        }

        public void setAddressPrefix(String addressPrefix) {
            this.addressPrefix = addressPrefix;
        }

        public int getStartIndex() {
            return startIndex;
        }

        public void setStartIndex(int startIndex) {
            this.startIndex = startIndex;
        }
    }

    /**
     * Kafka 主题配置。
     */
    public static class Kafka {
        private String depositDetectedTopic = "falconx.wallet.deposit.detected";
        private String depositConfirmedTopic = "falconx.wallet.deposit.confirmed";
        private String depositReversedTopic = "falconx.wallet.deposit.reversed";

        public String getDepositDetectedTopic() {
            return depositDetectedTopic;
        }

        public void setDepositDetectedTopic(String depositDetectedTopic) {
            this.depositDetectedTopic = depositDetectedTopic;
        }

        public String getDepositConfirmedTopic() {
            return depositConfirmedTopic;
        }

        public void setDepositConfirmedTopic(String depositConfirmedTopic) {
            this.depositConfirmedTopic = depositConfirmedTopic;
        }

        public String getDepositReversedTopic() {
            return depositReversedTopic;
        }

        public void setDepositReversedTopic(String depositReversedTopic) {
            this.depositReversedTopic = depositReversedTopic;
        }
    }

    /**
     * 全部链配置聚合。
     */
    public static class Chains {
        private final Chain eth = new Chain(URI.create("http://localhost:8545"), Duration.ofSeconds(5), 12, "block", "0");
        private final Chain bsc = new Chain(URI.create("http://localhost:8546"), Duration.ofSeconds(5), 15, "block", "0");
        private final Chain tron = new Chain(
                URI.create("http://localhost:8090"),
                URI.create("http://localhost:8091"),
                Duration.ofSeconds(5),
                19,
                "block",
                "0",
                null
        );
        private final Chain sol = new Chain(URI.create("http://localhost:8899"), Duration.ofSeconds(5), 32, "slot", "0");

        public Chain getEth() {
            return eth;
        }

        public Chain getBsc() {
            return bsc;
        }

        public Chain getTron() {
            return tron;
        }

        public Chain getSol() {
            return sol;
        }
    }

    /**
     * 单条链的节点、确认与游标配置。
     */
    public static class Chain {
        private URI rpcUrl = URI.create("http://localhost");
        private URI solidityRpcUrl;
        private Duration scanInterval = Duration.ofSeconds(5);
        private int requiredConfirmations = 12;
        private String cursorType = "block";
        private String initialCursor = "0";
        private String apiKey;

        public Chain() {
        }

        public Chain(URI rpcUrl, Duration scanInterval, int requiredConfirmations, String cursorType, String initialCursor) {
            this(rpcUrl, null, scanInterval, requiredConfirmations, cursorType, initialCursor, null);
        }

        public Chain(URI rpcUrl,
                     URI solidityRpcUrl,
                     Duration scanInterval,
                     int requiredConfirmations,
                     String cursorType,
                     String initialCursor,
                     String apiKey) {
            this.rpcUrl = rpcUrl;
            this.solidityRpcUrl = solidityRpcUrl;
            this.scanInterval = scanInterval;
            this.requiredConfirmations = requiredConfirmations;
            this.cursorType = cursorType;
            this.initialCursor = initialCursor;
            this.apiKey = apiKey;
        }

        public URI getRpcUrl() {
            return rpcUrl;
        }

        public void setRpcUrl(URI rpcUrl) {
            this.rpcUrl = rpcUrl;
        }

        public URI getSolidityRpcUrl() {
            return solidityRpcUrl;
        }

        public void setSolidityRpcUrl(URI solidityRpcUrl) {
            this.solidityRpcUrl = solidityRpcUrl;
        }

        public Duration getScanInterval() {
            return scanInterval;
        }

        public void setScanInterval(Duration scanInterval) {
            this.scanInterval = scanInterval;
        }

        public int getRequiredConfirmations() {
            return requiredConfirmations;
        }

        public void setRequiredConfirmations(int requiredConfirmations) {
            this.requiredConfirmations = requiredConfirmations;
        }

        public String getCursorType() {
            return cursorType;
        }

        public void setCursorType(String cursorType) {
            this.cursorType = cursorType;
        }

        public String getInitialCursor() {
            return initialCursor;
        }

        public void setInitialCursor(String initialCursor) {
            this.initialCursor = initialCursor;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }
    }
}
