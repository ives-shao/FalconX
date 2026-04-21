package com.falconx.market.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.falconx.common.error.CommonErrorCode;
import com.falconx.infrastructure.trace.TraceIdConstants;
import com.falconx.infrastructure.trace.TraceIdSupport;
import com.falconx.market.config.MarketServiceProperties;
import com.falconx.market.entity.KlineSnapshot;
import com.falconx.market.entity.MarketSymbol;
import com.falconx.market.entity.StandardQuote;
import com.falconx.market.repository.MarketSymbolRepository;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

/**
 * market-service WebSocket session registry。
 *
 * <p>该组件统一负责：
 *
 * <ul>
 *   <li>连接注册与关闭</li>
 *   <li>订阅协议解析</li>
 *   <li>行情 / K 线 / stale 推送</li>
 *   <li>Ping / Pong 心跳超时</li>
 * </ul>
 */
@Component
public class MarketWebSocketSessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(MarketWebSocketSessionRegistry.class);
    private static final String PRICE_TICK_CHANNEL = "price.tick";
    private static final String KLINE_PREFIX = "kline.";
    private static final String ERROR_TYPE = "error";
    private static final String SUBSCRIBED_TYPE = "subscribed";
    private static final String UNSUBSCRIBED_TYPE = "unsubscribed";
    private static final String PONG_TYPE = "pong";

    private final ObjectMapper objectMapper;
    private final MarketSymbolRepository marketSymbolRepository;
    private final Set<String> supportedKlineChannels;
    private final Duration pingInterval;
    private final Duration pongTimeout;
    private final ScheduledExecutorService heartbeatExecutor;
    private final ConcurrentMap<String, SessionState> sessions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> staleNotifiedAtBySymbol = new ConcurrentHashMap<>();

    public MarketWebSocketSessionRegistry(ObjectMapper objectMapper,
                                          MarketSymbolRepository marketSymbolRepository,
                                          MarketServiceProperties properties) {
        this.objectMapper = objectMapper;
        this.marketSymbolRepository = marketSymbolRepository;
        this.supportedKlineChannels = properties.getKline().getIntervals().stream()
                .map(interval -> KLINE_PREFIX + interval.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        this.pingInterval = properties.getWebSocket().getPingInterval();
        this.pongTimeout = properties.getWebSocket().getPongTimeout();
        this.heartbeatExecutor = Executors.newScheduledThreadPool(2, new WebSocketHeartbeatThreadFactory());
    }

    public void register(WebSocketSession rawSession) {
        String traceId = TraceIdSupport.reuseOrCreate(attribute(rawSession, MarketWebSocketHandshakeInterceptor.ATTRIBUTE_TRACE_ID));
        ConcurrentWebSocketSessionDecorator session = new ConcurrentWebSocketSessionDecorator(rawSession, 10_000, 512 * 1024);
        SessionState state = new SessionState(
                session,
                traceId,
                attribute(rawSession, MarketWebSocketHandshakeInterceptor.ATTRIBUTE_USER_ID),
                attribute(rawSession, MarketWebSocketHandshakeInterceptor.ATTRIBUTE_UID),
                attribute(rawSession, MarketWebSocketHandshakeInterceptor.ATTRIBUTE_STATUS)
        );
        sessions.put(session.getId(), state);
        state.pingFuture = heartbeatExecutor.scheduleAtFixedRate(
                () -> sendHeartbeat(state),
                pingInterval.toMillis(),
                pingInterval.toMillis(),
                TimeUnit.MILLISECONDS
        );
        state.watchdogFuture = heartbeatExecutor.scheduleAtFixedRate(
                () -> checkHeartbeat(state),
                1_000L,
                1_000L,
                TimeUnit.MILLISECONDS
        );
        withTrace(state.traceId(), () -> log.info("market.websocket.session.opened sessionId={} userId={} uid={} status={} activeSessions={}",
                session.getId(),
                state.userId(),
                state.uid(),
                state.status(),
                sessions.size()));
    }

    public void unregister(String sessionId, CloseStatus closeStatus, String reason) {
        SessionState state = sessions.remove(sessionId);
        if (state == null) {
            return;
        }
        cancel(state.pingFuture);
        cancel(state.watchdogFuture);
        closeQuietly(state.session(), closeStatus);
        withTrace(state.traceId(), () -> log.info("market.websocket.session.closed sessionId={} userId={} closeCode={} reason={} activeSessions={}",
                sessionId,
                state.userId(),
                closeStatus.getCode(),
                reason,
                sessions.size()));
    }

    public void handleTextMessage(String sessionId, String payload) {
        SessionState state = sessions.get(sessionId);
        if (state == null) {
            return;
        }
        withTrace(state.traceId(), () -> {
            try {
                JsonNode root = objectMapper.readTree(payload);
                String type = root.path("type").asText("");
                switch (type) {
                    case "subscribe" -> handleSubscribe(state, root);
                    case "unsubscribe" -> handleUnsubscribe(state, root);
                    case "ping" -> sendPong(state, root.path("ts").asText(""));
                    default -> sendError(state, root.path("requestId").asText(null),
                            CommonErrorCode.INVALID_REQUEST_PAYLOAD.code(),
                            CommonErrorCode.INVALID_REQUEST_PAYLOAD.message());
                }
            } catch (IOException exception) {
                sendError(state, null,
                        CommonErrorCode.INVALID_REQUEST_PAYLOAD.code(),
                        CommonErrorCode.INVALID_REQUEST_PAYLOAD.message());
            }
        });
    }

    public void markPong(String sessionId) {
        SessionState state = sessions.get(sessionId);
        if (state == null) {
            return;
        }
        state.lastPongAtMillis().set(System.currentTimeMillis());
        withTrace(state.traceId(), () -> log.debug("market.websocket.session.pong sessionId={} userId={}",
                sessionId,
                state.userId()));
    }

    public void publishQuote(StandardQuote quote) {
        staleNotifiedAtBySymbol.remove(quote.symbol());
        int recipients = 0;
        for (SessionState state : sessions.values()) {
            if (!state.isSubscribed(PRICE_TICK_CHANNEL, quote.symbol())) {
                continue;
            }
            recipients++;
            sendJson(state, new PriceTickFrame(
                    PRICE_TICK_CHANNEL,
                    quote.symbol(),
                    quote.bid().toPlainString(),
                    quote.ask().toPlainString(),
                    quote.mid().toPlainString(),
                    quote.mark().toPlainString(),
                    quote.ts().toString(),
                    quote.source(),
                    quote.stale()
            ));
        }
        if (recipients > 0) {
            log.info("market.websocket.price.push symbol={} stale={} recipientCount={} quoteTs={} source={}",
                    quote.symbol(),
                    quote.stale(),
                    recipients,
                    quote.ts(),
                    quote.source());
        }
    }

    public void publishKline(KlineSnapshot snapshot) {
        String channel = KLINE_PREFIX + snapshot.interval().toLowerCase(Locale.ROOT);
        int recipients = 0;
        for (SessionState state : sessions.values()) {
            if (!state.isSubscribed(channel, snapshot.symbol())) {
                continue;
            }
            recipients++;
            sendJson(state, new KlineFrame(
                    channel,
                    snapshot.symbol(),
                    snapshot.interval(),
                    snapshot.open().toPlainString(),
                    snapshot.high().toPlainString(),
                    snapshot.low().toPlainString(),
                    snapshot.close().toPlainString(),
                    snapshot.volume().toPlainString(),
                    snapshot.openTime().toString(),
                    snapshot.closeTime().toString(),
                    snapshot.isFinal()
            ));
        }
        if (recipients > 0) {
            log.info("market.websocket.kline.push symbol={} interval={} isFinal={} recipientCount={} closeTime={}",
                    snapshot.symbol(),
                    snapshot.interval(),
                    snapshot.isFinal(),
                    recipients,
                    snapshot.closeTime());
        }
    }

    public void publishStale(StandardQuote quote) {
        long quoteTsMillis = quote.ts().toInstant().toEpochMilli();
        Long previous = staleNotifiedAtBySymbol.putIfAbsent(quote.symbol(), quoteTsMillis);
        if (previous != null && previous >= quoteTsMillis) {
            return;
        }
        int recipients = 0;
        for (SessionState state : sessions.values()) {
            if (!state.isSubscribed(PRICE_TICK_CHANNEL, quote.symbol())) {
                continue;
            }
            recipients++;
            sendJson(state, new StalePriceFrame(
                    PRICE_TICK_CHANNEL,
                    quote.symbol(),
                    true,
                    quote.ts().toString()
            ));
        }
        if (recipients > 0) {
            log.warn("market.websocket.price.stale-push symbol={} recipientCount={} quoteTs={}",
                    quote.symbol(),
                    recipients,
                    quote.ts());
        }
    }

    public Set<String> subscribedSymbols() {
        return sessions.values().stream()
                .flatMap(state -> state.allSubscribedSymbols().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @PreDestroy
    public void shutdown() {
        heartbeatExecutor.shutdownNow();
    }

    private void handleSubscribe(SessionState state, JsonNode root) {
        String requestId = root.path("requestId").asText(null);
        List<String> channels = readStringArray(root.get("channels"));
        if (channels.isEmpty()) {
            sendError(state, requestId,
                    CommonErrorCode.INVALID_REQUEST_PAYLOAD.code(),
                    CommonErrorCode.INVALID_REQUEST_PAYLOAD.message());
            return;
        }
        List<String> normalizedChannels = normalizeChannels(channels);
        if (normalizedChannels.isEmpty()) {
            sendError(state, requestId,
                    CommonErrorCode.INVALID_REQUEST_PAYLOAD.code(),
                    CommonErrorCode.INVALID_REQUEST_PAYLOAD.message());
            return;
        }

        List<String> requestedSymbols = readStringArray(root.get("symbols"));
        List<String> resolvedSymbols = resolveSymbols(requestedSymbols);
        if (resolvedSymbols == null) {
            String invalidSymbol = requestedSymbols.stream()
                    .map(symbol -> symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT))
                    .filter(symbol -> !symbol.isBlank())
                    .filter(symbol -> !tradingSymbols().contains(symbol))
                    .findFirst()
                    .orElse("UNKNOWN");
            sendError(state, requestId, "30001", "symbol not found: " + invalidSymbol);
            return;
        }

        state.subscribe(normalizedChannels, resolvedSymbols);
        sendJson(state, new SubscriptionFrame(SUBSCRIBED_TYPE, requestId, normalizedChannels, resolvedSymbols));
        log.info("market.websocket.subscribe.accepted sessionId={} userId={} requestId={} channels={} symbols={} channelCount={} symbolCount={}",
                state.session().getId(),
                state.userId(),
                requestId,
                normalizedChannels,
                resolvedSymbols,
                normalizedChannels.size(),
                resolvedSymbols.size());
    }

    private void handleUnsubscribe(SessionState state, JsonNode root) {
        String requestId = root.path("requestId").asText(null);
        List<String> channels = normalizeChannels(readStringArray(root.get("channels")));
        List<String> symbols = resolveSymbols(readStringArray(root.get("symbols")));
        if (channels.isEmpty() || symbols == null) {
            sendError(state, requestId,
                    CommonErrorCode.INVALID_REQUEST_PAYLOAD.code(),
                    CommonErrorCode.INVALID_REQUEST_PAYLOAD.message());
            return;
        }

        state.unsubscribe(channels, symbols);
        sendJson(state, new SubscriptionFrame(UNSUBSCRIBED_TYPE, requestId, channels, symbols));
        log.info("market.websocket.unsubscribe.accepted sessionId={} userId={} requestId={} channels={} symbols={} channelCount={} symbolCount={}",
                state.session().getId(),
                state.userId(),
                requestId,
                channels,
                symbols,
                channels.size(),
                symbols.size());
    }

    private void sendPong(SessionState state, String ts) {
        sendJson(state, new PongFrame(PONG_TYPE, ts));
    }

    private void sendError(SessionState state, String requestId, String code, String message) {
        sendJson(state, new ErrorFrame(ERROR_TYPE, requestId, code, message));
    }

    private void sendJson(SessionState state, Object payload) {
        try {
            state.session().sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
        } catch (IOException exception) {
            log.warn("market.websocket.session.send.failed sessionId={} userId={} message={}",
                    state.session().getId(),
                    state.userId(),
                    exception.getMessage());
            unregister(state.session().getId(), CloseStatus.SERVER_ERROR, "send-failed");
        }
    }

    private void sendHeartbeat(SessionState state) {
        if (!state.session().isOpen()) {
            return;
        }
        state.lastPingAtMillis().set(System.currentTimeMillis());
        try {
            state.session().sendMessage(new PingMessage());
            withTrace(state.traceId(), () -> log.debug("market.websocket.session.ping sessionId={} userId={}",
                    state.session().getId(),
                    state.userId()));
        } catch (IOException exception) {
            withTrace(state.traceId(), () -> log.warn("market.websocket.session.ping.failed sessionId={} userId={} message={}",
                    state.session().getId(),
                    state.userId(),
                    exception.getMessage()));
            unregister(state.session().getId(), CloseStatus.SERVER_ERROR, "ping-send-failed");
        }
    }

    private void checkHeartbeat(SessionState state) {
        long lastPing = state.lastPingAtMillis().get();
        if (lastPing == 0L) {
            return;
        }
        long lastPong = state.lastPongAtMillis().get();
        long now = System.currentTimeMillis();
        if (lastPong < lastPing && now - lastPing > pongTimeout.toMillis()) {
            withTrace(state.traceId(), () -> log.warn("market.websocket.session.pong-timeout sessionId={} userId={} timeoutMillis={}",
                    state.session().getId(),
                    state.userId(),
                    pongTimeout.toMillis()));
            unregister(state.session().getId(), CloseStatus.GOING_AWAY, "pong-timeout");
        }
    }

    private List<String> normalizeChannels(List<String> rawChannels) {
        return rawChannels.stream()
                .map(channel -> channel == null ? "" : channel.trim().toLowerCase(Locale.ROOT))
                .filter(channel -> PRICE_TICK_CHANNEL.equals(channel) || supportedKlineChannels.contains(channel))
                .distinct()
                .toList();
    }

    private List<String> resolveSymbols(List<String> requestedSymbols) {
        Set<String> tradingSymbols = tradingSymbols();
        if (requestedSymbols.isEmpty()) {
            return tradingSymbols.stream().sorted().toList();
        }

        List<String> normalized = requestedSymbols.stream()
                .map(symbol -> symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT))
                .filter(symbol -> !symbol.isBlank())
                .distinct()
                .toList();
        if (normalized.stream().anyMatch(symbol -> !tradingSymbols.contains(symbol))) {
            return null;
        }
        return normalized;
    }

    private Set<String> tradingSymbols() {
        return marketSymbolRepository.findAllTradingSymbols().stream()
                .map(MarketSymbol::symbol)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<String> readStringArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(child -> {
            if (child != null && child.isTextual()) {
                values.add(child.asText());
            }
        });
        return values;
    }

    private void cancel(ScheduledFuture<?> future) {
        if (future != null) {
            future.cancel(true);
        }
    }

    private void closeQuietly(WebSocketSession session, CloseStatus closeStatus) {
        if (!session.isOpen()) {
            return;
        }
        try {
            session.close(closeStatus);
        } catch (IOException ignored) {
        }
    }

    private String attribute(WebSocketSession session, String key) {
        Object value = session.getAttributes().get(key);
        return value instanceof String string ? string : null;
    }

    private void withTrace(String traceId, Runnable action) {
        MDC.put(TraceIdConstants.TRACE_ID_MDC_KEY, traceId);
        try {
            action.run();
        } finally {
            MDC.remove(TraceIdConstants.TRACE_ID_MDC_KEY);
        }
    }

    private record SubscriptionFrame(String type, String requestId, List<String> channels, List<String> symbols) {
    }

    private record ErrorFrame(String type, String requestId, String code, String message) {
    }

    private record PongFrame(String type, String ts) {
    }

    private record PriceTickFrame(String type,
                                  String symbol,
                                  String bid,
                                  String ask,
                                  String mid,
                                  String mark,
                                  String ts,
                                  String source,
                                  boolean stale) {
    }

    private record StalePriceFrame(String type,
                                   String symbol,
                                   boolean stale,
                                   String ts) {
    }

    private record KlineFrame(String type,
                              String symbol,
                              String interval,
                              String open,
                              String high,
                              String low,
                              String close,
                              String volume,
                              String openTime,
                              String closeTime,
                              boolean isFinal) {
    }

    private static final class SessionState {
        private final ConcurrentWebSocketSessionDecorator session;
        private final String traceId;
        private final String userId;
        private final String uid;
        private final String status;
        private final ConcurrentMap<String, Set<String>> channelSymbols = new ConcurrentHashMap<>();
        private final AtomicLong lastPingAtMillis = new AtomicLong();
        private final AtomicLong lastPongAtMillis = new AtomicLong(System.currentTimeMillis());
        private volatile ScheduledFuture<?> pingFuture;
        private volatile ScheduledFuture<?> watchdogFuture;

        private SessionState(ConcurrentWebSocketSessionDecorator session,
                             String traceId,
                             String userId,
                             String uid,
                             String status) {
            this.session = session;
            this.traceId = traceId;
            this.userId = userId;
            this.uid = uid;
            this.status = status;
        }

        private ConcurrentWebSocketSessionDecorator session() {
            return session;
        }

        private String traceId() {
            return traceId;
        }

        private String userId() {
            return userId;
        }

        private String uid() {
            return uid;
        }

        private String status() {
            return status;
        }

        private AtomicLong lastPingAtMillis() {
            return lastPingAtMillis;
        }

        private AtomicLong lastPongAtMillis() {
            return lastPongAtMillis;
        }

        private void subscribe(List<String> channels, List<String> symbols) {
            channels.forEach(channel -> channelSymbols.compute(channel, (ignored, existing) -> {
                Set<String> next = existing == null ? ConcurrentHashMap.newKeySet() : existing;
                next.addAll(symbols);
                return next;
            }));
        }

        private void unsubscribe(List<String> channels, List<String> symbols) {
            channels.forEach(channel -> channelSymbols.computeIfPresent(channel, (ignored, existing) -> {
                existing.removeAll(symbols);
                return existing.isEmpty() ? null : existing;
            }));
        }

        private boolean isSubscribed(String channel, String symbol) {
            Set<String> symbols = channelSymbols.get(channel);
            return symbols != null && symbols.contains(symbol);
        }

        private Set<String> allSubscribedSymbols() {
            return channelSymbols.values().stream()
                    .flatMap(Set::stream)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
    }

    private static final class WebSocketHeartbeatThreadFactory implements ThreadFactory {
        private int index;

        @Override
        public synchronized Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "market-websocket-heartbeat-" + (++index));
            thread.setDaemon(true);
            return thread;
        }
    }
}
