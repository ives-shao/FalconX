package com.falconx.trading.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;

/**
 * 交易账户实体。
 *
 * <p>该对象对应 `falconx_trading.t_account` 的领域表达，
 * 固定遵守数据库设计中的账户语义：
 *
 * <ul>
 *   <li>`balance`：账户现金余额</li>
 *   <li>`frozen`：已预留未确认金额</li>
 *   <li>`marginUsed`：已确认占用的保证金</li>
 *   <li>`available = balance - frozen - marginUsed`</li>
 * </ul>
 *
 * <p>所有状态变更都通过返回新对象实现，便于在真实持久化场景下继续保持不可变更新语义。
 */
public record TradingAccount(
        Long accountId,
        Long userId,
        String currency,
        BigDecimal balance,
        BigDecimal frozen,
        BigDecimal marginUsed,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    /**
     * @return 当前可用于开仓、扣费或提现的可用余额
     */
    public BigDecimal available() {
        return balance.subtract(frozen).subtract(marginUsed).setScale(8, RoundingMode.HALF_UP);
    }

    /**
     * 生成入金后的账户快照。
     *
     * @param amount 入账金额
     * @param occurredAt 本次状态变化时间
     * @return 余额增加后的新账户对象
     */
    public TradingAccount credit(BigDecimal amount, OffsetDateTime occurredAt) {
        return new TradingAccount(
                accountId,
                userId,
                currency,
                scaled(balance.add(amount)),
                frozen,
                marginUsed,
                createdAt,
                occurredAt
        );
    }

    /**
     * 生成业务入金回滚后的账户快照。
     *
     * <p>当前阶段先冻结“余额回退”语义，后续若引入更复杂的人工补偿和负余额处理，
     * 也应从该方法继续扩展，而不是改变余额、冻结和保证金三字段的基础含义。
     *
     * @param amount 回滚金额
     * @param occurredAt 本次状态变化时间
     * @return 余额减少后的新账户对象
     */
    public TradingAccount reverseCredit(BigDecimal amount, OffsetDateTime occurredAt) {
        return new TradingAccount(
                accountId,
                userId,
                currency,
                scaled(balance.subtract(amount)),
                frozen,
                marginUsed,
                createdAt,
                occurredAt
        );
    }

    /**
     * 生成“预留保证金”后的账户快照。
     *
     * @param amount 预留金额
     * @param occurredAt 本次状态变化时间
     * @return `frozen` 增加后的账户对象
     */
    public TradingAccount reserveMargin(BigDecimal amount, OffsetDateTime occurredAt) {
        return new TradingAccount(
                accountId,
                userId,
                currency,
                balance,
                scaled(frozen.add(amount)),
                marginUsed,
                createdAt,
                occurredAt
        );
    }

    /**
     * 生成“扣手续费”后的账户快照。
     *
     * @param fee 手续费金额
     * @param occurredAt 本次状态变化时间
     * @return `balance` 扣减后的账户对象
     */
    public TradingAccount chargeFee(BigDecimal fee, OffsetDateTime occurredAt) {
        return new TradingAccount(
                accountId,
                userId,
                currency,
                scaled(balance.subtract(fee)),
                frozen,
                marginUsed,
                createdAt,
                occurredAt
        );
    }

    /**
     * 生成“确认占用保证金”后的账户快照。
     *
     * @param margin 已成交需要确认占用的保证金
     * @param occurredAt 本次状态变化时间
     * @return `frozen` 减少、`marginUsed` 增加后的账户对象
     */
    public TradingAccount confirmMarginUsed(BigDecimal margin, OffsetDateTime occurredAt) {
        return new TradingAccount(
                accountId,
                userId,
                currency,
                balance,
                scaled(frozen.subtract(margin)),
                scaled(marginUsed.add(margin)),
                createdAt,
                occurredAt
        );
    }

    /**
     * 生成“手动平仓结算”后的账户快照。
     *
     * <p>平仓结算只释放已占用保证金并把已实现盈亏回写到现金余额，
     * 不改动 `frozen`，避免把开仓预留阶段语义重新引入到终态平仓路径。
     *
     * @param releasedMargin 释放的保证金
     * @param realizedPnl 已实现盈亏
     * @param occurredAt 本次状态变化时间
     * @return 平仓结算后的账户对象
     */
    public TradingAccount settleClosedPosition(BigDecimal releasedMargin,
                                               BigDecimal realizedPnl,
                                               OffsetDateTime occurredAt) {
        return new TradingAccount(
                accountId,
                userId,
                currency,
                scaled(balance.add(realizedPnl)),
                frozen,
                scaled(marginUsed.subtract(releasedMargin)),
                createdAt,
                occurredAt
        );
    }

    private BigDecimal scaled(BigDecimal value) {
        return value.setScale(8, RoundingMode.HALF_UP);
    }
}
