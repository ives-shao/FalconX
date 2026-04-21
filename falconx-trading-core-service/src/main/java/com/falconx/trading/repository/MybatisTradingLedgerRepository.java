package com.falconx.trading.repository;

import com.falconx.infrastructure.id.IdGenerator;
import com.falconx.trading.entity.TradingLedgerEntry;
import com.falconx.trading.repository.mapper.TradingLedgerMapper;
import com.falconx.trading.repository.mapper.record.TradingLedgerRecord;
import com.falconx.trading.repository.mapper.record.TradingSwapSettlementRecord;
import com.falconx.trading.service.model.TradingSwapSettlementView;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 交易账本 Repository 的 MyBatis 实现。
 *
 * <p>该实现保持账本“只追加、不覆盖”的语义，
 * 并通过 XML SQL 落库三维资金快照。
 */
@Repository
public class MybatisTradingLedgerRepository implements TradingLedgerRepository {

    private final TradingLedgerMapper tradingLedgerMapper;
    private final IdGenerator idGenerator;

    public MybatisTradingLedgerRepository(TradingLedgerMapper tradingLedgerMapper, IdGenerator idGenerator) {
        this.tradingLedgerMapper = tradingLedgerMapper;
        this.idGenerator = idGenerator;
    }

    @Override
    public TradingLedgerEntry save(TradingLedgerEntry entry) {
        long id = entry.ledgerId() == null ? idGenerator.nextId() : entry.ledgerId();
        TradingLedgerEntry persisted = entry.ledgerId() == null
                ? new TradingLedgerEntry(
                id,
                entry.accountId(),
                entry.userId(),
                entry.bizType(),
                entry.amount(),
                entry.idempotencyKey(),
                entry.referenceNo(),
                entry.balanceBefore(),
                entry.balanceAfter(),
                entry.frozenBefore(),
                entry.frozenAfter(),
                entry.marginUsedBefore(),
                entry.marginUsedAfter(),
                entry.createdAt()
        )
                : entry;
        tradingLedgerMapper.insertTradingLedger(toRecord(persisted));
        return persisted;
    }

    @Override
    public List<TradingLedgerEntry> findByUserId(Long userId) {
        return tradingLedgerMapper.selectByUserId(userId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<TradingLedgerEntry> findByUserIdPaginated(Long userId, int offset, int limit) {
        return tradingLedgerMapper.selectByUserIdPaginated(userId, offset, limit).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public long countByUserId(Long userId) {
        return tradingLedgerMapper.countByUserId(userId);
    }

    @Override
    public List<TradingSwapSettlementView> findSwapSettlementsByUserId(Long userId, int offset, int limit) {
        return tradingLedgerMapper.selectSwapSettlementsByUserId(userId, offset, limit).stream()
                .map(this::toSwapSettlementView)
                .toList();
    }

    @Override
    public long countSwapSettlementsByUserId(Long userId) {
        return tradingLedgerMapper.countSwapSettlementsByUserId(userId);
    }

    @Override
    public boolean existsByUserIdAndIdempotencyKey(Long userId, String idempotencyKey) {
        return tradingLedgerMapper.countByUserIdAndIdempotencyKey(userId, idempotencyKey) > 0;
    }

    @Override
    public Optional<OffsetDateTime> findLatestSwapSettlementAt(Long userId, Long positionId) {
        LocalDateTime latestCreatedAt = tradingLedgerMapper.selectLatestSwapSettlementAt(userId, positionId);
        return latestCreatedAt == null
                ? Optional.empty()
                : Optional.of(TradingMybatisSupport.toOffsetDateTime(latestCreatedAt));
    }

    private TradingLedgerRecord toRecord(TradingLedgerEntry entry) {
        return new TradingLedgerRecord(
                entry.ledgerId(),
                entry.userId(),
                entry.accountId(),
                TradingMybatisSupport.toLedgerBizTypeCode(entry.bizType()),
                entry.idempotencyKey(),
                entry.referenceNo(),
                entry.amount(),
                entry.balanceBefore(),
                entry.balanceAfter(),
                entry.frozenBefore(),
                entry.frozenAfter(),
                entry.marginUsedBefore(),
                entry.marginUsedAfter(),
                TradingMybatisSupport.toLocalDateTime(entry.createdAt())
        );
    }

    private TradingLedgerEntry toDomain(TradingLedgerRecord record) {
        return new TradingLedgerEntry(
                record.id(),
                record.accountId(),
                record.userId(),
                TradingMybatisSupport.toLedgerBizType(record.bizTypeCode()),
                record.amount(),
                record.idempotencyKey(),
                record.referenceNo(),
                record.balanceBefore(),
                record.balanceAfter(),
                record.frozenBefore(),
                record.frozenAfter(),
                record.marginUsedBefore(),
                record.marginUsedAfter(),
                TradingMybatisSupport.toOffsetDateTime(record.createdAt())
        );
    }

    private TradingSwapSettlementView toSwapSettlementView(TradingSwapSettlementRecord record) {
        return new TradingSwapSettlementView(
                record.ledgerId(),
                record.positionId(),
                record.symbol(),
                record.sideCode() == null ? null : TradingMybatisSupport.toSide(record.sideCode()),
                TradingMybatisSupport.toLedgerBizType(record.bizTypeCode()),
                record.amount(),
                record.balanceAfter(),
                parseRolloverAt(record.rolloverAtText()),
                TradingMybatisSupport.toOffsetDateTime(record.settledAt()),
                record.referenceNo()
        );
    }

    private OffsetDateTime parseRolloverAt(String rolloverAtText) {
        if (rolloverAtText == null || rolloverAtText.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(rolloverAtText);
        } catch (DateTimeParseException exception) {
            throw new IllegalStateException("Invalid swap rolloverAt text: " + rolloverAtText, exception);
        }
    }
}
