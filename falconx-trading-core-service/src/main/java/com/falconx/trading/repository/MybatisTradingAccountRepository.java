package com.falconx.trading.repository;

import com.falconx.infrastructure.id.IdGenerator;
import com.falconx.trading.entity.TradingAccount;
import com.falconx.trading.repository.mapper.TradingAccountMapper;
import com.falconx.trading.repository.mapper.record.TradingAccountRecord;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 交易账户 Repository 的 MyBatis 实现。
 *
 * <p>该实现负责把账户领域对象转换为 `t_account` 的持久化记录，
 * 并通过 XML SQL 完成 owner 数据读写。
 */
@Repository
public class MybatisTradingAccountRepository implements TradingAccountRepository {

    private final TradingAccountMapper tradingAccountMapper;
    private final IdGenerator idGenerator;

    public MybatisTradingAccountRepository(TradingAccountMapper tradingAccountMapper, IdGenerator idGenerator) {
        this.tradingAccountMapper = tradingAccountMapper;
        this.idGenerator = idGenerator;
    }

    @Override
    public Optional<TradingAccount> findByUserIdAndCurrency(Long userId, String currency) {
        TradingAccountRecord record = tradingAccountMapper.selectByUserIdAndCurrency(userId, currency);
        return Optional.ofNullable(toDomain(record));
    }

    @Override
    public Optional<TradingAccount> findByUserIdAndCurrencyForUpdate(Long userId, String currency) {
        TradingAccountRecord record = tradingAccountMapper.selectByUserIdAndCurrencyForUpdate(userId, currency);
        return Optional.ofNullable(toDomain(record));
    }

    @Override
    public TradingAccount save(TradingAccount account) {
        if (account.accountId() == null) {
            long id = idGenerator.nextId();
            OffsetDateTime createdAt = account.createdAt() == null ? OffsetDateTime.now() : account.createdAt();
            TradingAccount persisted = new TradingAccount(
                    id,
                    account.userId(),
                    account.currency(),
                    account.balance(),
                    account.frozen(),
                    account.marginUsed(),
                    createdAt,
                    account.updatedAt() == null ? createdAt : account.updatedAt()
            );
            tradingAccountMapper.insertTradingAccount(toRecord(persisted));
            return persisted;
        }

        TradingAccount persisted = new TradingAccount(
                account.accountId(),
                account.userId(),
                account.currency(),
                account.balance(),
                account.frozen(),
                account.marginUsed(),
                account.createdAt(),
                account.updatedAt() == null ? OffsetDateTime.now() : account.updatedAt()
        );
        tradingAccountMapper.updateTradingAccount(toRecord(persisted));
        return persisted;
    }

    private TradingAccountRecord toRecord(TradingAccount account) {
        return new TradingAccountRecord(
                account.accountId(),
                account.userId(),
                account.currency(),
                account.balance(),
                account.frozen(),
                account.marginUsed(),
                TradingMybatisSupport.toLocalDateTime(account.createdAt()),
                TradingMybatisSupport.toLocalDateTime(account.updatedAt())
        );
    }

    private TradingAccount toDomain(TradingAccountRecord record) {
        if (record == null) {
            return null;
        }
        return new TradingAccount(
                record.id(),
                record.userId(),
                record.currency(),
                record.balance(),
                record.frozen(),
                record.marginUsed(),
                TradingMybatisSupport.toOffsetDateTime(record.createdAt()),
                TradingMybatisSupport.toOffsetDateTime(record.updatedAt())
        );
    }
}
