package com.falconx.market.producer;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.falconx.infrastructure.id.IdGenerator;
import com.falconx.infrastructure.kafka.KafkaEventMessageSupport;
import com.falconx.market.config.MarketServiceProperties;
import com.falconx.market.contract.event.MarketKlineUpdateEventPayload;
import com.falconx.market.contract.event.MarketPriceTickEventPayload;
import com.falconx.market.entity.MarketOutboxMessage;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * market-service 的 Kafka 事件发布实现。
 *
 * <p>该实现负责把市场域的价格事件与 K 线事件真正发送到 Kafka，
 * 取代前期只打印日志的占位 publisher。
 *
 * <p>为了让当前阶段的集成测试结果可判定，该实现采用“同步等待发送结果”的方式：
 *
 * <ul>
 *   <li>发送成功后方法才返回</li>
 *   <li>发送失败时直接抛出异常，避免上层误判为事件已送达</li>
 * </ul>
 */
@Component
public class KafkaMarketEventPublisher implements MarketEventPublisher, MarketOutboxEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaMarketEventPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final IdGenerator idGenerator;
    private final MarketServiceProperties properties;

    public KafkaMarketEventPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                     ObjectMapper objectMapper,
                                     IdGenerator idGenerator,
                                     MarketServiceProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.idGenerator = idGenerator;
        this.properties = properties;
    }

    @Override
    public void publishPriceTick(MarketPriceTickEventPayload payload) {
        send(
                properties.getKafka().getPriceTickTopic(),
                payload.symbol(),
                payload,
                "market.price.tick",
                null
        );
    }

    @Override
    public void publishKlineUpdate(MarketKlineUpdateEventPayload payload) {
        send(
                properties.getKafka().getKlineUpdateTopic(),
                payload.symbol() + ":" + payload.interval(),
                payload,
                "market.kline.update",
                null
        );
    }

    @Override
    public void publish(MarketOutboxMessage message) {
        send(
                resolveTopic(message.eventType()),
                message.partitionKey(),
                message.payload(),
                message.eventType(),
                message.eventId()
        );
    }

    /**
     * 发送一条市场事件到 Kafka。
     *
     * @param topic 目标 topic
     * @param partitionKey 分区键
     * @param payload 事件 payload
     * @param eventType 事件类型
     */
    private void send(String topic, String partitionKey, Object payload, String eventType, String existingEventId) {
        String eventId = existingEventId == null ? "evt-" + idGenerator.nextId() : existingEventId;
        String payloadJson = toJson(payload);
        try {
            kafkaTemplate.send(KafkaEventMessageSupport.buildJsonMessage(
                            topic,
                            partitionKey,
                            payloadJson,
                            eventId,
                            eventType,
                            "falconx-market-service"
                    ))
                    .get(5, TimeUnit.SECONDS);
            log.info("market.event.publish.completed topic={} eventType={} eventId={} partitionKey={}",
                    topic,
                    eventType,
                    eventId,
                    partitionKey);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to publish market event to Kafka: " + eventType, exception);
        }
    }

    private String resolveTopic(String eventType) {
        if ("market.kline.update".equals(eventType)) {
            return properties.getKafka().getKlineUpdateTopic();
        }
        if ("market.price.tick".equals(eventType)) {
            return properties.getKafka().getPriceTickTopic();
        }
        throw new IllegalStateException("Unsupported market event type: " + eventType);
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to serialize market event payload", exception);
        }
    }
}
