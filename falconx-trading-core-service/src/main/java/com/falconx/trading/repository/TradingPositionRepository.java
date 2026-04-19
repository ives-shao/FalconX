package com.falconx.trading.repository;

import com.falconx.trading.entity.TradingPosition;
import java.util.List;
import java.util.Optional;

/**
 * 持仓仓储接口。
 *
 * <p>当前阶段只提供按开仓订单查询持仓的最小能力，
 * 便于在幂等下单场景中快速找到已经创建的持仓结果。
 */
public interface TradingPositionRepository {

    /**
     * 保存持仓。
     *
     * @param position 持仓对象
     * @return 持久化后的持仓对象
     */
    TradingPosition save(TradingPosition position);

    /**
     * 按开仓订单查询持仓。
     *
     * @param openingOrderId 开仓订单 ID
     * @return 持仓可选结果
     */
    Optional<TradingPosition> findByOpeningOrderId(Long openingOrderId);

    /**
     * 按持仓 ID 和用户 ID 查询持仓并加锁。
     *
     * @param positionId 持仓 ID
     * @param userId 用户 ID
     * @return 被锁定的持仓
     */
    Optional<TradingPosition> findByIdAndUserIdForUpdate(Long positionId, Long userId);

    /**
     * 查询某个用户当前全部 OPEN 持仓。
     *
     * @param userId 用户 ID
     * @return OPEN 持仓列表
     */
    List<TradingPosition> findOpenByUserId(Long userId);

    /**
     * 查询系统当前全部 OPEN 持仓。
     *
     * <p>该方法主要用于交易核心启动时构建按 symbol 分组的内存快照，
     * 让 `QuoteDrivenEngine` 在运行期不直接扫描 MySQL。
     *
     * @return 全部 OPEN 持仓
     */
    List<TradingPosition> findAllOpenPositions();
}
