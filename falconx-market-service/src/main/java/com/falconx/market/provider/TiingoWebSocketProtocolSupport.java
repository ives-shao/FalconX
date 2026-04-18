package com.falconx.market.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Comparator;
import org.springframework.stereotype.Component;

/**
 * Tiingo WebSocket 协议支持组件。
 *
 * <p>这里把“订阅报文构造”和“入站消息解析”收敛成一处可测试组件，避免把协议细节散落在 Provider 里。
 * 当前实现以项目内已冻结的真实 Tiingo 协议样例为准：
 *
 * <ol>
 *   <li>订阅报文使用 `eventData.authToken`，按全品种订阅建立连接</li>
 *   <li>入站消息优先解析官方样例中的 `data` 数组形态</li>
 *   <li>`messageType=I/H/E` 等非报价帧不会进入标准报价对象</li>
 *   <li>同时保留对象形态的宽松解析，避免后续协议小改动导致 provider 立即失效</li>
 * </ol>
 */
@Component
public class TiingoWebSocketProtocolSupport {

    private static final List<String> TICKER_FIELDS = List.of("ticker", "symbol", "pair", "instrument");
    private static final List<String> BID_FIELDS = List.of("bidPrice", "bid", "topBidPrice");
    private static final List<String> ASK_FIELDS = List.of("askPrice", "ask", "topAskPrice");
    private static final List<String> TIMESTAMP_FIELDS = List.of("quoteTimestamp", "timestamp", "ts", "date");

    private final ObjectMapper objectMapper;

    public TiingoWebSocketProtocolSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 构造 Tiingo 订阅报文。
     *
     * <p>当前 Tiingo Forex WebSocket 的项目内冻结格式是“全品种订阅”，
     * 因此这里不再拼装 ticker 列表，而是只发送 `eventName + eventData.authToken`。
     *
     * @param apiKey Tiingo API Key
     * @return JSON 字符串
     */
    public String buildSubscribeMessage(String apiKey) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "eventName", "subscribe",
                    "eventData", Map.of(
                            "authToken", apiKey
                    )
            ));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize Tiingo subscribe message", exception);
        }
    }

    /**
     * 解析一帧 Tiingo 文本消息中的全部报价。
     *
     * <p>当前解析优先覆盖两类报文：
     *
     * <ul>
     *   <li>官方样例：`{\"service\":\"fx\",\"messageType\":\"A\",\"data\":[...]}`</li>
     *   <li>兼容形态：根对象或 `data/eventData` 内嵌对象报价</li>
     * </ul>
     *
     * <p>对于 Tiingo 文档中出现的 `messageType=I/H/E` 等元数据、心跳或错误帧，
     * 当前策略是“不构造报价对象，直接返回空列表”。
     *
     * @param messageText WebSocket 文本帧
     * @return 成功解析出的报价列表；不识别的报文返回空列表
     */
    public List<TiingoRawQuote> parseQuotes(String messageText) {
        try {
            JsonNode root = objectMapper.readTree(messageText);
            List<TiingoRawQuote> quotes = new ArrayList<>();
            collectQuotes(root, quotes);
            return deduplicate(quotes);
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    /**
     * 解析 Tiingo 入站帧中的连接级元数据。
     *
     * <p>当前只提取 provider 真正会消费的最小字段，用于：
     *
     * <ul>
     *   <li>订阅成功确认日志</li>
     *   <li>心跳日志</li>
     *   <li>错误帧日志</li>
     * </ul>
     *
     * <p>注意这里不会把 `A/U/D` 之类报价数据帧转换为业务报价对象，
     * 报价仍然由 {@link #parseQuotes(String)} 负责。
     *
     * @param messageText WebSocket 文本帧
     * @return 若该帧包含可识别的元数据，则返回元数据对象
     */
    public Optional<TiingoInboundFrameMetadata> parseFrameMetadata(String messageText) {
        try {
            JsonNode root = objectMapper.readTree(messageText);
            return toFrameMetadata(root)
                    .or(() -> {
                        JsonNode dataNode = root.get("data");
                        return toFrameMetadata(dataNode);
                    })
                    .or(() -> {
                        JsonNode eventDataNode = root.get("eventData");
                        return toFrameMetadata(eventDataNode);
                    });
        } catch (JsonProcessingException exception) {
            return Optional.empty();
        }
    }

    /**
     * 从 Tiingo crypto 成交流里解析可落库的 symbol 候选。
     *
     * <p>当前只接受：
     *
     * <ul>
     *   <li>`messageType=A`</li>
     *   <li>`data[0]=T` 的成交帧</li>
     *   <li>来自允许交易所白名单的 symbol</li>
     *   <li>可以拆分为“base + quote”且长度合理的标准交易对</li>
     * </ul>
     *
     * <p>像 Tiingo 聚合进来的 AMM 池子符号（常见于 `uniswap* / raydium / pharaoh ...`）
     * 不会通过这里的过滤逻辑，避免污染 `t_symbol`。
     *
     * @param messageText WebSocket 文本帧
     * @param cryptoSymbolImport crypto symbol 导入配置
     * @return 若该帧可抽取出一个可落库 symbol，则返回候选对象
     */
    public Optional<TiingoDiscoveredCryptoSymbol> parseDiscoveredCryptoSymbol(
            String messageText,
            com.falconx.market.config.MarketServiceProperties.CryptoSymbolImport cryptoSymbolImport
    ) {
        try {
            JsonNode root = objectMapper.readTree(messageText);
            if (!"A".equalsIgnoreCase(root.path("messageType").asText())) {
                return Optional.empty();
            }
            JsonNode dataNode = root.get("data");
            if (dataNode == null || !dataNode.isArray() || dataNode.size() < 6) {
                return Optional.empty();
            }
            if (!"T".equalsIgnoreCase(dataNode.path(0).asText())) {
                return Optional.empty();
            }
            String exchange = dataNode.path(3).asText("");
            if (exchange.isBlank() || !isAllowedExchange(exchange, cryptoSymbolImport.getAllowedExchanges())) {
                return Optional.empty();
            }
            String symbol = normalizeTicker(dataNode.path(1).asText(""));
            Optional<SymbolParts> symbolParts = splitSymbol(
                    symbol,
                    cryptoSymbolImport.getQuoteCurrencies(),
                    cryptoSymbolImport.getMinBaseLength(),
                    cryptoSymbolImport.getMaxBaseLength()
            );
            if (symbolParts.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new TiingoDiscoveredCryptoSymbol(
                    symbol,
                    symbolParts.get().baseCurrency(),
                    symbolParts.get().quoteCurrency(),
                    exchange.toLowerCase(),
                    parseTimestamp(dataNode.path(2)),
                    parseDecimal(dataNode.path(4)),
                    parseDecimal(dataNode.path(5))
            ));
        } catch (JsonProcessingException exception) {
            return Optional.empty();
        }
    }

    private void collectQuotes(JsonNode node, List<TiingoRawQuote> quotes) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            toQuoteFromArray(node).ifPresent(quotes::add);
            node.forEach(child -> collectQuotes(child, quotes));
            return;
        }
        if (!node.isObject()) {
            return;
        }
        toQuote(node).ifPresent(quotes::add);
        collectQuotes(node.get("data"), quotes);
        collectQuotes(node.get("eventData"), quotes);
    }

    private Optional<TiingoRawQuote> toQuote(JsonNode node) {
        JsonNode dataNode = node.get("data");
        if (dataNode != null && dataNode.isArray()) {
            Optional<TiingoRawQuote> arrayQuote = toQuoteFromArray(dataNode);
            if (arrayQuote.isPresent()) {
                return arrayQuote;
            }
        }
        String ticker = firstText(node, TICKER_FIELDS).map(this::normalizeTicker).orElse(null);
        BigDecimal bid = firstDecimal(node, BID_FIELDS).orElse(null);
        BigDecimal ask = firstDecimal(node, ASK_FIELDS).orElse(null);
        OffsetDateTime timestamp = firstTimestamp(node, TIMESTAMP_FIELDS).orElse(null);
        if (ticker == null || bid == null || ask == null || timestamp == null) {
            return Optional.empty();
        }
        return Optional.of(new TiingoRawQuote(ticker, bid, ask, timestamp));
    }

    private Optional<TiingoInboundFrameMetadata> toFrameMetadata(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Optional.empty();
        }

        String messageType = firstText(node, List.of("messageType")).orElse(null);
        String service = firstText(node, List.of("service")).orElse(null);
        Long subscriptionId = firstLong(node, List.of("subscriptionId")).orElse(null);

        JsonNode responseNode = node.get("response");
        Integer responseCode = firstInteger(node, List.of("code")).orElse(null);
        String responseMessage = firstText(node, List.of("message")).orElse(null);
        if (responseNode != null && responseNode.isObject()) {
            if (responseCode == null) {
                responseCode = firstInteger(responseNode, List.of("code")).orElse(null);
            }
            if (responseMessage == null) {
                responseMessage = firstText(responseNode, List.of("message")).orElse(null);
            }
        }

        if (messageType == null && subscriptionId == null && responseCode == null && responseMessage == null && service == null) {
            return Optional.empty();
        }
        return Optional.of(new TiingoInboundFrameMetadata(
                messageType,
                service,
                subscriptionId,
                responseCode,
                responseMessage
        ));
    }

    /**
     * 解析 Tiingo 当前真实样例中的数组报价。
     *
     * <p>当前项目冻结的样例是：
     *
     * <pre>
     * ["Q","xptusd","2026-04-17T16:35:36.534000+00:00",1000000.0,2132.35,2135.25,1000000.0,2138.15]
     * </pre>
     *
     * 这里按该样例把：
     *
     * <ul>
     *   <li>`[1]` 解释为 symbol</li>
     *   <li>`[2]` 解释为时间戳</li>
     *   <li>`[4]` 解释为 bid</li>
     *   <li>`[7]` 解释为 ask</li>
     * </ul>
     *
     * 中间的 `[3] / [6]` 当前视为盘口量字段，`[5]` 可能是中间价或平台附加字段，
     * 一期市场链路不直接消费，因此这里不落入标准报价对象。
     */
    private Optional<TiingoRawQuote> toQuoteFromArray(JsonNode node) {
        if (node == null || !node.isArray() || node.size() < 8) {
            return Optional.empty();
        }
        String messageType = node.get(0).asText();
        if (messageType == null || messageType.isBlank() || !"Q".equalsIgnoreCase(messageType)) {
            return Optional.empty();
        }
        String ticker = normalizeTicker(node.get(1).asText());
        OffsetDateTime timestamp = parseTimestamp(node.get(2));
        BigDecimal bid = parseDecimal(node.get(4));
        BigDecimal ask = parseDecimal(node.get(7));
        if (ticker == null || ticker.isBlank() || timestamp == null || bid == null || ask == null) {
            return Optional.empty();
        }
        return Optional.of(new TiingoRawQuote(ticker, bid, ask, timestamp));
    }

    private Optional<String> firstText(JsonNode node, List<String> fieldNames) {
        return fieldNames.stream()
                .map(node::get)
                .filter(field -> field != null && !field.isNull())
                .map(JsonNode::asText)
                .filter(value -> !value.isBlank())
                .findFirst();
    }

    private Optional<BigDecimal> firstDecimal(JsonNode node, List<String> fieldNames) {
        return fieldNames.stream()
                .map(node::get)
                .filter(field -> field != null && !field.isNull())
                .map(this::parseDecimal)
                .filter(value -> value != null)
                .findFirst();
    }

    private Optional<Integer> firstInteger(JsonNode node, List<String> fieldNames) {
        return fieldNames.stream()
                .map(node::get)
                .filter(field -> field != null && !field.isNull())
                .map(JsonNode::asInt)
                .findFirst();
    }

    private Optional<Long> firstLong(JsonNode node, List<String> fieldNames) {
        return fieldNames.stream()
                .map(node::get)
                .filter(field -> field != null && !field.isNull())
                .map(JsonNode::asLong)
                .findFirst();
    }

    private BigDecimal parseDecimal(JsonNode field) {
        if (field == null || field.isNull()) {
            return null;
        }
        try {
            return new BigDecimal(field.asText());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Optional<OffsetDateTime> firstTimestamp(JsonNode node, List<String> fieldNames) {
        return fieldNames.stream()
                .map(node::get)
                .filter(field -> field != null && !field.isNull())
                .map(this::parseTimestamp)
                .filter(value -> value != null)
                .findFirst();
    }

    private OffsetDateTime parseTimestamp(JsonNode field) {
        String rawText = field.asText();
        if (rawText == null || rawText.isBlank()) {
            return null;
        }
        try {
            if (rawText.matches("^\\d{10,}$")) {
                long epochValue = Long.parseLong(rawText);
                if (rawText.length() == 10) {
                    return OffsetDateTime.ofInstant(Instant.ofEpochSecond(epochValue), ZoneOffset.UTC);
                }
                return OffsetDateTime.ofInstant(Instant.ofEpochMilli(epochValue), ZoneOffset.UTC);
            }
            return OffsetDateTime.parse(rawText);
        } catch (DateTimeParseException | NumberFormatException exception) {
            return null;
        }
    }

    private String normalizeTicker(String rawTicker) {
        return rawTicker.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    }

    private boolean isAllowedExchange(String exchange, List<String> allowedExchanges) {
        return allowedExchanges.stream()
                .map(value -> value == null ? "" : value.trim().toLowerCase())
                .filter(value -> !value.isBlank())
                .anyMatch(exchange.toLowerCase()::equals);
    }

    private Optional<SymbolParts> splitSymbol(String symbol,
                                              List<String> quoteCurrencies,
                                              int minBaseLength,
                                              int maxBaseLength) {
        if (symbol == null || symbol.isBlank()) {
            return Optional.empty();
        }
        return quoteCurrencies.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::toUpperCase)
                .sorted(Comparator.comparingInt(String::length).reversed())
                .filter(symbol::endsWith)
                .map(quoteCurrency -> new SymbolParts(
                        symbol.substring(0, symbol.length() - quoteCurrency.length()),
                        quoteCurrency
                ))
                .filter(parts -> parts.baseCurrency().length() >= minBaseLength
                        && parts.baseCurrency().length() <= maxBaseLength)
                .findFirst();
    }

    private List<TiingoRawQuote> deduplicate(List<TiingoRawQuote> quotes) {
        Set<String> seenKeys = new LinkedHashSet<>();
        List<TiingoRawQuote> deduplicated = new ArrayList<>();
        for (TiingoRawQuote quote : quotes) {
            String key = quote.ticker() + "|" + quote.ts() + "|" + quote.bid() + "|" + quote.ask();
            if (seenKeys.add(key)) {
                deduplicated.add(quote);
            }
        }
        return deduplicated;
    }

    private record SymbolParts(String baseCurrency, String quoteCurrency) {
    }
}
