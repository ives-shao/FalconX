package com.falconx.market.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.falconx.market.config.MarketServiceProperties;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tiingo WebSocket 协议支持单元测试。
 *
 * <p>该测试只验证本地协议处理逻辑，不依赖真实外网连接。
 * 重点覆盖两件事：
 *
 * <ol>
 *   <li>订阅报文是否按当前真实 Tiingo 协议字段构造</li>
 *   <li>入站 JSON 是否能从 Tiingo 当前数组形态中提取报价</li>
 * </ol>
 */
class TiingoWebSocketProtocolSupportTests {

    private final TiingoWebSocketProtocolSupport protocolSupport = new TiingoWebSocketProtocolSupport(new ObjectMapper());

    @Test
    void shouldBuildSubscribeMessageWithAuthToken() {
        String subscribeMessage = protocolSupport.buildSubscribeMessage("api-key");

        Assertions.assertTrue(subscribeMessage.contains("\"eventName\":\"subscribe\""));
        Assertions.assertTrue(subscribeMessage.contains("\"eventData\":{\"authToken\":\"api-key\"}"));
    }

    @Test
    void shouldParseQuotesFromCurrentTiingoArrayPayload() {
        String inboundMessage = """
                {
                  "service":"fx",
                  "messageType":"A",
                  "data":[
                    "Q",
                    "xptusd",
                    "2026-04-17T16:35:36.534000+00:00",
                    1000000.0,
                    2132.35,
                    2135.25,
                    1000000.0,
                    2138.15
                  ]
                }
                """;

        List<TiingoRawQuote> quotes = protocolSupport.parseQuotes(inboundMessage);

        Assertions.assertEquals(1, quotes.size());
        Assertions.assertEquals("XPTUSD", quotes.getFirst().ticker());
        Assertions.assertEquals("2132.35", quotes.getFirst().bid().toPlainString());
        Assertions.assertEquals("2138.15", quotes.getFirst().ask().toPlainString());
    }

    @Test
    void shouldIgnoreNonQuoteArrayPayloadEvenWhenArrayShapeMatches() {
        String inboundMessage = """
                {
                  "service":"fx",
                  "messageType":"A",
                  "data":[
                    "T",
                    "xptusd",
                    "2026-04-17T16:35:36.534000+00:00",
                    1000000.0,
                    2132.35,
                    2135.25,
                    1000000.0,
                    2138.15
                  ]
                }
                """;

        List<TiingoRawQuote> quotes = protocolSupport.parseQuotes(inboundMessage);

        Assertions.assertTrue(quotes.isEmpty());
    }

    @Test
    void shouldRemainCompatibleWithNestedObjectPayload() {
        String inboundMessage = """
                {
                  "service":"fx",
                  "messageType":"A",
                  "data":{
                    "ticker":"EUR/USD",
                    "bidPrice":"1.08321",
                    "askPrice":"1.08331",
                    "quoteTimestamp":"2026-04-18T10:00:00Z"
                  }
                }
                """;

        List<TiingoRawQuote> quotes = protocolSupport.parseQuotes(inboundMessage);

        Assertions.assertEquals(1, quotes.size());
        Assertions.assertEquals("EURUSD", quotes.getFirst().ticker());
    }

    @Test
    void shouldIgnoreInformationalSubscriptionMessage() {
        String informationalMessage = """
                {
                  "data":{
                    "subscriptionId":51,
                    "response":{
                      "message":"Success",
                      "code":200
                    },
                    "messageType":"I"
                  }
                }
                """;

        List<TiingoRawQuote> quotes = protocolSupport.parseQuotes(informationalMessage);
        TiingoInboundFrameMetadata metadata = protocolSupport.parseFrameMetadata(informationalMessage).orElseThrow();

        Assertions.assertTrue(quotes.isEmpty());
        Assertions.assertTrue(metadata.isInformational());
        Assertions.assertEquals(51L, metadata.subscriptionId());
        Assertions.assertEquals(200, metadata.responseCode());
        Assertions.assertEquals("Success", metadata.responseMessage());
    }

    @Test
    void shouldIgnoreHeartbeatMessage() {
        String heartbeatMessage = """
                {
                  "response":{
                    "message":"HeartBeat",
                    "code":200
                  },
                  "messageType":"H"
                }
                """;

        List<TiingoRawQuote> quotes = protocolSupport.parseQuotes(heartbeatMessage);
        TiingoInboundFrameMetadata metadata = protocolSupport.parseFrameMetadata(heartbeatMessage).orElseThrow();

        Assertions.assertTrue(quotes.isEmpty());
        Assertions.assertTrue(metadata.isHeartbeat());
        Assertions.assertEquals(200, metadata.responseCode());
        Assertions.assertEquals("HeartBeat", metadata.responseMessage());
    }

    @Test
    void shouldParseErrorFrameMetadata() {
        TiingoInboundFrameMetadata metadata = protocolSupport.parseFrameMetadata("""
                {
                  "service":"fx",
                  "messageType":"E",
                  "response":{
                    "message":"Unauthorized",
                    "code":401
                  }
                }
                """).orElseThrow();

        Assertions.assertTrue(metadata.isError());
        Assertions.assertEquals("fx", metadata.service());
        Assertions.assertEquals(401, metadata.responseCode());
        Assertions.assertEquals("Unauthorized", metadata.responseMessage());
    }

    @Test
    void shouldParseDiscoveredCryptoSymbolFromCentralizedExchangeTrade() {
        MarketServiceProperties.CryptoSymbolImport cryptoSymbolImport = new MarketServiceProperties.CryptoSymbolImport();
        cryptoSymbolImport.setAllowedExchanges(List.of("binance", "gdax", "huobi"));
        cryptoSymbolImport.setQuoteCurrencies(List.of("USDT", "USDC", "USD", "EUR"));

        TiingoDiscoveredCryptoSymbol discoveredSymbol = protocolSupport.parseDiscoveredCryptoSymbol("""
                {
                  "service":"crypto_data",
                  "messageType":"A",
                  "data":[
                    "T",
                    "solusdt",
                    "2026-04-18T03:27:39.737000+00:00",
                    "binance",
                    8.39,
                    126.968
                  ]
                }
                """, cryptoSymbolImport).orElseThrow();

        Assertions.assertEquals("SOLUSDT", discoveredSymbol.symbol());
        Assertions.assertEquals("SOL", discoveredSymbol.baseCurrency());
        Assertions.assertEquals("USDT", discoveredSymbol.quoteCurrency());
        Assertions.assertEquals("binance", discoveredSymbol.exchange());
        Assertions.assertEquals("126.968", discoveredSymbol.price().toPlainString());
    }

    @Test
    void shouldIgnoreCryptoTradeFromUnsupportedExchange() {
        MarketServiceProperties.CryptoSymbolImport cryptoSymbolImport = new MarketServiceProperties.CryptoSymbolImport();
        cryptoSymbolImport.setAllowedExchanges(List.of("binance", "gdax", "huobi"));

        Assertions.assertTrue(protocolSupport.parseDiscoveredCryptoSymbol("""
                {
                  "service":"crypto_data",
                  "messageType":"A",
                  "data":[
                    "T",
                    "tngospusdcusdt",
                    "2026-04-18T03:27:39+00:00",
                    "raydium",
                    0.0000001,
                    0.9999
                  ]
                }
                """, cryptoSymbolImport).isEmpty());
    }
}
