package com.falconx.market.provider;

import java.util.List;
import java.util.function.Consumer;

/**
 * Tiingo 报价源抽象接口。
 *
 * <p>当前接口固定了 market-service 外部报价输入边界：
 * 由实现类负责建立连接、订阅报价并把原始报文转换为 {@link TiingoRawQuote}，
 * 应用层只接收标准化前的原始报价对象。
 */
public interface TiingoQuoteProvider {

    /**
     * 启动行情接入。
     *
     * @param symbols 内部标准 symbol 列表；当前 Tiingo 接入会先全品种订阅，再只把命中这些 symbol 的报价交给应用层
     * @param quoteConsumer 原始报价消费函数，用于将报价交给应用层处理
     */
    void start(List<String> symbols, Consumer<TiingoRawQuote> quoteConsumer);

    /**
     * 热刷新本地 symbol 白名单。
     *
     * <p>Tiingo 当前 FX 接入采用全品种订阅，因此运行时“新增或下线某个平台品种”不需要重新建立外部订阅，
     * 只需要更新服务内本地放行白名单即可。实现类必须把这里的 symbol 集合视为 owner 数据源的唯一真相，
     * 并在后续入站报文过滤时即时生效。
     *
     * @param symbols owner 中当前启用的内部标准 symbol 列表
     */
    void refreshSymbols(List<String> symbols);
}
