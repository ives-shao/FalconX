package com.falconx.market.service;

import com.falconx.market.entity.KlineSnapshot;
import com.falconx.market.entity.StandardQuote;
import java.util.List;

/**
 * K 线聚合服务。
 *
 * <p>该接口用于冻结市场服务内部的 K 线生成职责边界。
 * 当前实现允许同一条报价同时推进多个周期，因此返回值改为“本次报价触发收盘的所有 K 线快照”。
 */
public interface KlineAggregationService {

    /**
     * 用最新报价更新 K 线聚合状态。
     *
     * @param quote 标准报价对象
     * @return 当前报价触发收盘的所有 K 线快照；若没有任何周期收盘，则返回空列表
     */
    List<KlineSnapshot> onQuote(StandardQuote quote);
}
