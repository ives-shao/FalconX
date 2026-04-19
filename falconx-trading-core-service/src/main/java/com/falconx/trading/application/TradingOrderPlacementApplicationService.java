package com.falconx.trading.application;

import com.falconx.trading.command.PlaceMarketOrderCommand;
import com.falconx.trading.config.TradingCoreServiceProperties;
import com.falconx.trading.dto.OrderPlacementResult;
import com.falconx.trading.engine.OpenPositionSnapshotStore;
import com.falconx.trading.entity.TradingAccount;
import com.falconx.trading.entity.TradingMarginMode;
import com.falconx.trading.entity.TradingOrder;
import com.falconx.trading.entity.TradingOrderStatus;
import com.falconx.trading.entity.TradingOrderType;
import com.falconx.trading.entity.TradingOutboxMessage;
import com.falconx.trading.entity.TradingOutboxStatus;
import com.falconx.trading.entity.TradingPosition;
import com.falconx.trading.entity.TradingPositionStatus;
import com.falconx.trading.entity.TradingQuoteSnapshot;
import com.falconx.trading.entity.TradingTrade;
import com.falconx.trading.entity.TradingTradeType;
import com.falconx.trading.repository.TradingOrderRepository;
import com.falconx.trading.repository.TradingOutboxRepository;
import com.falconx.trading.repository.TradingPositionRepository;
import com.falconx.trading.repository.TradingQuoteSnapshotRepository;
import com.falconx.trading.repository.TradingTradeRepository;
import com.falconx.trading.service.TradingAccountService;
import com.falconx.trading.service.TradingRiskObservabilityService;
import com.falconx.trading.service.TradingRiskService;
import com.falconx.trading.service.model.TradingRiskDecision;
import com.falconx.trading.support.TradingPricingSupport;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 市价单下单应用服务。
 *
 * <p>该服务是 Stage 3B 交易核心的最小同步主链路：
 *
 * <ol>
 *   <li>按 `(userId, clientOrderId)` 做请求幂等</li>
 *   <li>读取最新市场快照</li>
 *   <li>执行同步风控</li>
 *   <li>完成保证金预留、手续费扣减和保证金确认</li>
 *   <li>写订单、持仓、成交、净敞口和 Outbox 事件骨架</li>
 *   <li>同步刷新 OPEN 持仓内存快照，供 `QuoteDrivenEngine` 高频读取</li>
 * </ol>
 */
@Service
public class TradingOrderPlacementApplicationService {

    private static final Logger log = LoggerFactory.getLogger(TradingOrderPlacementApplicationService.class);

    private final TradingCoreServiceProperties properties;
    private final TradingAccountService tradingAccountService;
    private final TradingRiskService tradingRiskService;
    private final TradingQuoteSnapshotRepository tradingQuoteSnapshotRepository;
    private final TradingOrderRepository tradingOrderRepository;
    private final TradingPositionRepository tradingPositionRepository;
    private final TradingTradeRepository tradingTradeRepository;
    private final TradingOutboxRepository tradingOutboxRepository;
    private final TradingRiskObservabilityService tradingRiskObservabilityService;
    private final OpenPositionSnapshotStore openPositionSnapshotStore;

    public TradingOrderPlacementApplicationService(TradingCoreServiceProperties properties,
                                                   TradingAccountService tradingAccountService,
                                                   TradingRiskService tradingRiskService,
                                                   TradingQuoteSnapshotRepository tradingQuoteSnapshotRepository,
                                                   TradingOrderRepository tradingOrderRepository,
                                                   TradingPositionRepository tradingPositionRepository,
                                                   TradingTradeRepository tradingTradeRepository,
                                                   TradingOutboxRepository tradingOutboxRepository,
                                                   TradingRiskObservabilityService tradingRiskObservabilityService,
                                                   OpenPositionSnapshotStore openPositionSnapshotStore) {
        this.properties = properties;
        this.tradingAccountService = tradingAccountService;
        this.tradingRiskService = tradingRiskService;
        this.tradingQuoteSnapshotRepository = tradingQuoteSnapshotRepository;
        this.tradingOrderRepository = tradingOrderRepository;
        this.tradingPositionRepository = tradingPositionRepository;
        this.tradingTradeRepository = tradingTradeRepository;
        this.tradingOutboxRepository = tradingOutboxRepository;
        this.tradingRiskObservabilityService = tradingRiskObservabilityService;
        this.openPositionSnapshotStore = openPositionSnapshotStore;
    }

    /**
     * 处理一笔市价开仓请求。
     *
     * @param command 市价单命令
     * @return 下单结果；可能是已成交、已拒绝或幂等返回
     */
    @Transactional
    public OrderPlacementResult placeMarketOrder(PlaceMarketOrderCommand command) {
        log.info("trading.order.place.request userId={} symbol={} clientOrderId={}",
                command.userId(),
                command.symbol(),
                command.clientOrderId());

        // 下单链路先按 `(userId, clientOrderId)` 做幂等短路。
        // 这样即使客户端重复提交，也不会再次扣费、再次占用保证金或生成第二个持仓。
        TradingOrder existingOrder = tradingOrderRepository.findByUserIdAndClientOrderId(command.userId(), command.clientOrderId())
                .orElse(null);
        if (existingOrder != null) {
            TradingPosition existingPosition = tradingPositionRepository.findByOpeningOrderId(existingOrder.orderId()).orElse(null);
            TradingTrade existingTrade = tradingTradeRepository.findOpenTradeByOrderId(existingOrder.orderId()).orElse(null);
            TradingAccount account = tradingAccountService.getOrCreateAccount(command.userId(), properties.getSettlementToken());
            log.info("trading.order.place.duplicate userId={} orderNo={} clientOrderId={}",
                    command.userId(),
                    existingOrder.orderNo(),
                    command.clientOrderId());
            return new OrderPlacementResult(existingOrder, existingPosition, existingTrade, account, true, existingOrder.rejectReason());
        }

        TradingAccount account = tradingAccountService.getOrCreateAccountForUpdate(command.userId(), properties.getSettlementToken());
        TradingQuoteSnapshot quote = tradingQuoteSnapshotRepository.findBySymbol(command.symbol()).orElse(null);
        TradingRiskDecision decision = tradingRiskService.evaluateMarketOrder(command, account, quote);

        // 风控拒单也要持久化一条 REJECTED 订单，避免只有日志没有业务事实。
        // 这样后续排查时可以从订单表直接看到“为什么没成交”，而不是只能依赖运行日志。
        if (!decision.accepted()) {
            BigDecimal requestedPrice = TradingPricingSupport.resolveOrderReferencePrice(quote, command.side());
            TradingOrder rejectedOrder = tradingOrderRepository.save(new TradingOrder(
                    null,
                    null,
                    command.userId(),
                    command.symbol(),
                    command.side(),
                    TradingOrderType.MARKET,
                    command.quantity(),
                    requestedPrice,
                    null,
                    command.leverage(),
                    BigDecimal.ZERO.setScale(8),
                    BigDecimal.ZERO.setScale(8),
                    command.clientOrderId(),
                    TradingOrderStatus.REJECTED,
                    decision.rejectReason(),
                    OffsetDateTime.now(),
                    OffsetDateTime.now()
            ));
            log.warn("trading.order.place.rejected userId={} symbol={} clientOrderId={} reason={}",
                    command.userId(),
                    command.symbol(),
                    command.clientOrderId(),
                    decision.rejectReason());
            return new OrderPlacementResult(rejectedOrder, null, null, account, false, decision.rejectReason());
        }

        OffsetDateTime now = OffsetDateTime.now();
        // 资金侧固定走“预留 -> 扣费 -> 确认保证金占用”的顺序。
        // 这样可以让账本完整记录资金变化轨迹，并保证订单一旦进入 FILLED，
        // 账户侧已经处于最终一致的已成交状态，而不是停留在“冻结但未确认”的中间态。
        account = tradingAccountService.reserveMargin(
                command.userId(),
                properties.getSettlementToken(),
                decision.margin(),
                "order-margin-reserve:" + command.clientOrderId(),
                command.clientOrderId(),
                now
        );
        account = tradingAccountService.chargeFee(
                command.userId(),
                properties.getSettlementToken(),
                decision.fee(),
                "order-fee-charge:" + command.clientOrderId(),
                command.clientOrderId(),
                now
        );
        account = tradingAccountService.confirmMarginUsed(
                command.userId(),
                properties.getSettlementToken(),
                decision.margin(),
                "order-margin-confirm:" + command.clientOrderId(),
                command.clientOrderId(),
                now
        );

        // 订单、持仓、成交、净敞口和 Outbox 放在同一本地事务里落库。
        // 这里的目标不是追求“最少写表”，而是确保撮合结果、仓位事实和后续事件源数据同时成立，
        // 避免出现“账户已扣费但没有持仓”或“持仓已创建但没有事件”的断裂状态。
        TradingOrder order = tradingOrderRepository.save(new TradingOrder(
                null,
                null,
                command.userId(),
                command.symbol(),
                command.side(),
                TradingOrderType.MARKET,
                command.quantity(),
                TradingPricingSupport.resolveOrderReferencePrice(quote, command.side()),
                decision.fillPrice(),
                command.leverage(),
                decision.margin(),
                decision.fee(),
                command.clientOrderId(),
                TradingOrderStatus.FILLED,
                null,
                now,
                now
        ));
        TradingPosition position = tradingPositionRepository.save(new TradingPosition(
                null,
                order.orderId(),
                command.userId(),
                command.symbol(),
                command.side(),
                command.quantity(),
                decision.fillPrice(),
                command.leverage(),
                decision.margin(),
                TradingMarginMode.ISOLATED,
                decision.liquidationPrice(),
                command.takeProfitPrice(),
                command.stopLossPrice(),
                null,
                null,
                null,
                TradingPositionStatus.OPEN,
                now,
                null,
                now
        ));
        TradingTrade trade = tradingTradeRepository.save(new TradingTrade(
                null,
                order.orderId(),
                position.positionId(),
                command.userId(),
                command.symbol(),
                command.side(),
                TradingTradeType.OPEN,
                command.quantity(),
                decision.fillPrice(),
                decision.fee(),
                BigDecimal.ZERO.setScale(8),
                now
        ));
        tradingRiskObservabilityService.applyOpenPosition(
                command.symbol(),
                command.side(),
                command.quantity(),
                quote,
                now,
                position.positionId()
        );

        // OPEN 持仓快照同步写入内存视图，供报价驱动引擎做高频只读判断。
        // 这里不替代 MySQL 事实表，只是把“当前仍 OPEN 的最小必要信息”提前放到内存，
        // 避免后续 tick 处理每次都扫库。
        // 内存快照必须晚于数据库事务提交再更新。
        // 否则后续任何 SQL 或 Outbox 写入失败都会导致事务回滚，但内存里已经提前出现 OPEN 仓位，
        // 形成“数据库不存在、内存仍可见”的幽灵持仓。
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        openPositionSnapshotStore.upsert(position);
                    }
                }
        );

        enqueueOrderEvents(order, position, trade, now);

        log.info("trading.order.place.completed userId={} orderNo={} positionId={} tradeId={}",
                command.userId(),
                order.orderNo(),
                position.positionId(),
                trade.tradeId());
        return new OrderPlacementResult(order, position, trade, account, false, null);
    }

    private void enqueueOrderEvents(TradingOrder order,
                                    TradingPosition position,
                                    TradingTrade trade,
                                    OffsetDateTime occurredAt) {
        tradingOutboxRepository.save(new TradingOutboxMessage(
                null,
                "order-created:" + order.orderNo(),
                "trading.order.created",
                String.valueOf(order.userId()),
                Map.of(
                        "orderNo", order.orderNo(),
                        "userId", order.userId(),
                        "symbol", order.symbol(),
                        "status", order.status().name()
                ),
                TradingOutboxStatus.PENDING,
                occurredAt,
                null,
                0,
                occurredAt,
                null
        ));
        tradingOutboxRepository.save(new TradingOutboxMessage(
                null,
                "order-filled:" + order.orderNo(),
                "trading.order.filled",
                String.valueOf(order.userId()),
                Map.of(
                        "orderNo", order.orderNo(),
                        "positionId", position.positionId(),
                        "tradeId", trade.tradeId(),
                        "filledPrice", order.filledPrice(),
                        "quantity", order.quantity()
                ),
                TradingOutboxStatus.PENDING,
                occurredAt,
                null,
                0,
                occurredAt,
                null
        ));
    }
}
