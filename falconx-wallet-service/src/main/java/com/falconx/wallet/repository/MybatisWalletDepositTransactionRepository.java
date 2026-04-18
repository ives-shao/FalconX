package com.falconx.wallet.repository;

import com.falconx.domain.enums.ChainType;
import com.falconx.infrastructure.id.IdGenerator;
import com.falconx.wallet.entity.WalletDepositTransaction;
import com.falconx.wallet.repository.mapper.WalletDepositTransactionMapper;
import com.falconx.wallet.repository.mapper.record.WalletDepositTransactionRecord;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 原始链上入金 Repository 的 MyBatis 实现。
 *
 * <p>该实现把 `(chain, txHash)` 去重和确认数推进正式落到 `t_wallet_deposit_tx`。
 */
@Repository
public class MybatisWalletDepositTransactionRepository implements WalletDepositTransactionRepository {

    private final WalletDepositTransactionMapper walletDepositTransactionMapper;
    private final IdGenerator idGenerator;

    public MybatisWalletDepositTransactionRepository(WalletDepositTransactionMapper walletDepositTransactionMapper,
                                                     IdGenerator idGenerator) {
        this.walletDepositTransactionMapper = walletDepositTransactionMapper;
        this.idGenerator = idGenerator;
    }

    @Override
    public Optional<WalletDepositTransaction> findByChainAndTxHash(ChainType chain, String txHash) {
        return Optional.ofNullable(toDomain(
                walletDepositTransactionMapper.selectByChainAndTxHash(WalletMybatisSupport.toChainValue(chain), txHash)
        ));
    }

    @Override
    public List<WalletDepositTransaction> findByChainAndBlockRange(ChainType chain, long fromBlock, long toBlock) {
        return walletDepositTransactionMapper.selectByChainAndBlockRange(
                        WalletMybatisSupport.toChainValue(chain),
                        fromBlock,
                        toBlock
                ).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public WalletDepositTransaction save(WalletDepositTransaction transaction) {
        if (transaction.id() == null) {
            WalletDepositTransaction persisted = new WalletDepositTransaction(
                    idGenerator.nextId(),
                    transaction.userId(),
                    transaction.chain(),
                    transaction.token(),
                    transaction.txHash(),
                    transaction.fromAddress(),
                    transaction.toAddress(),
                    transaction.amount(),
                    transaction.blockNumber(),
                    transaction.confirmations(),
                    transaction.requiredConfirmations(),
                    transaction.status(),
                    transaction.detectedAt(),
                    transaction.confirmedAt(),
                    transaction.updatedAt()
            );
            walletDepositTransactionMapper.insertWalletDepositTransaction(toRecord(persisted));
            return persisted;
        }

        walletDepositTransactionMapper.updateWalletDepositTransaction(toRecord(transaction));
        return transaction;
    }

    private WalletDepositTransactionRecord toRecord(WalletDepositTransaction transaction) {
        return new WalletDepositTransactionRecord(
                transaction.id(),
                transaction.userId(),
                WalletMybatisSupport.toChainValue(transaction.chain()),
                transaction.token(),
                transaction.txHash(),
                transaction.fromAddress(),
                transaction.toAddress(),
                transaction.amount(),
                transaction.blockNumber(),
                transaction.confirmations(),
                transaction.requiredConfirmations(),
                WalletMybatisSupport.toDepositStatusCode(transaction.status()),
                WalletMybatisSupport.toLocalDateTime(transaction.detectedAt()),
                WalletMybatisSupport.toLocalDateTime(transaction.confirmedAt()),
                WalletMybatisSupport.toLocalDateTime(transaction.updatedAt())
        );
    }

    private WalletDepositTransaction toDomain(WalletDepositTransactionRecord record) {
        if (record == null) {
            return null;
        }
        return new WalletDepositTransaction(
                record.id(),
                record.userId(),
                WalletMybatisSupport.toChainType(record.chain()),
                record.token(),
                record.txHash(),
                record.fromAddress(),
                record.toAddress(),
                record.amount(),
                record.blockNumber(),
                record.confirmations(),
                record.requiredConfirmations(),
                WalletMybatisSupport.toDepositStatus(record.statusCode()),
                WalletMybatisSupport.toOffsetDateTime(record.detectedAt()),
                WalletMybatisSupport.toOffsetDateTime(record.confirmedAt()),
                WalletMybatisSupport.toOffsetDateTime(record.updatedAt())
        );
    }
}
