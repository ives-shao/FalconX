package com.falconx.trading.service.impl;

import com.falconx.trading.entity.TradingAccount;
import com.falconx.trading.entity.TradingLedgerBizType;
import com.falconx.trading.entity.TradingLedgerEntry;
import com.falconx.trading.repository.TradingAccountRepository;
import com.falconx.trading.repository.TradingLedgerRepository;
import com.falconx.trading.service.TradingAccountService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * 交易账户服务默认实现。
 *
 * <p>该实现承担 Stage 3B 的账户语义冻结职责：
 *
 * <ol>
 *   <li>统一创建或读取交易账户</li>
 *   <li>把账户变更转换为不可变新快照</li>
 *   <li>为每次变化写出可回放账本流水</li>
 * </ol>
 *
 * <p>当前阶段账户 owner 数据已经接入真实仓储，但方法边界仍按本地事务模型保持稳定：
 * 后续若从 JDBC 迁移到 `MyBatis + XML Mapper`，也只允许替换持久化实现，不应改变业务步骤。
 */
@Service
public class DefaultTradingAccountService implements TradingAccountService {

    private final TradingAccountRepository tradingAccountRepository;
    private final TradingLedgerRepository tradingLedgerRepository;

    public DefaultTradingAccountService(TradingAccountRepository tradingAccountRepository,
                                        TradingLedgerRepository tradingLedgerRepository) {
        this.tradingAccountRepository = tradingAccountRepository;
        this.tradingLedgerRepository = tradingLedgerRepository;
    }

    @Override
    public TradingAccount getOrCreateAccount(Long userId, String currency) {
        return getOrCreateAccountInternal(userId, currency, false);
    }

    @Override
    public TradingAccount getOrCreateAccountForUpdate(Long userId, String currency) {
        return getOrCreateAccountInternal(userId, currency, true);
    }

    @Override
    public TradingAccount getExistingAccountForUpdate(Long userId, String currency) {
        return findExistingAccountForUpdateOrThrow(userId, currency);
    }

    @Override
    public TradingAccount creditDeposit(Long userId,
                                        String currency,
                                        BigDecimal amount,
                                        String idempotencyKey,
                                        String referenceNo,
                                        OffsetDateTime occurredAt) {
        TradingAccount before = getOrCreateAccount(userId, currency);
        TradingAccount after = tradingAccountRepository.save(before.credit(amount, occurredAt));
        writeLedger(before, after, TradingLedgerBizType.DEPOSIT_CREDIT, amount, idempotencyKey, referenceNo, occurredAt);
        return after;
    }

    @Override
    public TradingAccount reverseDeposit(Long userId,
                                         String currency,
                                         BigDecimal amount,
                                         String idempotencyKey,
                                         String referenceNo,
                                         OffsetDateTime occurredAt) {
        TradingAccount before = getOrCreateAccount(userId, currency);
        TradingAccount after = tradingAccountRepository.save(before.reverseCredit(amount, occurredAt));
        writeLedger(before, after, TradingLedgerBizType.DEPOSIT_REVERSAL, amount, idempotencyKey, referenceNo, occurredAt);
        return after;
    }

    @Override
    public TradingAccount reserveMargin(Long userId,
                                        String currency,
                                        BigDecimal margin,
                                        String idempotencyKey,
                                        String referenceNo,
                                        OffsetDateTime occurredAt) {
        TradingAccount before = getOrCreateAccount(userId, currency);
        TradingAccount after = tradingAccountRepository.save(before.reserveMargin(margin, occurredAt));
        writeLedger(before, after, TradingLedgerBizType.ORDER_MARGIN_RESERVED, margin, idempotencyKey, referenceNo, occurredAt);
        return after;
    }

    @Override
    public TradingAccount chargeFee(Long userId,
                                    String currency,
                                    BigDecimal fee,
                                    String idempotencyKey,
                                    String referenceNo,
                                    OffsetDateTime occurredAt) {
        TradingAccount before = getOrCreateAccount(userId, currency);
        TradingAccount after = tradingAccountRepository.save(before.chargeFee(fee, occurredAt));
        writeLedger(before, after, TradingLedgerBizType.ORDER_FEE_CHARGED, fee, idempotencyKey, referenceNo, occurredAt);
        return after;
    }

    @Override
    public TradingAccount supplementIsolatedMargin(TradingAccount existingAccount,
                                                   BigDecimal amount,
                                                   String idempotencyKey,
                                                   String referenceNo,
                                                   OffsetDateTime occurredAt) {
        TradingAccount before = Objects.requireNonNull(existingAccount, "existingAccount");
        BigDecimal positiveAmount = Objects.requireNonNull(amount, "amount");
        if (positiveAmount.signum() <= 0) {
            throw new IllegalArgumentException("Isolated margin supplement amount must be positive");
        }
        TradingAccount after = tradingAccountRepository.save(before.supplementIsolatedMargin(positiveAmount, occurredAt));
        writeLedger(before, after, TradingLedgerBizType.ISOLATED_MARGIN_SUPPLEMENT, positiveAmount, idempotencyKey, referenceNo, occurredAt);
        return after;
    }

    @Override
    public TradingLedgerEntry settleSwap(TradingAccount existingAccount,
                                         BigDecimal amount,
                                         TradingLedgerBizType ledgerBizType,
                                         String idempotencyKey,
                                         String referenceNo,
                                         OffsetDateTime occurredAt) {
        TradingAccount before = Objects.requireNonNull(existingAccount, "existingAccount");
        BigDecimal positiveAmount = Objects.requireNonNull(amount, "amount");
        if (positiveAmount.signum() < 0) {
            throw new IllegalArgumentException("Swap settlement amount must be positive");
        }
        if (ledgerBizType != TradingLedgerBizType.SWAP_CHARGE && ledgerBizType != TradingLedgerBizType.SWAP_INCOME) {
            throw new IllegalArgumentException("Swap settlement requires SWAP_CHARGE or SWAP_INCOME");
        }
        TradingAccount after = ledgerBizType == TradingLedgerBizType.SWAP_CHARGE
                ? tradingAccountRepository.save(before.chargeSwap(positiveAmount, occurredAt))
                : tradingAccountRepository.save(before.creditSwap(positiveAmount, occurredAt));
        return writeLedger(before, after, ledgerBizType, positiveAmount, idempotencyKey, referenceNo, occurredAt);
    }

    @Override
    public TradingAccount confirmMarginUsed(Long userId,
                                            String currency,
                                            BigDecimal margin,
                                            String idempotencyKey,
                                            String referenceNo,
                                            OffsetDateTime occurredAt) {
        TradingAccount before = getOrCreateAccount(userId, currency);
        TradingAccount after = tradingAccountRepository.save(before.confirmMarginUsed(margin, occurredAt));
        writeLedger(before, after, TradingLedgerBizType.ORDER_MARGIN_CONFIRMED, margin, idempotencyKey, referenceNo, occurredAt);
        return after;
    }

    @Override
    public PositionSettlementResult settlePositionExit(TradingAccount existingAccount,
                                                       BigDecimal releasedMargin,
                                                       BigDecimal realizedPnl,
                                                       TradingLedgerBizType ledgerBizType,
                                                       boolean protectNegativeBalance,
                                                       String idempotencyKey,
                                                       String referenceNo,
                                                       OffsetDateTime occurredAt) {
        TradingAccount before = Objects.requireNonNull(existingAccount, "existingAccount");
        if (before.marginUsed().compareTo(releasedMargin) < 0) {
            throw new IllegalStateException(
                    "Trading margin_used snapshot is inconsistent, accountId=" + before.accountId()
                            + ", marginUsed=" + before.marginUsed()
                            + ", releasedMargin=" + releasedMargin
            );
        }

        BigDecimal appliedPnl = realizedPnl;
        BigDecimal platformCoveredLoss = BigDecimal.ZERO.setScale(8);
        if (protectNegativeBalance && realizedPnl.signum() < 0) {
            BigDecimal minimumAllowedPnl = before.balance().negate();
            if (realizedPnl.compareTo(minimumAllowedPnl) < 0) {
                appliedPnl = minimumAllowedPnl.setScale(8);
                platformCoveredLoss = realizedPnl.abs().subtract(appliedPnl.abs()).setScale(8);
            }
        }

        TradingAccount after = tradingAccountRepository.save(before.settlePositionExit(releasedMargin, appliedPnl, occurredAt));
        writeLedger(before, after, Objects.requireNonNull(ledgerBizType, "ledgerBizType"), appliedPnl, idempotencyKey, referenceNo, occurredAt);
        return new PositionSettlementResult(after, appliedPnl, platformCoveredLoss);
    }

    private TradingLedgerEntry writeLedger(TradingAccount before,
                                           TradingAccount after,
                                           TradingLedgerBizType bizType,
                                           BigDecimal amount,
                                           String idempotencyKey,
                                           String referenceNo,
                                           OffsetDateTime occurredAt) {
        return tradingLedgerRepository.save(new TradingLedgerEntry(
                null,
                after.accountId(),
                after.userId(),
                bizType,
                amount,
                idempotencyKey,
                referenceNo,
                before.balance(),
                after.balance(),
                before.frozen(),
                after.frozen(),
                before.marginUsed(),
                after.marginUsed(),
                occurredAt
        ));
    }

    /**
     * 统一承接“查找或创建账户”的并发语义。
     *
     * <p>普通读路径只需返回账户快照；交易写路径则必须在账户已存在时拿到 `FOR UPDATE`
     * 锁，以保证后续保证金校验与扣减基于同一行锁视图完成。
     *
     * <p>若两个线程首次同时为同一用户创建账户，第二个线程会命中唯一键冲突。
     * 这里显式捕获 `DuplicateKeyException`，回退为重新查询，避免把瞬时并发初始化错误暴露给上层。
     */
    private TradingAccount getOrCreateAccountInternal(Long userId, String currency, boolean lockForUpdate) {
        if (lockForUpdate) {
            TradingAccount locked = tradingAccountRepository.findByUserIdAndCurrencyForUpdate(userId, currency).orElse(null);
            if (locked != null) {
                return locked;
            }
        } else {
            TradingAccount existing = tradingAccountRepository.findByUserIdAndCurrency(userId, currency).orElse(null);
            if (existing != null) {
                return existing;
            }
        }

        TradingAccount newAccount = new TradingAccount(
                null,
                userId,
                currency,
                BigDecimal.ZERO.setScale(8),
                BigDecimal.ZERO.setScale(8),
                BigDecimal.ZERO.setScale(8),
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
        try {
            TradingAccount created = tradingAccountRepository.save(newAccount);
            if (lockForUpdate) {
                return tradingAccountRepository.findByUserIdAndCurrencyForUpdate(userId, currency).orElse(created);
            }
            return created;
        } catch (DuplicateKeyException exception) {
            return lockForUpdate
                    ? tradingAccountRepository.findByUserIdAndCurrencyForUpdate(userId, currency)
                    .orElseThrow(() -> new IllegalStateException("Account not found after duplicate key", exception))
                    : tradingAccountRepository.findByUserIdAndCurrency(userId, currency)
                    .orElseThrow(() -> new IllegalStateException("Account not found after duplicate key", exception));
        }
    }

    private TradingAccount findExistingAccountForUpdateOrThrow(Long userId, String currency) {
        return tradingAccountRepository.findByUserIdAndCurrencyForUpdate(userId, currency)
                .orElseThrow(() -> new IllegalStateException(
                        "Trading settlement account not found or currency mismatch, userId=" + userId + ", currency=" + currency
                ));
    }
}
