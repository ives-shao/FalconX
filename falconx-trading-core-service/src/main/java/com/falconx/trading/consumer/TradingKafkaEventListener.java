package com.falconx.trading.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.falconx.infrastructure.kafka.KafkaEventHeaderConstants;
import com.falconx.infrastructure.kafka.KafkaEventMessageSupport;
import com.falconx.market.contract.event.MarketPriceTickEventPayload;
import com.falconx.trading.config.TradingCoreServiceProperties;
import com.falconx.wallet.contract.event.WalletDepositConfirmedEventPayload;
import com.falconx.wallet.contract.event.WalletDepositReversedEventPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * trading-core Kafka 监听适配器。
 *
 * <p>该组件承担“Kafka 框架入口 -> 领域消费者”的适配职责：
 *
 * <ul>
 *   <li>从消息头提取 `eventId` 与 `traceId`</li>
 *   <li>把 JSON 字符串反序列化为契约 payload</li>
 *   <li>调用既有的业务消费者完成领域处理</li>
 * </ul>
 *
 * <p>这样可以保持原有 `*EventConsumer` 类只关注业务语义，不直接依赖 Kafka 注解和字符串反序列化。
 */
@Component
public class TradingKafkaEventListener {

    private static final Logger log = LoggerFactory.getLogger(TradingKafkaEventListener.class);

    private final ObjectMapper objectMapper;
    private final TradingCoreServiceProperties properties;
    private final MarketPriceTickEventConsumer marketPriceTickEventConsumer;
    private final WalletDepositConfirmedEventConsumer walletDepositConfirmedEventConsumer;
    private final WalletDepositReversedEventConsumer walletDepositReversedEventConsumer;

    public TradingKafkaEventListener(ObjectMapper objectMapper,
                                     TradingCoreServiceProperties properties,
                                     MarketPriceTickEventConsumer marketPriceTickEventConsumer,
                                     WalletDepositConfirmedEventConsumer walletDepositConfirmedEventConsumer,
                                     WalletDepositReversedEventConsumer walletDepositReversedEventConsumer) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.marketPriceTickEventConsumer = marketPriceTickEventConsumer;
        this.walletDepositConfirmedEventConsumer = walletDepositConfirmedEventConsumer;
        this.walletDepositReversedEventConsumer = walletDepositReversedEventConsumer;
    }

    /**
     * 消费市场高频价格事件。
     *
     * @param payloadJson Kafka 里的 JSON payload
     * @param eventId 事件 ID
     * @param traceId 链路 traceId
     */
    @KafkaListener(
            topics = "${falconx.trading.kafka.market-price-tick-topic}",
            groupId = "${falconx.trading.kafka.consumer-group-id}"
    )
    public void onMarketPriceTick(String payloadJson,
                                  @Header(KafkaEventHeaderConstants.EVENT_ID_HEADER) String eventId,
                                  @Header(value = KafkaEventHeaderConstants.TRACE_ID_HEADER, required = false)
                                  String traceId) {
        withTraceId(traceId, () -> {
            log.info("trading.kafka.consume.received topic={} eventId={}",
                    properties.getKafka().getMarketPriceTickTopic(),
                    eventId);
            marketPriceTickEventConsumer.consume(
                    eventId,
                    objectMapper.readValue(payloadJson, MarketPriceTickEventPayload.class)
            );
        });
    }

    /**
     * 消费钱包确认入金事件。
     *
     * @param payloadJson Kafka 里的 JSON payload
     * @param eventId 事件 ID
     * @param traceId 链路 traceId
     */
    @KafkaListener(
            topics = "${falconx.trading.kafka.wallet-deposit-confirmed-topic}",
            groupId = "${falconx.trading.kafka.consumer-group-id}"
    )
    public void onWalletDepositConfirmed(String payloadJson,
                                         @Header(KafkaEventHeaderConstants.EVENT_ID_HEADER) String eventId,
                                         @Header(value = KafkaEventHeaderConstants.TRACE_ID_HEADER, required = false)
                                         String traceId) {
        withTraceId(traceId, () -> {
            log.info("trading.kafka.consume.received topic={} eventId={}",
                    properties.getKafka().getWalletDepositConfirmedTopic(),
                    eventId);
            walletDepositConfirmedEventConsumer.consume(
                    eventId,
                    objectMapper.readValue(payloadJson, WalletDepositConfirmedEventPayload.class)
            );
        });
    }

    /**
     * 消费钱包回滚事件。
     *
     * @param payloadJson Kafka 里的 JSON payload
     * @param eventId 事件 ID
     * @param traceId 链路 traceId
     */
    @KafkaListener(
            topics = "${falconx.trading.kafka.wallet-deposit-reversed-topic}",
            groupId = "${falconx.trading.kafka.consumer-group-id}"
    )
    public void onWalletDepositReversed(String payloadJson,
                                        @Header(KafkaEventHeaderConstants.EVENT_ID_HEADER) String eventId,
                                        @Header(value = KafkaEventHeaderConstants.TRACE_ID_HEADER, required = false)
                                        String traceId) {
        withTraceId(traceId, () -> {
            log.info("trading.kafka.consume.received topic={} eventId={}",
                    properties.getKafka().getWalletDepositReversedTopic(),
                    eventId);
            walletDepositReversedEventConsumer.consume(
                    eventId,
                    objectMapper.readValue(payloadJson, WalletDepositReversedEventPayload.class)
            );
        });
    }

    /**
     * 统一处理 Kafka 消费线程的 traceId 注入与异常包装。
     *
     * @param traceIdHeader Kafka 头中的 traceId
     * @param action 具体消费动作
     */
    private void withTraceId(String traceIdHeader, ThrowingRunnable action) {
        KafkaEventMessageSupport.bindTraceId(traceIdHeader);
        try {
            action.run();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to process Kafka event in trading-core-service", exception);
        } finally {
            KafkaEventMessageSupport.clearTraceId();
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
