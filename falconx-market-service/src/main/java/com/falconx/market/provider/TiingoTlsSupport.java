package com.falconx.market.provider;

import com.falconx.market.config.MarketServiceProperties;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.CertPathBuilderException;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManagerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * Tiingo 连接的 TLS 支撑。
 *
 * <p>当前主要解决两类问题：
 *
 * <ul>
 *   <li>在本地网络存在 TLS inspection / 自签根证书时，为 JDK `HttpClient` 显式装配自定义 trust store</li>
 *   <li>把 `PKIX path building failed` 这类泛化握手失败收敛为可执行的排障提示</li>
 * </ul>
 */
@Component
public class TiingoTlsSupport {

    private final ResourceLoader resourceLoader;

    public TiingoTlsSupport(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
     * 为 Tiingo WebSocket 构建 SSLContext。
     *
     * <p>未配置自定义 trust store 时，保持 JDK 默认信任链行为；配置后只对 Tiingo 连接使用该 trust store。
     */
    public SSLContext buildSslContext(MarketServiceProperties.Tiingo tiingo) {
        String trustStoreLocation = tiingo.getTrustStoreLocation();
        if (trustStoreLocation == null || trustStoreLocation.isBlank()) {
            try {
                return SSLContext.getDefault();
            } catch (Exception exception) {
                throw new IllegalStateException("Unable to initialize default Tiingo SSL context", exception);
            }
        }

        try {
            Resource resource = resourceLoader.getResource(trustStoreLocation);
            if (!resource.exists()) {
                throw new IllegalStateException("Tiingo trust store does not exist: " + trustStoreLocation);
            }

            KeyStore trustStore = KeyStore.getInstance(tiingo.getTrustStoreType());
            char[] password = tiingo.getTrustStorePassword() == null
                    ? new char[0]
                    : tiingo.getTrustStorePassword().toCharArray();
            try (InputStream inputStream = resource.getInputStream()) {
                trustStore.load(inputStream, password);
            }

            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm()
            );
            trustManagerFactory.init(trustStore);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
            return sslContext;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to initialize Tiingo SSL context", exception);
        }
    }

    /**
     * 把 TLS 握手失败映射成对运维更友好的提示。
     */
    public static String buildHandshakeFailureHint(Throwable error) {
        if (hasCause(error, CertPathBuilderException.class)
                || messageContains(error, "PKIX path building failed")
                || messageContains(error, "unable to find valid certification path")) {
            return "pkix-trust-path-failed: configure falconx.market.tiingo.trust-store-location/password/type with the local root CA used on this network";
        }
        if (hasCause(error, SSLHandshakeException.class)) {
            return "ssl-handshake-failed: inspect the Tiingo certificate chain, local TLS inspection root CA, and JDK trust store";
        }
        return "none";
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        Throwable current = error;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean messageContains(Throwable error, String pattern) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains(pattern)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
