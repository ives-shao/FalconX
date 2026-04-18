package com.falconx.market.service;

import com.falconx.market.entity.StandardQuote;

/**
 * 市场最新报价查询服务接口。
 *
 * <p>该服务用于把北向接口与内部读模型隔离开，
 * 保证后续即使改为直接读 Redis，也不会改变 controller 的依赖方向。
 */
public interface MarketQuoteQueryService {

    /**
     * 查询指定品种的最新报价。
     *
     * @param symbol 品种代码
     * @return 最新标准报价
     */
    StandardQuote getLatestQuote(String symbol);
}
