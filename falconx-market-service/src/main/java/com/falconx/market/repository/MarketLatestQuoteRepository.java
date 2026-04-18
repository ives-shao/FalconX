package com.falconx.market.repository;

import com.falconx.market.entity.StandardQuote;
import java.util.Optional;

/**
 * 市场最新报价读模型仓储接口。
 *
 * <p>该仓储用于承接 `market-service` 内部最新报价查询场景，
 * 让北向查询接口无需直接依赖 Redis 即可在骨架阶段形成稳定调用链。
 */
public interface MarketLatestQuoteRepository {

    /**
     * 保存最新报价。
     *
     * @param quote 标准报价对象
     */
    void save(StandardQuote quote);

    /**
     * 按品种查询最新报价。
     *
     * @param symbol 品种代码
     * @return 最新报价
     */
    Optional<StandardQuote> findBySymbol(String symbol);
}
