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

    public String getPublicKeyPem() {
        return publicKeyPem;
    }

    public void setPublicKeyPem(String publicKeyPem) {
        this.publicKeyPem = publicKeyPem;
    }
}
