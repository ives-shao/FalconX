package com.falconx.market.provider;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Tiingo Provider 的骨架实现。
 *
 * <p>当前实现只用于冻结接入点和日志节点，不直接发起外部网络连接。
 * 当真正进入 Tiingo 接入实现时，应保留该类的职责边界，
 * 但用真实 WebSocket 连接逻辑替换当前的空实现。
 */
@Component
@Profile("stub")
public class StubTiingoQuoteProvider implements TiingoQuoteProvider {

    private static final Logger log = LoggerFactory.getLogger(StubTiingoQuoteProvider.class);

    /**
     * 启动接入骨架。
     *
     * @param quoteConsumer 原始报价消费函数
     */
    @Override
    public void start(List<String> symbols, Consumer<TiingoRawQuote> quoteConsumer) {
        log.info("market.tiingo.provider.stub.started symbols={}", symbols.size());

        // stub 环境也不允许再回退到静态产品清单。
        // 如果当前调用方没有给出 owner 已启用的 symbol，说明启动链路本身就没有准备好正式产品数据，
        // 这里直接记录并退出，避免继续用写死的演示产品掩盖问题。
        if (symbols == null || symbols.isEmpty()) {
            log.warn("market.tiingo.provider.stub.skipped reason=no-enabled-symbols");
            return;
        }
        for (String symbol : symbols) {
            BigDecimal bid = stubBid(symbol);
            BigDecimal ask = bid.add(new BigDecimal("0.00010000"));
            quoteConsumer.accept(new TiingoRawQuote(
                    symbol,
                    bid,
                    ask,
                    OffsetDateTime.now()
            ));
        }
    }

    @Override
    public void refreshSymbols(List<String> symbols) {
        log.info("market.tiingo.provider.stub.symbols.refreshed count={} symbols={}",
                symbols == null ? 0 : symbols.size(),
                symbols);
    }

    /**
     * 生成测试环境下的通用演示报价。
     *
     * <p>这里故意不再为单个固定品种写死价格，而是根据 symbol 生成稳定的伪基准价，
     * 让 stub 环境能够在“不依赖真实外部源”的前提下覆盖任意 owner 已启用品种。
     *
     * @param symbol 平台内部标准 symbol
     * @return 对应 symbol 的稳定演示 bid
     */
    private BigDecimal stubBid(String symbol) {
        int hash = Math.abs(symbol.hashCode() % 10_000);
        BigDecimal base = BigDecimal.valueOf(hash).movePointLeft(4);
        return base.max(new BigDecimal("1.00000000")).setScale(8, RoundingMode.HALF_UP);
    }
}
