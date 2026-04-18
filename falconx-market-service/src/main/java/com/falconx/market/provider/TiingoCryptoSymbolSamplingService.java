package com.falconx.market.provider;

import com.falconx.market.config.MarketServiceProperties;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.DisposableBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Tiingo crypto symbol 一次性采样服务。
 *
 * <p>该服务只负责：
 *
 * <ol>
 *   <li>连接 `wss://api.tiingo.com/crypto`</li>
 *   <li>发送最小订阅报文</li>
 *   <li>在固定时间窗口内收集可落库的 symbol 候选</li>
 * </ol>
 *
 * <p>它不把 `crypto` 成交流接入标准报价主链路，避免把成交价误当成 `bid/ask`
 * 写进 Redis/Kafka/ClickHouse 最新价流程。
 */
@Component
@Profile("!stub")
public class TiingoCryptoSymbolSamplingService implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(TiingoCryptoSymbolSamplingService.class);

    private final MarketServiceProperties properties;
    private final TiingoWebSocketProtocolSupport protocolSupport;
    private final HttpClient httpClient;

    public TiingoCryptoSymbolSamplingService(MarketServiceProperties properties,
                                             TiingoWebSocketProtocolSupport protocolSupport) {
        this.properties = properties;
        this.protocolSupport = protocolSupport;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getTiingo().getCryptoSymbolImport().getConnectTimeout())
                .build();
    }

    /**
     * 按配置窗口采样 Tiingo crypto symbol。
     *
     * @return 去重后的 symbol 候选列表
     */
    public List<TiingoDiscoveredCryptoSymbol> sample() {
        MarketServiceProperties.CryptoSymbolImport cryptoSymbolImport = properties.getTiingo().getCryptoSymbolImport();
        if (!cryptoSymbolImport.isEnabled()) {
            log.debug("market.tiingo.crypto-symbol-import.skipped reason=config-disabled");
            return List.of();
        }
        if (properties.getTiingo().getApiKey() == null
                || properties.getTiingo().getApiKey().isBlank()
                || "CHANGE_ME".equals(properties.getTiingo().getApiKey())) {
            log.warn("market.tiingo.crypto-symbol-import.skipped reason=missing-api-key");
            return List.of();
        }

        Duration sampleWindow = cryptoSymbolImport.getSampleWindow();
        log.info(
                "market.tiingo.crypto-symbol-import.start url={} window={} exchanges={} quoteCurrencies={}",
                cryptoSymbolImport.getWebsocketUrl(),
                sampleWindow,
                cryptoSymbolImport.getAllowedExchanges(),
                cryptoSymbolImport.getQuoteCurrencies()
        );

        Map<String, TiingoDiscoveredCryptoSymbol> discoveredSymbols = new LinkedHashMap<>();
        AtomicReference<WebSocket> activeWebSocket = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        CompletableFuture<WebSocket> connectFuture = httpClient.newWebSocketBuilder()
                .connectTimeout(cryptoSymbolImport.getConnectTimeout())
                .buildAsync(cryptoSymbolImport.getWebsocketUrl(), new Listener(discoveredSymbols, activeWebSocket, failure));

        WebSocket webSocket;
        try {
            webSocket = connectFuture.join();
            activeWebSocket.set(webSocket);
            Thread.sleep(sampleWindow.toMillis());
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while sampling Tiingo crypto symbols", interruptedException);
        } catch (RuntimeException runtimeException) {
            throw new IllegalStateException("Failed to sample Tiingo crypto symbols", runtimeException);
        } finally {
            WebSocket currentWebSocket = activeWebSocket.getAndSet(null);
            if (currentWebSocket != null) {
                currentWebSocket.abort();
            }
        }

        Throwable throwable = failure.get();
        if (throwable != null) {
            throw new IllegalStateException("Tiingo crypto symbol sampling failed", throwable);
        }

        List<TiingoDiscoveredCryptoSymbol> symbols = new ArrayList<>(discoveredSymbols.values());
        log.info("market.tiingo.crypto-symbol-import.sampled count={} symbols={}",
                symbols.size(),
                symbols.stream().map(TiingoDiscoveredCryptoSymbol::symbol).toList());
        return List.copyOf(symbols);
    }

    /**
     * 释放 Tiingo crypto 采样使用的底层 HTTP 资源。
     *
     * <p>该采样服务不是长连接主链路，但同样持有独立的 JDK `HttpClient`。服务关闭时显式关闭它，
     * 可以避免采样链路内部线程或网络资源在 JVM 退出前继续悬挂。
     */
    @Override
    public void destroy() {
        httpClient.close();
    }

    private final class Listener implements WebSocket.Listener {

        private final StringBuilder textFrameBuffer = new StringBuilder();
        private final Map<String, TiingoDiscoveredCryptoSymbol> discoveredSymbols;
        private final AtomicReference<WebSocket> activeWebSocket;
        private final AtomicReference<Throwable> failure;

        private Listener(Map<String, TiingoDiscoveredCryptoSymbol> discoveredSymbols,
                         AtomicReference<WebSocket> activeWebSocket,
                         AtomicReference<Throwable> failure) {
            this.discoveredSymbols = discoveredSymbols;
            this.activeWebSocket = activeWebSocket;
            this.failure = failure;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            activeWebSocket.set(webSocket);
            String subscribeMessage = protocolSupport.buildSubscribeMessage(properties.getTiingo().getApiKey());
            webSocket.sendText(subscribeMessage, true)
                    .whenComplete((unused, throwable) -> {
                        if (throwable != null) {
                            failure.compareAndSet(null, throwable);
                            log.warn("market.tiingo.crypto-symbol-import.subscribe.failed reason={}", throwable.toString());
                        } else {
                            log.info("market.tiingo.crypto-symbol-import.subscribed");
                        }
                    });
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            textFrameBuffer.append(data);
            if (last) {
                String messageText = textFrameBuffer.toString();
                textFrameBuffer.setLength(0);
                protocolSupport.parseDiscoveredCryptoSymbol(
                        messageText,
                        properties.getTiingo().getCryptoSymbolImport()
                ).ifPresent(symbol -> discoveredSymbols.putIfAbsent(symbol.symbol(), symbol));
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            activeWebSocket.compareAndSet(webSocket, null);
            log.info("market.tiingo.crypto-symbol-import.closed statusCode={} reason={}", statusCode, reason);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            activeWebSocket.compareAndSet(webSocket, null);
            failure.compareAndSet(null, error);
            log.error("market.tiingo.crypto-symbol-import.error reason={}", error.toString(), error);
        }
    }
}
