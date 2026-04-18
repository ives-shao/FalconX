package com.falconx.market.application;

import com.falconx.infrastructure.id.IdGenerator;
import com.falconx.market.config.MarketServiceProperties;
import com.falconx.market.entity.MarketSymbol;
import com.falconx.market.provider.TiingoCryptoSymbolSamplingService;
import com.falconx.market.provider.TiingoDiscoveredCryptoSymbol;
import com.falconx.market.repository.MarketSymbolRepository;
import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Tiingo crypto symbol 导入启动器。
 *
 * <p>该启动器是一个显式开启的“运维型” runner。它在服务启动时执行一次采样导入，
 * 用于把 Tiingo `crypto` 成交流里出现的标准交易对补录到 `t_symbol`。
 *
 * <p>导入策略固定为：
 *
 * <ul>
 *   <li>只追加，不覆盖已存在 symbol</li>
 *   <li>默认写成 `status=2 suspended`</li>
 *   <li>只补基础元数据，不把这些 symbol 自动纳入当前可交易白名单</li>
 * </ul>
 */
@Component
@Profile("!stub")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TiingoCryptoSymbolImportRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TiingoCryptoSymbolImportRunner.class);

    private static final BigDecimal DEFAULT_MIN_QTY = new BigDecimal("0.000001");
    private static final BigDecimal DEFAULT_MAX_QTY = new BigDecimal("1000000.000000");
    private static final BigDecimal DEFAULT_MIN_NOTIONAL = new BigDecimal("10.000000");
    private static final BigDecimal DEFAULT_TAKER_FEE_RATE = new BigDecimal("0.000500");
    private static final BigDecimal DEFAULT_SPREAD = BigDecimal.ZERO.setScale(8);

    private final MarketServiceProperties properties;
    private final TiingoCryptoSymbolSamplingService samplingService;
    private final MarketSymbolRepository marketSymbolRepository;
    private final IdGenerator idGenerator;

    public TiingoCryptoSymbolImportRunner(MarketServiceProperties properties,
                                          TiingoCryptoSymbolSamplingService samplingService,
                                          MarketSymbolRepository marketSymbolRepository,
                                          IdGenerator idGenerator) {
        this.properties = properties;
        this.samplingService = samplingService;
        this.marketSymbolRepository = marketSymbolRepository;
        this.idGenerator = idGenerator;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.getTiingo().getCryptoSymbolImport().isEnabled()) {
            return;
        }
        // symbol 发现是运维增强能力，不应阻塞 market-service 主启动链路。
        // 这里改为独立虚拟线程后台执行，即使 Tiingo crypto 连接缓慢或采样窗口需要等待 10 秒，
        // 也不会拖慢 FX 主报价链路、Redis 预热或 HTTP 服务可用性。
        Thread.ofVirtual()
                .name("market-crypto-symbol-import")
                .start(() -> {
                    try {
                        doImport();
                    } catch (RuntimeException exception) {
                        // 该任务失败不应中断服务启动；这里只做告警并等待下一次人工触发或重启采样。
                        log.warn("market.tiingo.crypto-symbol-import.background.failed reason={}", exception.getMessage(), exception);
                    }
                });
    }

    private void doImport() {
        List<TiingoDiscoveredCryptoSymbol> sampledSymbols = samplingService.sample();
        List<MarketSymbol> appendCandidates = sampledSymbols.stream()
                .map(this::toSuspendedMarketSymbol)
                .toList();
        int insertedCount = marketSymbolRepository.appendIfAbsent(appendCandidates);
        log.info(
                "market.tiingo.crypto-symbol-import.completed sampledCount={} insertedCount={} importedSymbols={}",
                sampledSymbols.size(),
                insertedCount,
                sampledSymbols.stream().map(TiingoDiscoveredCryptoSymbol::symbol).toList()
        );
    }

    private MarketSymbol toSuspendedMarketSymbol(TiingoDiscoveredCryptoSymbol discoveredSymbol) {
        return new MarketSymbol(
                idGenerator.nextId(),
                discoveredSymbol.symbol(),
                1,
                "CRYPTO",
                discoveredSymbol.baseCurrency(),
                discoveredSymbol.quoteCurrency(),
                resolvePricePrecision(discoveredSymbol.price()),
                resolveQtyPrecision(discoveredSymbol.quantity()),
                DEFAULT_MIN_QTY,
                DEFAULT_MAX_QTY,
                DEFAULT_MIN_NOTIONAL,
                100,
                DEFAULT_TAKER_FEE_RATE,
                DEFAULT_SPREAD,
                2
        );
    }

    private int resolvePricePrecision(BigDecimal observedPrice) {
        if (observedPrice == null) {
            return 8;
        }
        return clampPrecision(observedPrice.stripTrailingZeros().scale(), 2, 8);
    }

    private int resolveQtyPrecision(BigDecimal observedQuantity) {
        if (observedQuantity == null) {
            return 6;
        }
        return clampPrecision(observedQuantity.stripTrailingZeros().scale(), 6, 8);
    }

    private int clampPrecision(int observedScale, int min, int max) {
        int normalizedScale = Math.max(observedScale, min);
        return Math.min(normalizedScale, max);
    }
}
