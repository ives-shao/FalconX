package com.falconx.trading.repository.mapper.record;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 交易账本 MyBatis 记录对象。
 *
 * <p>该记录对象对应 `t_ledger` 的数据库结构。
 *
 * @param id 主键 ID
 * @param userId 用户 ID
 * @param accountId 账户 ID
 * @param bizTypeCode 业务类型码
 * @param idempotencyKey 幂等键
 * @param referenceNo 业务参考号
 * @param amount 变动金额
 * @param balanceBefore 余额前快照
 * @param balanceAfter 余额后快照
 * @param frozenBefore 冻结前快照
 * @param frozenAfter 冻结后快照
 * @param marginUsedBefore 保证金前快照
 * @param marginUsedAfter 保证金后快照
 * @param createdAt 创建时间
 */
public record TradingLedgerRecord(
        Long id,
        Long userId,
        Long accountId,
        Integer bizTypeCode,
        String idempotencyKey,
        String referenceNo,
        BigDecimal amount,
        BigDecimal balanceBefore,
        BigDecimal balanceAfter,
        BigDecimal frozenBefore,
        BigDecimal frozenAfter,
        BigDecimal marginUsedBefore,
        BigDecimal marginUsedAfter,
        LocalDateTime createdAt
) {
}
