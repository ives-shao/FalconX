package com.falconx.trading.engine;

import com.falconx.trading.entity.TradingPosition;
import com.falconx.trading.entity.TradingPositionStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * OPEN 持仓内存快照默认实现。
 *
 * <p>该实现用按 `symbol -> positionId -> TradingPosition` 的双层索引承载实时持仓视图，
 * 让高频 `QuoteDrivenEngine` 能做到：
 *
 * <ul>
 *   <li>O(1) 定位某个品种的持仓集合</li>
 *   <li>不因每个 tick 去查 MySQL</li>
 *   <li>账户查询也可以直接复用当前快照视图</li>
 * </ul>
 */
@Component
public class InMemoryOpenPositionSnapshotStore implements OpenPositionSnapshotStore {

    private volatile ConcurrentHashMap<String, ConcurrentHashMap<Long, TradingPosition>> positionsBySymbol =
            new ConcurrentHashMap<>();

    @Override
    public void replaceAll(List<TradingPosition> positions) {
        ConcurrentHashMap<String, ConcurrentHashMap<Long, TradingPosition>> refreshedSnapshot = new ConcurrentHashMap<>();
        positions.forEach(position -> {
            if (position == null || position.positionId() == null || position.status() != TradingPositionStatus.OPEN) {
                return;
            }
            refreshedSnapshot
                    .computeIfAbsent(position.symbol(), ignored -> new ConcurrentHashMap<>())
                    .put(position.positionId(), position);
        });
        positionsBySymbol = refreshedSnapshot;
    }

    @Override
    public void upsert(TradingPosition position) {
        if (position == null || position.positionId() == null) {
            return;
        }
        if (position.status() != TradingPositionStatus.OPEN) {
            remove(position.symbol(), position.positionId());
            return;
        }
        positionsBySymbol
                .computeIfAbsent(position.symbol(), ignored -> new ConcurrentHashMap<>())
                .put(position.positionId(), position);
    }

    @Override
    public void remove(String symbol, Long positionId) {
        if (symbol == null || positionId == null) {
            return;
        }
        Map<Long, TradingPosition> positions = positionsBySymbol.get(symbol);
        if (positions == null) {
            return;
        }
        positions.remove(positionId);
        if (positions.isEmpty()) {
            positionsBySymbol.remove(symbol, positions);
        }
    }

    @Override
    public List<TradingPosition> listOpenBySymbol(String symbol) {
        Map<Long, TradingPosition> positions = positionsBySymbol.get(symbol);
        if (positions == null || positions.isEmpty()) {
            return List.of();
        }
        List<TradingPosition> snapshot = new ArrayList<>(positions.values());
        snapshot.sort(Comparator.comparing(TradingPosition::openedAt));
        return List.copyOf(snapshot);
    }

    @Override
    public List<TradingPosition> listOpenByUserId(Long userId) {
        List<TradingPosition> snapshot = positionsBySymbol.values().stream()
                .flatMap(map -> map.values().stream())
                .filter(position -> position.userId().equals(userId))
                .sorted(Comparator.comparing(TradingPosition::openedAt).reversed())
                .toList();
        return List.copyOf(snapshot);
    }
}
