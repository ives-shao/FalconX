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

    @Test
    void shouldIgnoreUnknownFieldsWhenDeserializingDepositCreditedPayload() throws Exception {
        DepositCreditedEventConsumer consumer = mock(DepositCreditedEventConsumer.class);
        IdentityKafkaEventListener listener = new IdentityKafkaEventListener(
                new ObjectMapper().findAndRegisterModules(),
                new IdentityServiceProperties(),
                consumer
        );
        DepositCreditedEventPayload payload = new DepositCreditedEventPayload(
                1002L,
                2003L,
                3004L,
                "ETH",
                "USDT",
                "0xhash-extra",
                new BigDecimal("88.88"),
                OffsetDateTime.parse("2026-04-20T12:00:00Z")
        );

        listener.onDepositCredited(
                """
                {"depositId":1002,"userId":2003,"accountId":3004,"chain":"ETH","token":"USDT","txHash":"0xhash-extra","amount":"88.88","creditedAt":"2026-04-20T12:00:00Z","newField":"ignored"}
                """,
                "evt-40002",
                "1234567890abcdef1234567890abcdef"
        );

        verify(consumer).handle(eq("evt-40002"), eq(payload));
    }
}
