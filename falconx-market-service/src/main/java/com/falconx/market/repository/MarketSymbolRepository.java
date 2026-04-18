package com.falconx.market.repository;

import com.falconx.market.entity.MarketSymbol;
import java.util.List;
import java.util.Optional;

/**
 * 市场品种仓储。
 *
 * <p>该仓储负责 `t_symbol` 的 owner 读写，
 * 用于交易时间快照预热、品种元数据查询以及 Tiingo crypto symbol 补录。
 */
public interface MarketSymbolRepository {

    /**
     * 查询全部可交易品种。
     *
     * @return 品种列表
     */
    List<MarketSymbol> findAllTradingSymbols();

    /**
     * 按 symbol 查询品种。
     *
     * @param symbol 品种代码
     * @return 品种
     */
    Optional<MarketSymbol> findBySymbol(String symbol);

    /**
     * 只追加不存在的 symbol。
     *
     * <p>该能力当前只服务于 Tiingo crypto 的一次性 symbol 发现导入：
     * 已存在的 symbol 必须保留原始配置，不允许因为补录动作覆盖现有 `status`
     * 或交易精度等人工配置。
     *
     * @param symbols 待追加的 symbol 列表
     * @return 实际新增行数
     */
    int appendIfAbsent(List<MarketSymbol> symbols);
}
