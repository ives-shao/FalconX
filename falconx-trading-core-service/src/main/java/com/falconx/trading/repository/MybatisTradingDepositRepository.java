package com.falconx.trading.repository;

import com.falconx.infrastructure.id.IdGenerator;
import com.falconx.trading.entity.TradingDeposit;
import com.falconx.trading.repository.mapper.TradingDepositMapper;
import com.falconx.trading.repository.mapper.record.TradingDepositRecord;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 业务入金 Repository 的 MyBatis 实现。
 *
 * <p>该实现负责 `t_deposit` 的 owner 持久化读写，
 * 并保持 `walletTxId` 的最小业务幂等查询入口。
 */
@Repository
public class MybatisTradingDepositRepository implements TradingDepositRepository {

    private final TradingDepositMapper tradingDepositMapper;
    private final IdGenerator idGenerator;

    public MybatisTradingDepositRepository(TradingDepositMapper tradingDepositMapper, IdGenerator idGenerator) {
        this.tradingDepositMapper = tradingDepositMapper;
        this.idGenerator = idGenerator;
    }

    @Override
    public Optional<TradingDeposit> findByWalletTxId(Long walletTxId) {
        return Optional.ofNullable(toDomain(
                tradingDepositMapper.selectByWalletTxId(walletTxId)
        ));
    }

    @Override
    public TradingDeposit save(TradingDeposit deposit) {
        if (deposit.depositId() == null) {
            long id = idGenerator.nextId();
            TradingDeposit persisted = new TradingDeposit(
                    id,
                    deposit.walletTxId(),
                    deposit.userId(),
                    deposit.accountId(),
                    deposit.chain(),
                    deposit.token(),
                    deposit.txHash(),
                    deposit.amount(),
                    deposit.status(),
                    deposit.creditedAt(),
                    deposit.reversedAt()
            );
            tradingDepositMapper.insertTradingDeposit(toRecord(persisted));
            return persisted;
        }

        tradingDepositMapper.updateTradingDeposit(toRecord(deposit));
        return deposit;
    }

    private TradingDepositRecord toRecord(TradingDeposit deposit) {
        return new TradingDepositRecord(
                deposit.depositId(),
                deposit.walletTxId(),
                deposit.userId(),
                deposit.accountId(),
                TradingMybatisSupport.toChainValue(deposit.chain()),
                deposit.token(),
                deposit.txHash(),
                deposit.amount(),
                TradingMybatisSupport.toDepositStatusCode(deposit.status()),
                TradingMybatisSupport.toLocalDateTime(deposit.creditedAt()),
                TradingMybatisSupport.toLocalDateTime(deposit.reversedAt()),
                TradingMybatisSupport.toLocalDateTime(
                        deposit.creditedAt() == null ? OffsetDateTime.now() : deposit.creditedAt()
                )
        );
    }

    private TradingDeposit toDomain(TradingDepositRecord record) {
        if (record == null) {
            return null;
        }
        return new TradingDeposit(
                record.id(),
                record.walletTxId(),
                record.userId(),
                record.accountId(),
                TradingMybatisSupport.toChainType(record.chain()),
                record.token(),
                record.txHash(),
                record.amount(),
                TradingMybatisSupport.toDepositStatus(record.statusCode()),
                TradingMybatisSupport.toOffsetDateTime(record.creditedAt()),
                TradingMybatisSupport.toOffsetDateTime(record.reversedAt())
        );
    }
}
