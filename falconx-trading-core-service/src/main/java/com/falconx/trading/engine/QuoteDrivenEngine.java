package com.falconx.trading.engine;

import com.falconx.market.contract.event.MarketPriceTickEventPayload;
import com.falconx.trading.dto.PriceTickProcessingResult;
import com.falconx.trading.entity.TradingQuoteSnapshot;
import com.falconx.trading.repository.TradingQuoteSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 报价驱动引擎骨架。
 *
 * <p>该引擎用于承接 `market-service` 推来的最新价格事件。
 * Stage 3B 先只冻结两件事：
 *
 * <ul>
 *   <li>把最新价格写入交易域内部快照仓储</li>
 *   <li>预留触发动作数量返回值，为后续限价单、止盈止损和强平引擎扩展做准备</li>
 * </ul>
 */
@Component
public class QuoteDrivenEngine {

    private static final Logger log = LoggerFactory.getLogger(QuoteDrivenEngine.class);

    private final TradingQuoteSnapshotRepository tradingQuoteSnapshotRepository;
    private final OpenPositionSnapshotStore openPositionSnapshotStore;

    public QuoteDrivenEngine(TradingQuoteSnapshotRepository tradingQuoteSnapshotRepository,
                             OpenPositionSnapshotStore openPositionSnapshotStore) {
        this.tradingQuoteSnapshotRepository = tradingQuoteSnapshotRepository;
        this.openPositionSnapshotStore = openPositionSnapshotStore;
    }

    /**
     * 处理一条价格 tick。
     *
     * @param payload 市场价格事件
     * @return 处理结果
     */
    public PriceTickProcessingResult processTick(MarketPriceTickEventPayload payload) {
        log.info("trading.quote.tick.received symbol={} ts={} stale={}",
                payload.symbol(),
                payload.ts(),
                payload.stale());
        TradingQuoteSnapshot snapshot = tradingQuoteSnapshotRepository.save(new TradingQuoteSnapshot(
                payload.symbol(),
                payload.bid(),
                payload.ask(),
                payload.mark(),
                payload.ts(),
                payload.source(),
                payload.stale()
        ));
        if (snapshot.stale()) {
            log.warn("trading.quote.tick.stale symbol={} source={} ts={} action=snapshot-only",
                    snapshot.symbol(),
                    snapshot.source(),
                    snapshot.ts());
            return new PriceTickProcessingResult(snapshot, 0);
        }
        int openPositions = openPositionSnapshotStore.listOpenBySymbol(snapshot.symbol()).size();
        log.info("trading.quote.tick.applied symbol={} source={} stale={} openPositions={}",
                snapshot.symbol(),
                snapshot.source(),
                snapshot.stale(),
                openPositions);
        return new PriceTickProcessingResult(snapshot, 0);
    }
}
