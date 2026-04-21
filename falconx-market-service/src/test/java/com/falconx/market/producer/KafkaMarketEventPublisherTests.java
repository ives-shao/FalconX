package com.falconx.market.producer;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.falconx.infrastructure.id.IdGenerator;
import com.falconx.infrastructure.kafka.KafkaEventHeaderConstants;
import com.falconx.market.config.MarketServiceProperties;
import com.falconx.market.contract.event.MarketPriceTickEventPayload;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * `KafkaMarketEventPublisher` 单元测试。
 *
 * <p>该测试用于验证 market-service 已经真正走 KafkaTemplate 发送事件，
 * 而不是继续停留在日志型 publisher。
 */
class KafkaMarketEventPublisherTests {

    @Test
    void shouldPublishPriceTickToKafkaTemplate() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(any(Message.class))).thenReturn(CompletableFuture.completedFuture(null));

        KafkaMarketEventPublisher publisher = new KafkaMarketEventPublisher(
                kafkaTemplate,
                JsonMapper.builder().build(),
                fixedIdGenerator(),
                new MarketServiceProperties()
        );

        publisher.publishPriceTick(new MarketPriceTickEventPayload(
                "BTCUSDT",
                new BigDecimal("100"),
                new BigDecimal("101"),
                new BigDecimal("100.5"),
                new BigDecimal("100.4"),
                OffsetDateTime.parse("2026-04-17T12:00:00Z"),
                "unit-test",
                false
        ));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Message<String>> captor = ArgumentCaptor.forClass(Message.class);
        verify(kafkaTemplate).send(captor.capture());
        Message<String> message = captor.getValue();
        Assertions.assertEquals("falconx.market.price.tick", message.getHeaders().get("kafka_topic"));
        Assertions.assertEquals("BTCUSDT", message.getHeaders().get("kafka_messageKey"));
        Assertions.assertEquals("evt-10001", message.getHeaders().get(KafkaEventHeaderConstants.EVENT_ID_HEADER));
        Assertions.assertTrue(message.getPayload().contains("\"symbol\":\"BTCUSDT\""));
    }

    private IdGenerator fixedIdGenerator() {
        return () -> 10001L;
    }
}
