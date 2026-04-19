package com.falconx.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * gateway 安全配置属性。
 *
 * <p>当前阶段该配置类只承载 JWT 验证所需的开发态 RSA 公钥。
 * 生产环境应改为外部密钥管理或环境变量注入。
 */
@ConfigurationProperties(prefix = "falconx.gateway.security")
public class GatewaySecurityProperties {

    private String publicKeyPem;
    private String clientIpHeader = "X-Client-Ip";
    private int authRequestRateLimitPerMinute = 20;
    private int tradingRequestRateLimitPerSecond = 10;
    private int globalRequestRateLimitPerMinute = 200;
    private int connectTimeoutMillis = 1000;
    private long responseTimeoutMillis = 5000;

    public String getPublicKeyPem() {
        return publicKeyPem;
    }

    public void setPublicKeyPem(String publicKeyPem) {
        this.publicKeyPem = publicKeyPem;
    }

    public String getClientIpHeader() {
        return clientIpHeader;
    }

    public void setClientIpHeader(String clientIpHeader) {
        this.clientIpHeader = clientIpHeader;
    }

    public int getAuthRequestRateLimitPerMinute() {
        return authRequestRateLimitPerMinute;
    }

    public void setAuthRequestRateLimitPerMinute(int authRequestRateLimitPerMinute) {
        this.authRequestRateLimitPerMinute = authRequestRateLimitPerMinute;
    }

    public int getTradingRequestRateLimitPerSecond() {
        return tradingRequestRateLimitPerSecond;
    }

    public void setTradingRequestRateLimitPerSecond(int tradingRequestRateLimitPerSecond) {
        this.tradingRequestRateLimitPerSecond = tradingRequestRateLimitPerSecond;
    }

    public int getGlobalRequestRateLimitPerMinute() {
        return globalRequestRateLimitPerMinute;
    }

    public void setGlobalRequestRateLimitPerMinute(int globalRequestRateLimitPerMinute) {
        this.globalRequestRateLimitPerMinute = globalRequestRateLimitPerMinute;
    }

    public int getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public void setConnectTimeoutMillis(int connectTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
    }

    public long getResponseTimeoutMillis() {
        return responseTimeoutMillis;
    }

    public void setResponseTimeoutMillis(long responseTimeoutMillis) {
        this.responseTimeoutMillis = responseTimeoutMillis;
    }
}
