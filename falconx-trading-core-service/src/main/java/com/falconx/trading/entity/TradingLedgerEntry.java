package com.falconx.trading.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 交易账本流水实体。
 *
 * <p>该对象对应 `falconx_trading.t_ledger` 的内存骨架表达。
 * 为满足数据库设计中“资金流水必须可重放”的要求，
 * 当前骨架会同时保存 `balance / frozen / marginUsed` 三个维度的变更前后快照。
 *
 * @param ledgerId 账本主键
 * @param accountId 账户主键
 * @param userId 用户主键
 * @param bizType 账本业务类型
 * @param amount 本次动作金额
 * @param idempotencyKey 账务动作幂等键
 * @param referenceNo 业务参考号，例如 `txHash` 或 `orderNo`
 * @param balanceBefore 余额变更前快照
 * @param balanceAfter 余额变更后快照
 * @param frozenBefore 冻结金额变更前快照
 * @param frozenAfter 冻结金额变更后快照
 * @param marginUsedBefore 保证金占用变更前快照
 * @param marginUsedAfter 保证金占用变更后快照
 * @param createdAt 账本记录时间
 */
public record TradingLedgerEntry(
        Long ledgerId,
        Long accountId,
        Long userId,
        TradingLedgerBizType bizType,
        BigDecimal amount,
        String idempotencyKey,
        String referenceNo,
        BigDecimal balanceBefore,
        BigDecimal balanceAfter,
        BigDecimal frozenBefore,
        BigDecimal frozenAfter,
        BigDecimal marginUsedBefore,
        BigDecimal marginUsedAfter,
        OffsetDateTime createdAt
) {
}
