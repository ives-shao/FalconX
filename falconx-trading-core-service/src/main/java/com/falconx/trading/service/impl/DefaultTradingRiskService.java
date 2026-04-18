package com.falconx.trading.service.impl;

import com.falconx.trading.calculator.LiquidationPriceCalculator;
import com.falconx.trading.calculator.MarginCalculator;
import com.falconx.trading.command.PlaceMarketOrderCommand;
import com.falconx.trading.config.TradingCoreServiceProperties;
import com.falconx.trading.entity.TradingAccount;
import com.falconx.trading.entity.TradingOrderSide;
import com.falconx.trading.entity.TradingQuoteSnapshot;
import com.falconx.trading.repository.TradingScheduleSnapshotRepository;
import com.falconx.trading.service.TradingRiskService;
import com.falconx.trading.service.TradingScheduleService;
import com.falconx.trading.service.model.TradingRiskDecision;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;

/**
 * 交易风险服务默认实现。
 *
 * <p>该实现承担 Stage 3B 的最小同步风控责任：
 *
 * <ul>
 *   <li>校验品种是否受支持</li>
 *   <li>校验价格是否 stale</li>
 *   <li>校验杠杆与数量是否合法</li>
 *   <li>校验账户可用余额是否足够覆盖保证金与手续费</li>
 * </ul>
 */
@Service
public class DefaultTradingRiskService implements TradingRiskService {

    private final TradingCoreServiceProperties properties;
    private final MarginCalculator marginCalculator;
    private final LiquidationPriceCalculator liquidationPriceCalculator;
    private final TradingScheduleService tradingScheduleService;
    private final TradingScheduleSnapshotRepository tradingScheduleSnapshotRepository;

    public DefaultTradingRiskService(TradingCoreServiceProperties properties,
                                     MarginCalculator marginCalculator,
                                     LiquidationPriceCalculator liquidationPriceCalculator,
                                     TradingScheduleService tradingScheduleService,
                                     TradingScheduleSnapshotRepository tradingScheduleSnapshotRepository) {
        this.properties = properties;
        this.marginCalculator = marginCalculator;
        this.liquidationPriceCalculator = liquidationPriceCalculator;
        this.tradingScheduleService = tradingScheduleService;
        this.tradingScheduleSnapshotRepository = tradingScheduleSnapshotRepository;
    }

    @Override
    public TradingRiskDecision evaluateMarketOrder(PlaceMarketOrderCommand command,
                                                   TradingAccount account,
                                                   TradingQuoteSnapshot quote) {
        // 交易核心不再持有本地静态 symbol 白名单。
        // “平台是否支持该产品”必须以 market-service 预热到 Redis 的交易时间快照为准，
        // 这样产品配置变更后无需同步修改 trading-core 配置，也不会出现两边白名单分叉。
        if (tradingScheduleSnapshotRepository.findBySymbol(command.symbol()).isEmpty()) {
            return reject("SYMBOL_NOT_SUPPORTED");
        }
        if (!tradingScheduleService.isTradable(command.symbol(), OffsetDateTime.now())) {
            return reject("SYMBOL_TRADING_SUSPENDED");
        }
        if (quote == null) {
            return reject("MARKET_QUOTE_NOT_FOUND");
        }
        if (quote.stale()) {
            return reject("MARKET_QUOTE_STALE");
        }
        if (command.quantity() == null || command.quantity().signum() <= 0) {
            return reject("INVALID_QUANTITY");
        }
        if (command.leverage() == null || command.leverage().signum() <= 0) {
            return reject("INVALID_LEVERAGE");
        }
        if (command.leverage().compareTo(properties.getMaxLeverage()) > 0) {
            return reject("LEVERAGE_EXCEEDED");
        }

        BigDecimal fillPrice = command.side() == TradingOrderSide.BUY ? quote.ask() : quote.bid();
        BigDecimal margin = marginCalculator.calculateInitialMargin(fillPrice, command.quantity(), command.leverage());
        BigDecimal fee = marginCalculator.calculateFee(fillPrice, command.quantity(), properties.getDefaultFeeRate());
        BigDecimal totalRequired = margin.add(fee);

        if (account.available().compareTo(totalRequired) < 0) {
            return reject("INSUFFICIENT_AVAILABLE_BALANCE");
        }

        BigDecimal liquidationPrice = liquidationPriceCalculator.calculate(
                command.side(),
                fillPrice,
                command.leverage(),
                properties.getMaintenanceMarginRate()
        );
        return new TradingRiskDecision(true, null, fillPrice, margin, fee, liquidationPrice);
    }

    private TradingRiskDecision reject(String reason) {
        return new TradingRiskDecision(false, reason, null, null, null, null);
    }
}
