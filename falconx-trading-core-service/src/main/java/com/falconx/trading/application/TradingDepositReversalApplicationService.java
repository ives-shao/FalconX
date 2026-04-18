package com.falconx.trading.application;

import com.falconx.trading.command.ReverseWalletDepositCommand;
import com.falconx.trading.config.TradingCoreServiceProperties;
import com.falconx.trading.dto.DepositReversalResult;
import com.falconx.trading.entity.TradingAccount;
import com.falconx.trading.entity.TradingDeposit;
import com.falconx.trading.entity.TradingDepositStatus;
import com.falconx.trading.repository.TradingDepositRepository;
import com.falconx.trading.repository.TradingInboxRepository;
import com.falconx.trading.service.TradingAccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 业务入金反转应用服务。
 *
 * <p>该服务用于处理 `wallet.deposit.reversed` 事件对应的交易域回退动作。
 * 当前阶段先冻结最小骨架：
 *
 * <ul>
 *   <li>按 `eventId` 做低频事件幂等</li>
 *   <li>按 `(chain, txHash)` 找到已入账业务事实</li>
 *   <li>若业务事实仍是 `CREDITED`，则回退账户余额并把业务事实标记为 `REVERSED`</li>
 * </ul>
 */
@Service
public class TradingDepositReversalApplicationService {

    private static final Logger log = LoggerFactory.getLogger(TradingDepositReversalApplicationService.class);

    private final TradingCoreServiceProperties properties;
    private final TradingAccountService tradingAccountService;
    private final TradingDepositRepository tradingDepositRepository;
    private final TradingInboxRepository tradingInboxRepository;

    public TradingDepositReversalApplicationService(TradingCoreServiceProperties properties,
                                                    TradingAccountService tradingAccountService,
                                                    TradingDepositRepository tradingDepositRepository,
                                                    TradingInboxRepository tradingInboxRepository) {
        this.properties = properties;
        this.tradingAccountService = tradingAccountService;
        this.tradingDepositRepository = tradingDepositRepository;
        this.tradingInboxRepository = tradingInboxRepository;
    }

    /**
     * 执行业务入金反转。
     *
     * @param command 钱包回滚命令
     * @return 反转结果；若原业务事实不存在则返回空结果并标记重复为 `false`
     */
    @Transactional
    public DepositReversalResult reverseDeposit(ReverseWalletDepositCommand command) {
        log.info("trading.deposit.reverse.request eventId={} userId={} chain={} txHash={}",
                command.eventId(),
                command.userId(),
                command.chain(),
                command.txHash());

        TradingDeposit existing = tradingDepositRepository.findByChainAndTxHash(command.chain(), command.txHash())
                .orElse(null);
        if (existing == null) {
            tradingInboxRepository.markProcessedIfAbsent(command.eventId(), "wallet.deposit.reversed", command.reversedAt());
            log.warn("trading.deposit.reverse.missing txHash={} userId={}", command.txHash(), command.userId());
            return new DepositReversalResult(null, null, false);
        }
        if (existing.status() == TradingDepositStatus.REVERSED) {
            tradingInboxRepository.markProcessedIfAbsent(command.eventId(), "wallet.deposit.reversed", command.reversedAt());
            TradingAccount account = tradingAccountService.getOrCreateAccount(existing.userId(), properties.getSettlementToken());
            log.info("trading.deposit.reverse.duplicate txHash={} depositId={}", command.txHash(), existing.depositId());
            return new DepositReversalResult(existing, account, true);
        }

        TradingAccount account = tradingAccountService.reverseDeposit(
                existing.userId(),
                properties.getSettlementToken(),
                existing.amount(),
                "deposit-reversal:" + existing.chain() + ":" + existing.txHash(),
                existing.txHash(),
                command.reversedAt()
        );
        TradingDeposit reversed = tradingDepositRepository.save(new TradingDeposit(
                existing.depositId(),
                existing.userId(),
                existing.accountId(),
                existing.chain(),
                existing.token(),
                existing.txHash(),
                existing.amount(),
                TradingDepositStatus.REVERSED,
                existing.creditedAt(),
                command.reversedAt()
        ));
        tradingInboxRepository.markProcessedIfAbsent(command.eventId(), "wallet.deposit.reversed", command.reversedAt());

        log.info("trading.deposit.reverse.completed eventId={} userId={} depositId={}",
                command.eventId(),
                existing.userId(),
                reversed.depositId());
        return new DepositReversalResult(reversed, account, false);
    }
}
