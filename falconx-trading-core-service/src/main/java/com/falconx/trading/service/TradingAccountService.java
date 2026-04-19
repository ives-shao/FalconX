package com.falconx.trading.service;

import com.falconx.trading.entity.TradingAccount;
import com.falconx.trading.entity.TradingLedgerBizType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 交易账户服务接口。
 *
 * <p>该服务封装交易账户语义和账本留痕规则，
 * 确保应用层在调用账户变更时不会破坏 `balance / frozen / marginUsed` 的定义。
 */
public interface TradingAccountService {

    /**
     * 持仓退出结算结果。
     *
     * @param account 变更后的账户
     * @param appliedPnl 实际记入账户余额的清算盈亏
     * @param platformCoveredLoss 因负净值保护由平台兜底的亏损
     */
    record PositionSettlementResult(
            TradingAccount account,
            BigDecimal appliedPnl,
            BigDecimal platformCoveredLoss
    ) {
    }

    /**
     * 获取或初始化交易账户。
     *
     * @param userId 用户 ID
     * @param currency 账户币种
     * @return 现有或新建账户
     */
    TradingAccount getOrCreateAccount(Long userId, String currency);

    /**
     * 获取或初始化交易账户，并在存在记录时对账户行加悲观锁。
     *
     * <p>该方法只用于需要“先读取余额、再同步扣减”的交易写路径，避免并发请求在风控检查阶段读到相同余额快照。
     *
     * @param userId 用户 ID
     * @param currency 账户币种
     * @return 现有或新建且已加锁的账户
     */
    TradingAccount getOrCreateAccountForUpdate(Long userId, String currency);

    /**
     * 获取已存在的交易账户并加悲观锁。
     *
     * <p>该方法只用于“不允许隐式补建账户”的结算类写路径。
     * 若 owner 账户缺失或币种不匹配，调用方必须让整笔事务失败并回滚。
     *
     * @param userId 用户 ID
     * @param currency 账户币种
     * @return 已存在且已加锁的账户
     */
    TradingAccount getExistingAccountForUpdate(Long userId, String currency);

    /**
     * 记一笔业务入金。
     *
     * @param userId 用户 ID
     * @param currency 币种
     * @param amount 入账金额
     * @param idempotencyKey 账务幂等键
     * @param referenceNo 业务参考号
     * @param occurredAt 发生时间
     * @return 变更后账户
     */
    TradingAccount creditDeposit(Long userId,
                                 String currency,
                                 BigDecimal amount,
                                 String idempotencyKey,
                                 String referenceNo,
                                 OffsetDateTime occurredAt);

    /**
     * 反转一笔已入账业务入金。
     *
     * @param userId 用户 ID
     * @param currency 币种
     * @param amount 回滚金额
     * @param idempotencyKey 账务幂等键
     * @param referenceNo 业务参考号
     * @param occurredAt 发生时间
     * @return 变更后账户
     */
    TradingAccount reverseDeposit(Long userId,
                                  String currency,
                                  BigDecimal amount,
                                  String idempotencyKey,
                                  String referenceNo,
                                  OffsetDateTime occurredAt);

    /**
     * 预留保证金。
     *
     * @param userId 用户 ID
     * @param currency 币种
     * @param margin 预留金额
     * @param idempotencyKey 账务幂等键
     * @param referenceNo 业务参考号
     * @param occurredAt 发生时间
     * @return 变更后账户
     */
    TradingAccount reserveMargin(Long userId,
                                 String currency,
                                 BigDecimal margin,
                                 String idempotencyKey,
                                 String referenceNo,
                                 OffsetDateTime occurredAt);

    /**
     * 扣减手续费。
     *
     * @param userId 用户 ID
     * @param currency 币种
     * @param fee 手续费金额
     * @param idempotencyKey 账务幂等键
     * @param referenceNo 业务参考号
     * @param occurredAt 发生时间
     * @return 变更后账户
     */
    TradingAccount chargeFee(Long userId,
                             String currency,
                             BigDecimal fee,
                             String idempotencyKey,
                             String referenceNo,
                             OffsetDateTime occurredAt);

    /**
     * 确认占用保证金。
     *
     * @param userId 用户 ID
     * @param currency 币种
     * @param margin 需确认的保证金
     * @param idempotencyKey 账务幂等键
     * @param referenceNo 业务参考号
     * @param occurredAt 发生时间
     * @return 变更后账户
     */
    TradingAccount confirmMarginUsed(Long userId,
                                     String currency,
                                     BigDecimal margin,
                                     String idempotencyKey,
                                     String referenceNo,
                                     OffsetDateTime occurredAt);

    /**
     * 结算一笔持仓退出。
     *
     * <p>该方法是手动平仓、TP、SL 和强平共享的正式资金写路径。
     * 若启用负净值保护，账户余额最低只能落到 `0`，超出部分由返回值中的 `platformCoveredLoss` 表示。
     * 账本流水仍记录原始 `realizedPnl`，用于保留真实市场盈亏审计。
     *
     * @param existingAccount 已存在且已加锁的账户
     * @param releasedMargin 释放保证金
     * @param realizedPnl 真实市场已实现盈亏
     * @param ledgerBizType 本次账本业务类型
     * @param protectNegativeBalance 是否启用负净值保护
     * @param idempotencyKey 账务幂等键
     * @param referenceNo 业务参考号
     * @param occurredAt 发生时间
     * @return 结算结果
     */
    PositionSettlementResult settlePositionExit(TradingAccount existingAccount,
                                                BigDecimal releasedMargin,
                                                BigDecimal realizedPnl,
                                                TradingLedgerBizType ledgerBizType,
                                                boolean protectNegativeBalance,
                                                String idempotencyKey,
                                                String referenceNo,
                                                OffsetDateTime occurredAt);
}
