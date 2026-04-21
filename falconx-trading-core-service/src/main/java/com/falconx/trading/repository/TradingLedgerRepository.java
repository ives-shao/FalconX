package com.falconx.trading.repository;

import com.falconx.trading.entity.TradingLedgerEntry;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 交易账本仓储接口。
 *
 * <p>该接口负责保存和查询 `t_ledger` 的最小流水能力，
 * 正式数据库实现统一通过 `MyBatis Mapper + XML` 承载。
 */
public interface TradingLedgerRepository {

    /**
     * 保存账本流水。
     *
     * @param entry 账本流水
     * @return 持久化后的账本对象
     */
    TradingLedgerEntry save(TradingLedgerEntry entry);

    /**
     * 查询用户全部账本流水。
     *
     * @param userId 用户 ID
     * @return 该用户账本列表
     */
    List<TradingLedgerEntry> findByUserId(Long userId);

    /**
     * 分页查询用户账本流水。
     *
     * @param userId 用户 ID
     * @param offset 偏移量
     * @param limit 本页条数
     * @return 当前页账本列表
     */
    List<TradingLedgerEntry> findByUserIdPaginated(Long userId, int offset, int limit);

    /**
     * 判断某个账务幂等键是否已经存在。
     *
     * @param userId 用户 ID
     * @param idempotencyKey 账务幂等键
     * @return `true` 表示已存在
     */
    boolean existsByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    /**
     * 查询指定持仓最近一次 `Swap` 结算时间。
     *
     * @param userId 用户 ID
     * @param positionId 持仓 ID
     * @return 最近一次 `Swap` 账本的发生时间
     */
    Optional<OffsetDateTime> findLatestSwapSettlementAt(Long userId, Long positionId);
}
