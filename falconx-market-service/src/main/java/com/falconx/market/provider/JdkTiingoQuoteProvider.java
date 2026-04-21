package com.falconx.market.provider;

import com.falconx.market.config.MarketServiceProperties;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 基于 JDK `HttpClient.WebSocket` 的 Tiingo 真连接实现。
 *
 * <p>该实现是 `market-service` 在 Stage 6A 的第一块真实外部源接入：
 *
 * <ol>
 *   <li>使用 JDK 原生 WebSocket 建立外部连接</li>
 *   <li>连接建立后按 Tiingo 当前全品种订阅协议发送最小订阅报文</li>
 *   <li>把收到的文本消息解析成 `TiingoRawQuote` 并交给应用层</li>
 *   <li>连接关闭或异常时按配置间隔自动重连</li>
 * </ol>
 *
 * <p>这里故意不引入第三方 WebSocket 客户端，以符合“优先利用 JDK 25 与 Spring Boot 4 官方能力”的当前规则。
 */
@Component
@Profile("!stub")
public class JdkTiingoQuoteProvider implements TiingoQuoteProvider, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(JdkTiingoQuoteProvider.class);

    private final MarketServiceProperties properties;
    private final TiingoWebSocketProtocolSupport protocolSupport;
    private final TiingoTlsSupport tiingoTlsSupport;
    private final HttpClient httpClient;
    private final ExecutorService quoteDispatchExecutor;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean reconnectScheduled = new AtomicBoolean(false);
    private final AtomicReference<WebSocket> activeWebSocket = new AtomicReference<>();

    private volatile List<String> subscribedSymbols = List.of();
    private volatile Set<String> subscribedSymbolSet = Set.of();
    private volatile Consumer<TiingoRawQuote> quoteConsumer;

    public JdkTiingoQuoteProvider(MarketServiceProperties properties,
                                  TiingoWebSocketProtocolSupport protocolSupport,
                                  TiingoTlsSupport tiingoTlsSupport) {
        this.properties = properties;
        this.protocolSupport = protocolSupport;
        this.tiingoTlsSupport = tiingoTlsSupport;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getTiingo().getConnectTimeout())
                .sslContext(tiingoTlsSupport.buildSslContext(properties.getTiingo()))
                .build();
        this.quoteDispatchExecutor = Executors.newSingleThreadExecutor(
                Thread.ofPlatform()
                        .name("market-tiingo-dispatch-", 0)
                        .daemon(true)
                        .factory()
        );
    }

    @Override
    public void start(List<String> symbols, Consumer<TiingoRawQuote> quoteConsumer) {
        if (!started.compareAndSet(false, true)) {
            log.info("market.tiingo.provider.already-started");
            return;
        }
        this.quoteConsumer = Objects.requireNonNull(quoteConsumer, "quoteConsumer");
        this.subscribedSymbols = normalizeSymbols(symbols);
        this.subscribedSymbolSet = Set.copyOf(this.subscribedSymbols);

        if (!properties.getTiingo().isEnabled()) {
            log.warn("market.tiingo.provider.disabled reason=config-disabled");
            return;
        }
        if (properties.getTiingo().getApiKey() == null
                || properties.getTiingo().getApiKey().isBlank()
                || "CHANGE_ME".equals(properties.getTiingo().getApiKey())) {
            log.warn("market.tiingo.provider.disabled reason=missing-api-key");
            return;
        }
        if (subscribedSymbols.isEmpty()) {
            log.warn("market.tiingo.provider.disabled reason=no-supported-symbols");
            return;
        }

        connect();
    }

    @Override
    public void refreshSymbols(List<String> symbols) {
        List<String> normalizedSymbols = normalizeSymbols(symbols);
        Set<String> newSymbolSet = Set.copyOf(normalizedSymbols);
        Set<String> previousSymbols = subscribedSymbolSet;
        if (previousSymbols.equals(newSymbolSet)) {
            log.debug("market.tiingo.provider.symbols.unchanged count={}", normalizedSymbols.size());
            return;
        }

        List<String> addedSymbols = normalizedSymbols.stream()
                .filter(symbol -> !previousSymbols.contains(symbol))
                .toList();
        List<String> removedSymbols = subscribedSymbols.stream()
                .filter(symbol -> !newSymbolSet.contains(symbol))
                .toList();

        this.subscribedSymbols = normalizedSymbols;
        this.subscribedSymbolSet = newSymbolSet;
        log.info(
                "market.tiingo.provider.symbols.refreshed total={} added={} removed={} addedSymbols={} removedSymbols={}",
                normalizedSymbols.size(),
                addedSymbols.size(),
                removedSymbols.size(),
                addedSymbols,
                removedSymbols
        );
    }

    private void connect() {
        log.info("market.tiingo.provider.connecting url={} symbols={}",
                properties.getTiingo().getWebsocketUrl(),
                subscribedSymbols);
        if (properties.getTiingo().getTrustStoreLocation() != null
                && !properties.getTiingo().getTrustStoreLocation().isBlank()) {
            log.info("market.tiingo.provider.trust-store.enabled location={} type={}",
                    properties.getTiingo().getTrustStoreLocation(),
                    properties.getTiingo().getTrustStoreType());
        }

        httpClient.newWebSocketBuilder()
                .connectTimeout(properties.getTiingo().getConnectTimeout())
                .buildAsync(properties.getTiingo().getWebsocketUrl(), new Listener())
                .whenComplete((webSocket, throwable) -> {
                    if (throwable != null) {
                        log.warn("market.tiingo.provider.connect.failed url={} reason={} hint={}",
                                properties.getTiingo().getWebsocketUrl(),
                                throwable.toString(),
                                TiingoTlsSupport.buildHandshakeFailureHint(throwable));
                        scheduleReconnect("connect-failed");
                        return;
                    }
                    activeWebSocket.set(webSocket);
                });
    }

    private void sendSubscribeMessage(WebSocket webSocket) {
        String subscribeMessage = protocolSupport.buildSubscribeMessage(
                properties.getTiingo().getApiKey()
        );
        webSocket.sendText(subscribeMessage, true)
                .whenComplete((unused, throwable) -> {
                    if (throwable != null) {
                        log.warn("market.tiingo.provider.subscribe.failed reason={}", throwable.toString());
                        scheduleReconnect("subscribe-failed");
                        return;
                    }
                    log.info("market.tiingo.provider.subscribed mode=all-symbols filterSymbols={}",
                            subscribedSymbols);
                });
    }

    private void scheduleReconnect(String reason) {
        if (!started.get()) {
            return;
        }
        if (!reconnectScheduled.compareAndSet(false, true)) {
            return;
        }
        Duration reconnectInterval = properties.getTiingo().getReconnectInterval();
        log.warn("market.tiingo.provider.reconnect.scheduled reason={} delay={}", reason, reconnectInterval);
        CompletableFuture.delayedExecutor(reconnectInterval.toMillis(), TimeUnit.MILLISECONDS)
                .execute(() -> {
                    reconnectScheduled.set(false);
                    if (!started.get()) {
                        return;
                    }
                    closeActiveSocket();
                    connect();
                });
    }

    private void closeActiveSocket() {
        WebSocket currentWebSocket = activeWebSocket.getAndSet(null);
        if (currentWebSocket != null) {
            currentWebSocket.abort();
        }
    }

    private List<String> normalizeSymbols(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String symbol : symbols) {
            if (symbol == null || symbol.isBlank()) {
                continue;
            }
            String normalizedSymbol = symbol.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
            if (!normalized.contains(normalizedSymbol)) {
                normalized.add(normalizedSymbol);
            }
        }
        return List.copyOf(normalized);
    }

    /**
     * 服务关闭时主动中断当前 WebSocket，避免本地重启时残留旧连接。
     */
    @Override
    public void destroy() {
        started.set(false);
        closeActiveSocket();
        quoteDispatchExecutor.shutdownNow();
    }

    /**
     * 把 Tiingo 服务端返回的错误帧压缩成可用于重连诊断的标准原因串。
     *
     * <p>这里故意使用稳定的字符串格式，而不是把整个元数据对象直接写进日志或调度原因，原因有两个：
     *
     * <ul>
     *   <li>后续 `onClose / onError` 会复用这段上下文，把“服务端为什么不满意”串到真正的断连事件里</li>
     *   <li>统一格式后，日志检索可以直接按 `server-error[...]` 检索同类问题</li>
     * </ul>
     *
     * @param metadata 最近一次收到的 Tiingo 元数据帧
     * @return 面向日志与重连调度的紧凑原因串
     */
    static String buildServerErrorHint(TiingoInboundFrameMetadata metadata) {
        if (metadata == null || !metadata.isError()) {
            return "no-server-error-context";
        }
        return "server-error[service=%s,code=%s,message=%s]".formatted(
                valueOrUnknown(metadata.service()),
                valueOrUnknown(metadata.responseCode()),
                sanitizeReasonSegment(metadata.responseMessage())
        );
    }

    /**
     * 生成 WebSocket 主动关闭后的重连原因。
     *
     * <p>这里除了保留状态码和原始 reason 外，还会尝试拼接最近一次服务端错误帧上下文。
     * 这样在排查“为什么会断开并进入重连”时，可以直接从一条日志里同时看到：
     *
     * <ul>
     *   <li>WebSocket close code 的类别</li>
     *   <li>close frame 自带的原因</li>
     *   <li>断连前 Tiingo 是否已经通过 `messageType=E` 给出更明确的服务端提示</li>
     * </ul>
     */
    static String buildReconnectReasonForClose(int statusCode,
                                               String reason,
                                               TiingoInboundFrameMetadata lastServerMetadata) {
        String closeReason = "close[%s,status=%s,reason=%s]".formatted(
                classifyCloseStatus(statusCode),
                statusCode,
                sanitizeReasonSegment(reason)
        );
        if (lastServerMetadata != null && lastServerMetadata.isError()) {
            return closeReason + " linked-" + buildServerErrorHint(lastServerMetadata);
        }
        return closeReason;
    }

    /**
     * 生成本地异常后的重连原因。
     *
     * <p>与 close 事件一样，本地异常也要尽量关联最近一次服务端错误帧。否则日志里只能看到
     * “socket 出错了”，却不知道此前 Tiingo 是否已经返回认证失败、限流或协议错误。
     */
    static String buildReconnectReasonForError(Throwable error, TiingoInboundFrameMetadata lastServerMetadata) {
        String throwableReason = "error[type=%s,message=%s]".formatted(
                error == null ? "unknown" : error.getClass().getSimpleName(),
                sanitizeReasonSegment(error == null ? null : error.getMessage())
        );
        if (lastServerMetadata != null && lastServerMetadata.isError()) {
            return throwableReason + " linked-" + buildServerErrorHint(lastServerMetadata);
        }
        return throwableReason;
    }

    /**
     * 在应用类加载器上下文中执行回调。
     *
     * <p>Tiingo WebSocket 的回调线程来自 JDK `HttpClient` 内部线程池，这些线程的上下文类加载器
     * 不是由 Spring Boot 应用控制。当前报价消费链路会进入 Kafka、Redis、ClickHouse 等组件，
     * 而 Kafka 在静态初始化和 SPI 加载时依赖线程上下文类加载器解析默认实现类。
     *
     * <p>如果直接在 JDK worker 线程里调用应用层消费逻辑，就会出现“服务能启动、单元测试能过，
     * 但收到第一条真实 tick 就在 Kafka 初始化处崩溃”的问题。这里统一切回应用类加载器，
     * 保证后续库组件看到的仍是 Spring Boot fat-jar 的完整类路径。
     */
    static void runWithApplicationClassLoader(Runnable runnable) {
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        ClassLoader applicationClassLoader = JdkTiingoQuoteProvider.class.getClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(applicationClassLoader);
            runnable.run();
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    /**
     * 把报价分发切到应用管理线程。
     *
     * <p>这里刻意使用单线程执行器，目的是在修复 TCCL 问题的同时尽量保留报价顺序，
     * 避免同一连接上的 tick 在缓存写入、K 线聚合和 Kafka 发布时发生乱序。
     *
     * <p>如果单条报价处理失败，这里只记录错误，不直接把 WebSocket 连接打断。
     * 这样可以避免再次进入“收到一条 tick -> 本地组件异常 -> 连接被动重连”的循环。
     */
    private void dispatchQuote(TiingoRawQuote quote) {
        quoteDispatchExecutor.execute(() -> runWithApplicationClassLoader(() -> {
            try {
                quoteConsumer.accept(quote);
            } catch (Throwable error) {
                if (error instanceof VirtualMachineError virtualMachineError) {
                    throw virtualMachineError;
                }
                log.error(
                        "market.tiingo.provider.dispatch.failed ticker={} ts={} reason={}",
                        quote.ticker(),
                        quote.ts(),
                        error.toString(),
                        error
                );
            }
        }));
    }

    static DispatchSummary summarizeDispatch(List<TiingoRawQuote> quotes, Set<String> subscribedSymbols) {
        if (quotes == null || quotes.isEmpty()) {
            return new DispatchSummary(List.of(), 0);
        }
        List<TiingoRawQuote> acceptedQuotes = new ArrayList<>();
        int filteredCount = 0;
        for (TiingoRawQuote quote : quotes) {
            if (quote != null && subscribedSymbols.contains(quote.ticker())) {
                acceptedQuotes.add(quote);
            } else {
                filteredCount++;
            }
        }
        return new DispatchSummary(List.copyOf(acceptedQuotes), filteredCount);
    }

    /**
     * 按常见 WebSocket close code 给出更适合排障的分类名。
     *
     * <p>这里不试图覆盖 RFC 中的全部关闭码，只处理当前 Tiingo 接入排障最常见的几类，
     * 其余情况统一回落到 `status-xxxx`，避免把未知状态强行解释错。
     */
    static String classifyCloseStatus(int statusCode) {
        return switch (statusCode) {
            case 1000 -> "normal-closure";
            case 1001 -> "going-away";
            case 1002 -> "protocol-error";
            case 1003 -> "unsupported-data";
            case 1006 -> "abnormal-closure";
            case 1008 -> "policy-violation";
            case 1009 -> "message-too-big";
            case 1011 -> "server-internal-error";
            case 1012 -> "service-restart";
            case 1013 -> "try-again-later";
            default -> "status-" + statusCode;
        };
    }

    private static String valueOrUnknown(Object value) {
        return value == null ? "unknown" : sanitizeReasonSegment(String.valueOf(value));
    }

    private static String sanitizeReasonSegment(String value) {
        if (value == null || value.isBlank()) {
            return "none";
        }
        return value.replace('\n', ' ')
                .replace('\r', ' ')
                .replace('[', '(')
                .replace(']', ')');
    }

    private final class Listener implements WebSocket.Listener {

        private final StringBuilder textFrameBuffer = new StringBuilder();
        private volatile TiingoInboundFrameMetadata lastServerMetadata;

        @Override
        public void onOpen(WebSocket webSocket) {
            log.info("market.tiingo.provider.connected url={}", properties.getTiingo().getWebsocketUrl());
            sendSubscribeMessage(webSocket);
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            textFrameBuffer.append(data);
            if (last) {
                String messageText = textFrameBuffer.toString();
                textFrameBuffer.setLength(0);
                protocolSupport.parseFrameMetadata(messageText).ifPresent(this::handleFrameMetadata);
                List<TiingoRawQuote> quotes = protocolSupport.parseQuotes(messageText);
                if (!quotes.isEmpty()) {
                    DispatchSummary dispatchSummary = summarizeDispatch(quotes, subscribedSymbolSet);
                    log.debug("market.tiingo.provider.message.parsed quotes={} accepted={} filtered={}",
                            quotes.size(),
                            dispatchSummary.acceptedQuotes().size(),
                            dispatchSummary.filteredCount());
                    dispatchSummary.acceptedQuotes().forEach(JdkTiingoQuoteProvider.this::dispatchQuote);
                }
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        /**
         * 统一处理 Tiingo 的连接级元数据帧。
         *
         * <p>这里不改变报价主链路，只负责把订阅确认、心跳和错误信息稳定输出到日志。
         * 这样做的原因是：
         *
         * <ul>
         *   <li>订阅确认可以证明重连后的订阅恢复已经成功</li>
         *   <li>心跳日志可以帮助定位“连接还活着，但没有报价”的场景</li>
         *   <li>错误帧日志可以补足 WebSocket 关闭前的服务端反馈</li>
         * </ul>
         */
        private void handleFrameMetadata(TiingoInboundFrameMetadata metadata) {
            if (metadata.isInformational()) {
                log.info(
                        "market.tiingo.provider.subscription.confirmed service={} subscriptionId={} code={} message={}",
                        metadata.service(),
                        metadata.subscriptionId(),
                        metadata.responseCode(),
                        metadata.responseMessage()
                );
                return;
            }
            if (metadata.isHeartbeat()) {
                log.debug(
                        "market.tiingo.provider.heartbeat service={} code={} message={}",
                        metadata.service(),
                        metadata.responseCode(),
                        metadata.responseMessage()
                );
                return;
            }
            if (metadata.isError()) {
                lastServerMetadata = metadata;
                log.warn(
                        "market.tiingo.provider.server-error service={} code={} message={} reconnectHint={}",
                        metadata.service(),
                        metadata.responseCode(),
                        metadata.responseMessage(),
                        buildServerErrorHint(metadata)
                );
            }
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            activeWebSocket.compareAndSet(webSocket, null);
            String reconnectReason = buildReconnectReasonForClose(statusCode, reason, lastServerMetadata);
            log.warn("market.tiingo.provider.closed statusCode={} reason={} reconnectReason={}",
                    statusCode,
                    reason,
                    reconnectReason);
            scheduleReconnect(reconnectReason);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            activeWebSocket.compareAndSet(webSocket, null);
            String reconnectReason = buildReconnectReasonForError(error, lastServerMetadata);
            log.error("market.tiingo.provider.error reason={} reconnectReason={}",
                    error.toString(),
                    reconnectReason);
            scheduleReconnect(reconnectReason);
        }
    }

    record DispatchSummary(List<TiingoRawQuote> acceptedQuotes, int filteredCount) {
    }
}
