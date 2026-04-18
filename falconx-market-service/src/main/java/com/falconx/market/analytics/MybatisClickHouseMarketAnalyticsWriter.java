package com.falconx.market.analytics;

import com.falconx.market.analytics.mapper.MarketKlineMapper;
import com.falconx.market.analytics.mapper.MarketQuoteTickMapper;
import com.falconx.market.analytics.mapper.record.MarketQuoteTickRecord;
import com.falconx.market.config.MarketServiceProperties;
import com.falconx.market.entity.KlineSnapshot;
import com.falconx.market.entity.StandardQuote;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * ClickHouse 市场分析写入 MyBatis 实现。
 *
 * <p>该实现把高频报价历史和已收盘 K 线统一改为 `Mapper + XML` 写入，
 * 避免在 Java 代码中出现字符串 SQL。
 */
@Component
@Profile("!stub")
public class MybatisClickHouseMarketAnalyticsWriter implements MarketAnalyticsWriter {

    private static final Logger log = LoggerFactory.getLogger(MybatisClickHouseMarketAnalyticsWriter.class);

    private final MarketQuoteTickMapper marketQuoteTickMapper;
    private final MarketKlineMapper marketKlineMapper;
    private final MarketServiceProperties properties;
    private final ConcurrentLinkedQueue<MarketQuoteTickRecord> pendingQuoteTicks = new ConcurrentLinkedQueue<>();
    private final AtomicInteger pendingQuoteTickCount = new AtomicInteger();
    private final ReentrantLock quoteFlushLock = new ReentrantLock();

    public MybatisClickHouseMarketAnalyticsWriter(MarketQuoteTickMapper marketQuoteTickMapper,
                                                  MarketKlineMapper marketKlineMapper,
                                                  MarketServiceProperties properties) {
        this.marketQuoteTickMapper = marketQuoteTickMapper;
        this.marketKlineMapper = marketKlineMapper;
        this.properties = properties;
    }

    @Override
    public void writeQuoteTick(StandardQuote quote) {
        // 高频 Tick 不再逐条同步写 ClickHouse。
        // 当前实现把标准化后的记录先放入进程内缓冲区，再由“批量阈值 + 定时刷新”双触发落库，
        // 以减少单条 INSERT 带来的网络往返和 ClickHouse 写入放大。
        MarketQuoteTickRecord record = MarketAnalyticsMybatisSupport.toQuoteTickRecord(quote);
        pendingQuoteTicks.add(record);
        int pendingSize = pendingQuoteTickCount.incrementAndGet();

        // 当缓冲达到阈值时，当前应用线程会尝试抢占 flush 锁并主动刷一批。
        // 若此时已有定时任务或其他线程正在刷盘，则本次只负责入队，不做阻塞等待。
        if (pendingSize >= properties.getAnalytics().getQuoteBatchSize()) {
            flushQuoteTicks("batch-threshold");
        }
    }

    @Override
    public void writeKline(KlineSnapshot snapshot) {
        if (!snapshot.isFinal()) {
            log.info("market.analytics.kline.skip_non_final symbol={} interval={} closeTime={}",
                    snapshot.symbol(),
                    snapshot.interval(),
                    snapshot.closeTime());
            return;
        }

        marketKlineMapper.insertKline(MarketAnalyticsMybatisSupport.toKlineRecord(snapshot));
        log.info("market.analytics.kline.write symbol={} interval={} closeTime={}",
                snapshot.symbol(),
                snapshot.interval(),
                snapshot.closeTime());
    }

    /**
     * 周期性刷新缓冲中的报价 Tick。
     *
     * <p>该定时任务与阈值触发共用同一套 flush 逻辑：
     *
     * <ul>
     *   <li>阈值触发负责高吞吐场景下尽快形成批量</li>
     *   <li>定时触发负责低吞吐场景下避免缓冲长期滞留</li>
     * </ul>
     *
     * <p>这里不使用外部回调线程直接刷盘，而是依赖 Spring 管理的调度线程执行，
     * 符合仓库里“外部回调线程不得直接执行完整下游链路”的规则。
     */
    @Scheduled(
            fixedDelayString = "${falconx.market.analytics.quote-flush-interval:500}",
            timeUnit = TimeUnit.MILLISECONDS
    )
    public void flushQuoteTicksOnSchedule() {
        flushQuoteTicks("scheduled");
    }

    /**
     * 服务关闭前尝试把残留缓冲刷入 ClickHouse，尽量减少正常停机时的数据遗失。
     */
    @PreDestroy
    public void flushRemainingQuoteTicks() {
        flushQuoteTicks("shutdown");
    }

    private void flushQuoteTicks(String trigger) {
        if (!quoteFlushLock.tryLock()) {
            return;
        }
        try {
            while (pendingQuoteTickCount.get() > 0) {
                List<MarketQuoteTickRecord> batch = drainQuoteTicks(properties.getAnalytics().getQuoteBatchSize());
                if (batch.isEmpty()) {
                    return;
                }
                try {
                    marketQuoteTickMapper.insertQuoteTicks(batch);
                    log.info("market.analytics.quote.flush.completed trigger={} batchSize={}", trigger, batch.size());
                } catch (RuntimeException exception) {
                    // ClickHouse 短暂不可用时不能直接吞掉已经出队的 Tick。
                    // 当前策略是把本批记录重新放回缓冲尾部，并恢复计数，再等待下一次阈值或定时刷新重试。
                    // 这样虽然可能打乱极小范围内的入队顺序，但能保证“批量失败不丢数”。
                    restoreQuoteTicks(batch);
                    log.error("market.analytics.quote.flush.failed trigger={} batchSize={}",
                            trigger,
                            batch.size(),
                            exception);
                    return;
                }
            }
        } finally {
            quoteFlushLock.unlock();
        }
    }

    private List<MarketQuoteTickRecord> drainQuoteTicks(int batchSize) {
        List<MarketQuoteTickRecord> batch = new ArrayList<>(batchSize);
        while (batch.size() < batchSize) {
            MarketQuoteTickRecord record = pendingQuoteTicks.poll();
            if (record == null) {
                break;
            }
            batch.add(record);
            pendingQuoteTickCount.decrementAndGet();
        }
        return batch;
    }

    private void restoreQuoteTicks(List<MarketQuoteTickRecord> batch) {
        batch.forEach(pendingQuoteTicks::add);
        pendingQuoteTickCount.addAndGet(batch.size());
    }
}
