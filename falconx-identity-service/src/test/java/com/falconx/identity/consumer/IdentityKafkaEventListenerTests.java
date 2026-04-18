package com.falconx.identity.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.falconx.identity.config.IdentityServiceProperties;
import com.falconx.trading.contract.event.DepositCreditedEventPayload;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * `IdentityKafkaEventListener` 单元测试。
 *
 * <p>该测试确认 identity-service 已经具备 Kafka 事件适配入口。
 */
class IdentityKafkaEventListenerTests {

    @Test
    void shouldDeserializeKafkaPayloadAndDelegateToDomainConsumer() throws Exception {
        DepositCreditedEventConsumer consumer = mock(DepositCreditedEventConsumer.class);
        IdentityKafkaEventListener listener = new IdentityKafkaEventListener(
                new ObjectMapper().findAndRegisterModules(),
                new IdentityServiceProperties(),
                consumer
        );
        DepositCreditedEventPayload payload = new DepositCreditedEventPayload(
                1001L,
                2002L,
                3003L,
                "ETH",
                "USDT",
                "0xhash",
                new BigDecimal("99.99"),
                OffsetDateTime.parse("2026-04-17T12:00:00Z")
        );

        listener.onDepositCredited(
                new ObjectMapper().findAndRegisterModules().writeValueAsString(payload),
                "evt-40001",
                "1234567890abcdef1234567890abcdef"
        );

        verify(consumer).handle(eq("evt-40001"), eq(payload));
    }
}
