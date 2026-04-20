package com.falconx.market.provider;

import static com.falconx.market.support.TiingoExternalTestSupport.combinedOutput;
import static com.falconx.market.support.TiingoExternalTestSupport.waitForCondition;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.falconx.market.config.MarketServiceProperties;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.DefaultResourceLoader;

/**
 * Tiingo 真端点失败路径测试。
 *
 * <p>该测试故意使用错误认证连接真实 Tiingo `fx` 端点，锁定“真实外部源拒绝后，provider 必须留下
 * 服务端/关闭/重连日志证据”的 Stage 6A 排障语义。
 *
 * <p>这里没有使用 stub，也没有伪造本地 WebSocket 服务，确保失败路径来自真实外部源反馈。
 */
@EnabledIfEnvironmentVariable(named = "FALCONX_MARKET_TIINGO_EXTERNAL_TEST_ENABLED", matches = "(?i)true")
@ExtendWith(OutputCaptureExtension.class)
class JdkTiingoQuoteProviderExternalFailureIntegrationTests {

    @Test
    void shouldLogReconnectEvidenceWhenTiingoRejectsInvalidApiKey(CapturedOutput output) {
        MarketServiceProperties properties = new MarketServiceProperties();
        properties.getTiingo().setEnabled(true);
        properties.getTiingo().setWebsocketUrl(URI.create("wss://api.tiingo.com/fx"));
        properties.getTiingo().setApiKey("falconx-invalid-tiingo-key");
        properties.getTiingo().setConnectTimeout(Duration.ofSeconds(10));
        properties.getTiingo().setReconnectInterval(Duration.ofSeconds(1));

        JdkTiingoQuoteProvider provider = new JdkTiingoQuoteProvider(
                properties,
                new TiingoWebSocketProtocolSupport(new ObjectMapper()),
                new TiingoTlsSupport(new DefaultResourceLoader())
        );
        AtomicInteger receivedQuotes = new AtomicInteger();
        try {
            provider.start(List.of("EURUSD"), quote -> receivedQuotes.incrementAndGet());

            waitForCondition(
                    Duration.ofSeconds(20),
                    "错误认证后未观察到重连调度日志" + System.lineSeparator() + combinedOutput(output),
                    () -> combinedOutput(output).contains("market.tiingo.provider.reconnect.scheduled")
            );

            String logs = combinedOutput(output);
            boolean hasFailureEvidence = logs.contains("market.tiingo.provider.server-error")
                    || logs.contains("market.tiingo.provider.closed")
                    || logs.contains("market.tiingo.provider.error")
                    || logs.contains("code=401")
                    || logs.contains("Unauthorized");
            Assertions.assertTrue(hasFailureEvidence, "未观察到 Tiingo 真端点拒绝认证的日志证据");
            Assertions.assertEquals(0, receivedQuotes.get());
        } finally {
            provider.destroy();
        }
    }
}
