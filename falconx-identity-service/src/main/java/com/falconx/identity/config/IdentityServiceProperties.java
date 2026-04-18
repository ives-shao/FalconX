package com.falconx.identity.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * identity-service 配置属性。
 *
 * <p>该配置类用于冻结 Stage 3A 身份服务的最小安全与认证参数，
 * 包括 token 签发者、访问令牌时长、刷新令牌时长以及密码哈希强度。
 * 当前阶段默认使用进程内临时 RSA 密钥对，仅用于本地骨架验证。
 */
@ConfigurationProperties(prefix = "falconx.identity")
public class IdentityServiceProperties {

    private final Token token = new Token();
    private final Password password = new Password();
    private final KeyPair keyPair = new KeyPair();
    private final Kafka kafka = new Kafka();

    public Token getToken() {
        return token;
    }

    public Password getPassword() {
        return password;
    }

    public KeyPair getKeyPair() {
        return keyPair;
    }

    public Kafka getKafka() {
        return kafka;
    }

    /**
     * Token 签发配置。
     */
    public static class Token {
        private String issuer = "falconx-identity-service";
        private Duration accessTokenTtl = Duration.ofMinutes(15);
        private Duration refreshTokenTtl = Duration.ofDays(3);

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }

        public Duration getAccessTokenTtl() {
            return accessTokenTtl;
        }

        public void setAccessTokenTtl(Duration accessTokenTtl) {
            this.accessTokenTtl = accessTokenTtl;
        }

        public Duration getRefreshTokenTtl() {
            return refreshTokenTtl;
        }

        public void setRefreshTokenTtl(Duration refreshTokenTtl) {
            this.refreshTokenTtl = refreshTokenTtl;
        }
    }

    /**
     * 密码哈希配置。
     */
    public static class Password {
        private int bcryptStrength = 12;

        public int getBcryptStrength() {
            return bcryptStrength;
        }

        public void setBcryptStrength(int bcryptStrength) {
            this.bcryptStrength = bcryptStrength;
        }
    }

    /**
     * 开发态 RSA 密钥配置。
     *
     * <p>当前阶段为了让 gateway 能完成本地 JWT 验证，
     * 允许在项目配置中放置开发专用密钥对。生产环境必须改为外部密钥管理。
     */
    public static class KeyPair {
        private String privateKeyPem;
        private String publicKeyPem;

        public String getPrivateKeyPem() {
            return privateKeyPem;
        }

        public void setPrivateKeyPem(String privateKeyPem) {
            this.privateKeyPem = privateKeyPem;
        }

        public String getPublicKeyPem() {
            return publicKeyPem;
        }

        public void setPublicKeyPem(String publicKeyPem) {
            this.publicKeyPem = publicKeyPem;
        }
    }

    /**
     * identity-service Kafka 消费配置。
     */
    public static class Kafka {
        private String depositCreditedTopic = "falconx.trading.deposit.credited";
        private String consumerGroupId = "falconx.identity-service.deposit-credited-consumer-group";

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
