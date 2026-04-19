package com.falconx.trading.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.falconx.market.contract.event.MarketPriceTickEventPayload;
import com.falconx.trading.config.TradingCoreServiceProperties;
import com.falconx.wallet.contract.event.WalletDepositConfirmedEventPayload;
import com.falconx.wallet.contract.event.WalletDepositReversedEventPayload;
import com.falconx.domain.enums.ChainType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * `TradingKafkaEventListener` 单元测试。
 *
 * <p>该测试确认 trading-core-service 的 Kafka listener 会完成 JSON 反序列化并委托给领域消费者。
 */
class TradingKafkaEventListenerTests {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldDelegateMarketPriceTick() throws Exception {
        MarketPriceTickEventConsumer marketConsumer = mock(MarketPriceTickEventConsumer.class);
        TradingKafkaEventListener listener = new TradingKafkaEventListener(
                objectMapper,
                new TradingCoreServiceProperties(),
                marketConsumer,
                mock(WalletDepositConfirmedEventConsumer.class),
                mock(WalletDepositReversedEventConsumer.class)
        );
        MarketPriceTickEventPayload payload = new MarketPriceTickEventPayload(
                "BTCUSDT",
                new BigDecimal("100"),
                new BigDecimal("101"),
                new BigDecimal("100.5"),
                new BigDecimal("100.4"),
                OffsetDateTime.parse("2026-04-17T12:00:00Z"),
                "unit-test",
                false
        );

        listener.onMarketPriceTick(
                objectMapper.writeValueAsString(payload),
                "evt-50001",
                "1234567890abcdef1234567890abcdef"
        );

        verify(marketConsumer).consume(eq("evt-50001"), eq(payload));
        verifyNoMoreInteractions(marketConsumer);
    }

    @Test
    void shouldDelegateWalletDepositConfirmed() throws Exception {
        WalletDepositConfirmedEventConsumer walletConfirmedConsumer = mock(WalletDepositConfirmedEventConsumer.class);
        TradingKafkaEventListener listener = new TradingKafkaEventListener(
                objectMapper,
                new TradingCoreServiceProperties(),
                mock(MarketPriceTickEventConsumer.class),
                walletConfirmedConsumer,
                mock(WalletDepositReversedEventConsumer.class)
        );
        WalletDepositConfirmedEventPayload payload = new WalletDepositConfirmedEventPayload(
                77001L,
                9001L,
                ChainType.ETH,
                "USDT",
                "0xhash",
                "0xfrom",
                "0xto",
                new BigDecimal("11.5"),
                12,
                12,
                OffsetDateTime.parse("2026-04-17T12:00:00Z")
        );

        listener.onWalletDepositConfirmed(
                objectMapper.writeValueAsString(payload),
                "evt-50002",
                "1234567890abcdef1234567890abcdef"
        );

        verify(walletConfirmedConsumer).consume(eq("evt-50002"), eq(payload));
    }

    @Test
    void shouldDelegateWalletDepositReversed() throws Exception {
        WalletDepositReversedEventConsumer walletReversedConsumer = mock(WalletDepositReversedEventConsumer.class);
        TradingKafkaEventListener listener = new TradingKafkaEventListener(
                objectMapper,
                new TradingCoreServiceProperties(),
                mock(MarketPriceTickEventConsumer.class),
                mock(WalletDepositConfirmedEventConsumer.class),
                walletReversedConsumer
        );
        WalletDepositReversedEventPayload payload = new WalletDepositReversedEventPayload(
                77002L,
                9002L,
                ChainType.TRON,
                "USDT",
                "0xreversed",
                "from",
                "to",
                new BigDecimal("21"),
                2,
                19,
                OffsetDateTime.parse("2026-04-17T12:00:00Z")
        );

        listener.onWalletDepositReversed(
                objectMapper.writeValueAsString(payload),
                "evt-50003",
                "1234567890abcdef1234567890abcdef"
        );

        verify(walletReversedConsumer).consume(eq("evt-50003"), eq(payload));
    }
}
