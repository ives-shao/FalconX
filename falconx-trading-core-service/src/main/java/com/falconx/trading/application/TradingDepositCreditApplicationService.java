package com.falconx.trading.application;

import com.falconx.trading.command.CreditConfirmedDepositCommand;
import com.falconx.trading.config.TradingCoreServiceProperties;
import com.falconx.trading.contract.event.DepositCreditedEventPayload;
import com.falconx.trading.dto.DepositCreditResult;
import com.falconx.trading.entity.TradingAccount;
import com.falconx.trading.entity.TradingDeposit;
import com.falconx.trading.entity.TradingDepositStatus;
import com.falconx.trading.entity.TradingOutboxMessage;
import com.falconx.trading.entity.TradingOutboxStatus;
import com.falconx.trading.repository.TradingDepositRepository;
import com.falconx.trading.repository.TradingInboxRepository;
import com.falconx.trading.repository.TradingOutboxRepository;
import com.falconx.trading.service.TradingAccountService;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 业务入金入账应用服务。
 *
 * <p>该服务是 Stage 3B 最关键的交易核心编排链路之一，用于把 `wallet.deposit.confirmed`
 * 事件转换成 trading-core-service owner 的最终业务事实：
 *
 * <ol>
 *   <li>按 `eventId` 做低频关键事件幂等</li>
 *   <li>按 `(chain, txHash)` 做业务入金幂等</li>
 *   <li>创建或读取交易账户并入账</li>
 *   <li>写 `t_deposit` 业务事实</li>
 *   <li>写 `t_outbox`，为 `falconx.trading.deposit.credited` 做后续发布准备</li>
 * </ol>
 */
@Service
public class TradingDepositCreditApplicationService {

    private static final Logger log = LoggerFactory.getLogger(TradingDepositCreditApplicationService.class);

    private final TradingCoreServiceProperties properties;
    private final TradingAccountService tradingAccountService;
    private final TradingDepositRepository tradingDepositRepository;
    private final TradingInboxRepository tradingInboxRepository;
    private final TradingOutboxRepository tradingOutboxRepository;

    public TradingDepositCreditApplicationService(TradingCoreServiceProperties properties,
                                                  TradingAccountService tradingAccountService,
                                                  TradingDepositRepository tradingDepositRepository,
                                                  TradingInboxRepository tradingInboxRepository,
                                                  TradingOutboxRepository tradingOutboxRepository) {
        this.properties = properties;
        this.tradingAccountService = tradingAccountService;
        this.tradingDepositRepository = tradingDepositRepository;
        this.tradingInboxRepository = tradingInboxRepository;
        this.tradingOutboxRepository = tradingOutboxRepository;
    }

    /**
     * 执行业务入金入账。
     *
     * @param command 钱包确认入金命令
     * @return 入账结果
     */
    @Transactional
    public DepositCreditResult creditConfirmedDeposit(CreditConfirmedDepositCommand command) {
        log.info("trading.deposit.credit.request eventId={} userId={} chain={} txHash={}",
                command.eventId(),
                command.userId(),
                command.chain(),
                command.txHash());

        TradingDeposit existing = tradingDepositRepository.findByChainAndTxHash(command.chain(), command.txHash())
                .orElse(null);
        if (existing != null) {
            tradingInboxRepository.markProcessedIfAbsent(
                    command.eventId(),
                    "wallet.deposit.confirmed",
                    command.confirmedAt()
            );
            TradingAccount account = tradingAccountService.getOrCreateAccount(existing.userId(), properties.getSettlementToken());
            log.info("trading.deposit.credit.duplicate txHash={} depositId={} userId={}",
                    command.txHash(),
                    existing.depositId(),
                    existing.userId());
            return new DepositCreditResult(existing, account, true);
        }

        TradingAccount account = tradingAccountService.creditDeposit(
                command.userId(),
                properties.getSettlementToken(),
                command.amount(),
                "deposit-credit:" + command.chain() + ":" + command.txHash(),
                command.txHash(),
                command.confirmedAt()
        );
        TradingDeposit deposit = tradingDepositRepository.save(new TradingDeposit(
                null,
                command.userId(),
                account.accountId(),
                command.chain(),
                command.token(),
                command.txHash(),
                command.amount(),
                TradingDepositStatus.CREDITED,
                command.confirmedAt(),
                null
        ));

        tradingOutboxRepository.save(new TradingOutboxMessage(
                null,
                "deposit-credited:" + command.chain() + ":" + command.txHash(),
                "trading.deposit.credited",
                String.valueOf(command.userId()),
                new DepositCreditedEventPayload(
                        deposit.depositId(),
                        command.userId(),
                        account.accountId(),
                        command.chain().name(),
                        command.token(),
                        command.txHash(),
                        command.amount(),
                        command.confirmedAt()
                ),
                TradingOutboxStatus.PENDING,
                command.confirmedAt(),
                null,
                0,
                command.confirmedAt(),
                null
        ));
        tradingInboxRepository.markProcessedIfAbsent(command.eventId(), "wallet.deposit.confirmed", command.confirmedAt());

        log.info("trading.deposit.credit.completed eventId={} userId={} depositId={} accountId={}",
                command.eventId(),
                command.userId(),
                deposit.depositId(),
                account.accountId());
        return new DepositCreditResult(deposit, account, false);
    }
}
