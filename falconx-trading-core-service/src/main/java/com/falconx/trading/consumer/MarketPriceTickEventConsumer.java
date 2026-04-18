package com.falconx.trading.consumer;

import com.falconx.market.contract.event.MarketPriceTickEventPayload;
import com.falconx.trading.engine.QuoteDrivenEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public MarketPriceTickEventConsumer(QuoteDrivenEngine quoteDrivenEngine) {
        this.quoteDrivenEngine = quoteDrivenEngine;
    }

    /**
     * 消费价格 tick 事件。
     *
     * @param eventId 事件 ID
     * @param payload 标准价格 payload
     */
    public void consume(String eventId, MarketPriceTickEventPayload payload) {
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
    }
}
