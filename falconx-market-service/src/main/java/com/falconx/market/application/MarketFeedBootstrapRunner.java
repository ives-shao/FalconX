package com.falconx.market.application;

import com.falconx.market.entity.MarketSymbol;
import com.falconx.market.provider.TiingoQuoteProvider;
import com.falconx.market.repository.MarketSymbolRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 市场数据入口启动器。
 *
 * <p>该启动器在 market-service 启动完成后挂起 Tiingo Provider，
 * 让 Stage 6A 形成明确的运行时入口：服务启动 -> Provider 连接外部源 -> 报价进入应用层。
 *
 * <p>当前实现只允许从 market owner 查询全部“已启用交易”的平台品种。
 * Tiingo 当前真实协议使用“全品种订阅”，因此这里解析出的列表不再决定 WebSocket 订阅报文本身，
 * 而是决定 provider 在收到全量报价后，哪些 symbol 会继续进入平台内部主链路。
 */
@Component
public class MarketFeedBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MarketFeedBootstrapRunner.class);

    private final TiingoQuoteProvider tiingoQuoteProvider;
    private final MarketDataIngestionApplicationService marketDataIngestionApplicationService;
    private final MarketSymbolRepository marketSymbolRepository;

    public MarketFeedBootstrapRunner(TiingoQuoteProvider tiingoQuoteProvider,
                                     MarketDataIngestionApplicationService marketDataIngestionApplicationService,
                                     MarketSymbolRepository marketSymbolRepository) {
        this.tiingoQuoteProvider = tiingoQuoteProvider;
        this.marketDataIngestionApplicationService = marketDataIngestionApplicationService;
        this.marketSymbolRepository = marketSymbolRepository;
    }

    /**
     * 服务启动后启动报价 Provider。
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        log.info("market.feed.bootstrap.start");
        List<String> tiingoSymbols = resolveTiingoSymbols();
        log.info("market.feed.bootstrap.symbols resolvedCount={} symbols={}", tiingoSymbols.size(), tiingoSymbols);
        tiingoQuoteProvider.start(tiingoSymbols, marketDataIngestionApplicationService::ingest);
        log.info("market.feed.bootstrap.ready");
    }

    /**
     * 定时热刷新 Tiingo 本地白名单。
     *
     * <p>Tiingo 当前协议是“全量订阅 + 服务内过滤”，因此数据库里 symbol 启用状态变化后，无需重连外部源，
     * 只要定时把 owner `t_symbol` 的最新启用列表推送给 provider 即可。这里如果数据库临时不可用，
     * 必须保留 provider 当前白名单，避免一次失败把运行中的行情链路整体清空。
     */
    @Scheduled(
            cron = "${falconx.market.tiingo.symbol-whitelist-refresh-cron:0 */1 * * * *}",
            zone = "${falconx.market.tiingo.symbol-whitelist-refresh-zone:UTC}"
    )
    public void refreshTiingoSymbolWhitelist() {
        try {
            List<String> tiingoSymbols = resolveTiingoSymbols();
            tiingoQuoteProvider.refreshSymbols(tiingoSymbols);
            log.info("market.feed.bootstrap.symbols.refreshed resolvedCount={} symbols={}",
                    tiingoSymbols.size(),
                    tiingoSymbols);
        } catch (RuntimeException error) {
            log.error("market.feed.bootstrap.symbols.refresh.failed reason={}", error.toString(), error);
        }
    }

    /**
     * 解析 Tiingo 本地过滤用的 symbol 列表。
     *
     * <p>这里必须只依赖 market owner 中全部“已启用交易”的品种，确保真正进入系统的报价范围与平台内部
     * `t_symbol` 配置保持一致。运行时不允许再回退到静态 symbol 列表，否则数据库与应用过滤条件会分叉，
     * 导致“配置未启用的产品却被外部源写入系统”或“数据库已启用但应用仍旧忽略”的生产级问题。
     *
     * @return 应订阅的内部标准 symbol 列表
     */
    private List<String> resolveTiingoSymbols() {
        List<String> databaseSymbols = marketSymbolRepository.findAllTradingSymbols().stream()
                .map(MarketSymbol::symbol)
                .toList();
        if (databaseSymbols.isEmpty()) {
            log.warn("market.feed.bootstrap.symbols.empty reason=no-enabled-symbols");
        }
        return databaseSymbols;
    }
}
