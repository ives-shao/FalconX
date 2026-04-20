package com.falconx.market.provider;

import com.falconx.market.config.MarketServiceProperties;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

/**
 * Tiingo TLS 支撑测试。
 */
class TiingoTlsSupportTests {

    @Test
    void shouldUseDefaultSslContextWhenTrustStoreIsNotConfigured() {
        TiingoTlsSupport tlsSupport = new TiingoTlsSupport(new DefaultResourceLoader());
        MarketServiceProperties.Tiingo tiingo = new MarketServiceProperties.Tiingo();

        SSLContext sslContext = tlsSupport.buildSslContext(tiingo);

        Assertions.assertNotNull(sslContext);
    }

    @Test
    void shouldLoadConfiguredPkcs12TrustStore() throws Exception {
        TiingoTlsSupport tlsSupport = new TiingoTlsSupport(new DefaultResourceLoader());
        MarketServiceProperties.Tiingo tiingo = new MarketServiceProperties.Tiingo();

        Path trustStorePath = Files.createTempFile("tiingo-trust-store", ".p12");
        char[] password = "changeit".toCharArray();
        try {
            KeyStore trustStore = KeyStore.getInstance("PKCS12");
            trustStore.load(null, password);
            try (OutputStream outputStream = Files.newOutputStream(trustStorePath)) {
                trustStore.store(outputStream, password);
            }

            tiingo.setTrustStoreLocation(trustStorePath.toUri().toString());
            tiingo.setTrustStorePassword("changeit");
            tiingo.setTrustStoreType("PKCS12");

            SSLContext sslContext = tlsSupport.buildSslContext(tiingo);

            Assertions.assertNotNull(sslContext);
        } finally {
            Files.deleteIfExists(trustStorePath);
        }
    }
}
