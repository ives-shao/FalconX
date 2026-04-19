package com.falconx.trading.consumer;

import com.falconx.infrastructure.kafka.KafkaEventMessageSupport;
import com.falconx.infrastructure.trace.TraceIdConstants;
import com.falconx.market.contract.event.MarketPriceTickEventPayload;
import com.falconx.trading.engine.QuoteDrivenEngine;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 市场价格事件消费者。
 *
 * <p>该消费者是 `market-service -> trading-core-service` 的高频事件入口。
 * 当前阶段仅负责把标准价格 payload 交给报价驱动引擎，不对高频 tick 写 `t_inbox`。
 */
@Component
public class MarketPriceTickEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(MarketPriceTickEventConsumer.class);

    private final QuoteDrivenEngine quoteDrivenEngine;
    private final ExecutorService tradingPriceTickExecutor;

    public MarketPriceTickEventConsumer(QuoteDrivenEngine quoteDrivenEngine,
                                        @Qualifier("tradingPriceTickExecutor")
                                        ExecutorService tradingPriceTickExecutor) {
        this.quoteDrivenEngine = quoteDrivenEngine;
        this.tradingPriceTickExecutor = tradingPriceTickExecutor;
    }

    /**
     * 消费价格 tick 事件。
     *
     * @param eventId 事件 ID
     * @param payload 标准价格 payload
     */
    public void consume(String eventId, MarketPriceTickEventPayload payload) {
        String traceId = MDC.get(TraceIdConstants.TRACE_ID_MDC_KEY);
        ClassLoader callerClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            tradingPriceTickExecutor.submit(() -> runOnManagedThread(eventId, payload, traceId, callerClassLoader)).get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while processing market price tick", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Unable to process market price tick", cause);
        }
    }

    private void runOnManagedThread(String eventId,
                                    MarketPriceTickEventPayload payload,
                                    String traceId,
                                    ClassLoader callerClassLoader) {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        KafkaEventMessageSupport.bindTraceId(traceId);
        Thread.currentThread().setContextClassLoader(callerClassLoader);
        try {
            log.info("trading.consumer.market.price.tick eventId={} symbol={} ts={}",
                    eventId,
                    payload.symbol(),
                    payload.ts());
            if (payload.stale()) {
                log.warn("trading.consumer.market.price.tick.stale eventId={} symbol={} ts={} action=snapshot-only",
                        eventId,
                        payload.symbol(),
                        payload.ts());
            }
            quoteDrivenEngine.processTick(payload);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
            KafkaEventMessageSupport.clearTraceId();
        }
    }
}
