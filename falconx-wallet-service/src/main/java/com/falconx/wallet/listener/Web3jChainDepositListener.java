package com.falconx.wallet.listener;

import com.falconx.domain.enums.ChainType;
import com.falconx.wallet.client.WalletBlockchainClientFactory;
import com.falconx.wallet.config.WalletServiceProperties;
import com.falconx.wallet.entity.WalletChainCursor;
import com.falconx.wallet.entity.WalletDepositStatus;
import com.falconx.wallet.entity.WalletDepositTransaction;
import com.falconx.wallet.repository.WalletAddressRepository;
import com.falconx.wallet.repository.WalletChainCursorRepository;
import com.falconx.wallet.repository.WalletDepositTransactionRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterNumber;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

/**
 * 基于 Web3j 的 EVM 链监听器。
 *
 * <p>该监听器当前只落地 Stage 6A 的最小真实链路：
 *
 * <ul>
 *   <li>按 owner 游标扫描 EVM 最新区块范围</li>
 *   <li>识别原生币转入平台地址的交易</li>
 *   <li>把链事实转换成统一的 {@link ObservedDepositTransaction}</li>
 *   <li>把扫描推进位置持续回写到 `t_wallet_chain_cursor`</li>
 * </ul>
 *
 * <p>当前实现刻意只处理原生币入金，不在本类里混入 ERC20 日志解析、业务入账或事件发布。
 * 这些职责仍然分别属于 listener 下游的应用层与后续阶段实现。
 */
public class Web3jChainDepositListener implements ChainDepositListener, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(Web3jChainDepositListener.class);
    private static final BigInteger ZERO = BigInteger.ZERO;
    private static final BigInteger ONE = BigInteger.ONE;
    private static final BigDecimal EVM_DECIMAL_FACTOR = BigDecimal.TEN.pow(18);

    private final ChainType chainType;
    private final WalletServiceProperties.Chain chainProperties;
    private final WalletBlockchainClientFactory walletBlockchainClientFactory;
    private final WalletAddressRepository walletAddressRepository;
    private final WalletChainCursorRepository walletChainCursorRepository;
    private final WalletDepositTransactionRepository walletDepositTransactionRepository;

    private Web3j web3j;
    private ScheduledExecutorService pollingExecutor;
    private Consumer<ObservedDepositTransaction> depositConsumer;
    private volatile BigInteger lastProcessedBlockNumber;

    public Web3jChainDepositListener(ChainType chainType,
                                     WalletServiceProperties.Chain chainProperties,
                                     WalletBlockchainClientFactory walletBlockchainClientFactory,
                                     WalletAddressRepository walletAddressRepository,
                                     WalletChainCursorRepository walletChainCursorRepository,
                                     WalletDepositTransactionRepository walletDepositTransactionRepository) {
        this.chainType = chainType;
        this.chainProperties = chainProperties;
        this.walletBlockchainClientFactory = walletBlockchainClientFactory;
        this.walletAddressRepository = walletAddressRepository;
        this.walletChainCursorRepository = walletChainCursorRepository;
        this.walletDepositTransactionRepository = walletDepositTransactionRepository;
    }

    @Override
    public ChainType chainType() {
        return chainType;
    }

    @Override
    public void start(Consumer<ObservedDepositTransaction> depositConsumer) {
        this.depositConsumer = depositConsumer;
        if (web3j == null) {
            web3j = walletBlockchainClientFactory.createEvmClient(chainProperties.getRpcUrl());
        }
        if (lastProcessedBlockNumber == null) {
            lastProcessedBlockNumber = resolveInitialCursorValue();
        }
        log.info("wallet.listener.started chain={} client=web3j rpcUrl={} requiredConfirmations={} scanInterval={}",
                chainType,
                chainProperties.getRpcUrl(),
                chainProperties.getRequiredConfirmations(),
                chainProperties.getScanInterval());
        // 启动后立即做一次同步：
        // - 若 owner 游标仍是初始值，则仅把基线对齐到最新链头，避免首次启动误扫全历史区块
        // - 若 owner 游标已经存在真实扫描进度，则从最近确认窗口继续扫块并回调应用层
        synchronizeObservedDeposits("bootstrap");
        if (pollingExecutor == null) {
            pollingExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "wallet-" + chainType.name().toLowerCase() + "-head-poller");
                thread.setDaemon(true);
                return thread;
            });
            long intervalMillis = Math.max(1000L, chainProperties.getScanInterval().toMillis());
            pollingExecutor.scheduleWithFixedDelay(
                    () -> synchronizeObservedDeposits("poll"),
                    intervalMillis,
                    intervalMillis,
                    TimeUnit.MILLISECONDS
            );
        }
    }

    private void synchronizeObservedDeposits(String trigger) {
        try {
            BigInteger latestBlockNumber = fetchLatestBlockNumber();
            if (latestBlockNumber == null) {
                log.warn("wallet.listener.chainHead.missing chain={} trigger={} rpcUrl={}",
                        chainType,
                        trigger,
                        chainProperties.getRpcUrl());
                return;
            }

            if (isBootstrapBaselineRequired()) {
                persistProcessedCursor(latestBlockNumber);
                log.info("wallet.listener.bootstrap.baselineReady chain={} trigger={} blockNumber={} rpcUrl={}",
                        chainType,
                        trigger,
                        latestBlockNumber,
                        chainProperties.getRpcUrl());
                return;
            }

            BigInteger scanStartBlock = resolveScanStartBlock(latestBlockNumber);
            BigInteger trackedWindowEnd = latestBlockNumber.max(lastProcessedBlockNumber);
            Set<String> assignedAddressSnapshot = walletAddressRepository.findAssignedAddressesByChain(chainType);
            List<WalletDepositTransaction> trackedTransactionsInWindow = walletDepositTransactionRepository.findByChainAndBlockRange(
                    chainType,
                    scanStartBlock.longValueExact(),
                    trackedWindowEnd.longValueExact()
            );
            Set<String> observedTransactionHashes = new LinkedHashSet<>();
            for (BigInteger blockNumber = scanStartBlock; blockNumber.compareTo(latestBlockNumber) <= 0; blockNumber = blockNumber.add(ONE)) {
                processBlock(blockNumber, latestBlockNumber, assignedAddressSnapshot, observedTransactionHashes);
            }
            reconcileMissingConfirmedTransactions(trackedTransactionsInWindow, observedTransactionHashes);
            persistProcessedCursor(latestBlockNumber);
            log.info("wallet.listener.chainHead.synced chain={} trigger={} blockNumber={} scanStart={} addressCount={} rpcUrl={}",
                    chainType,
                    trigger,
                    latestBlockNumber,
                    scanStartBlock,
                    assignedAddressSnapshot.size(),
                    chainProperties.getRpcUrl());
        } catch (IOException | RuntimeException ex) {
            // 区块抓取、回执读取或下游应用层处理失败时，不推进当前区块游标。
            // 下一轮轮询会从最后一个成功区块继续重试，依赖应用层 `(chain, txHash)` 幂等避免重复副作用。
            log.warn("wallet.listener.chainHead.syncFailed chain={} trigger={} rpcUrl={} lastProcessedBlock={}",
                    chainType,
                    trigger,
                    chainProperties.getRpcUrl(),
                    lastProcessedBlockNumber,
                    ex);
        }
    }

    private BigInteger fetchLatestBlockNumber() throws IOException {
        return web3j.ethBlockNumber().send().getBlockNumber();
    }

    private BigInteger resolveInitialCursorValue() {
        WalletChainCursor currentCursor = walletChainCursorRepository.findByChain(chainType);
        if (currentCursor == null || currentCursor.cursorValue() == null || currentCursor.cursorValue().isBlank()) {
            return parseCursorValue(chainProperties.getInitialCursor());
        }
        return parseCursorValue(currentCursor.cursorValue());
    }

    private BigInteger parseCursorValue(String cursorValue) {
        try {
            return new BigInteger(cursorValue);
        } catch (NumberFormatException ex) {
            log.warn("wallet.listener.cursor.invalid chain={} cursorValue={} rpcUrl={}",
                    chainType,
                    cursorValue,
                    chainProperties.getRpcUrl(),
                    ex);
            return ZERO;
        }
    }

    private boolean isBootstrapBaselineRequired() {
        return Objects.equals(lastProcessedBlockNumber, parseCursorValue(chainProperties.getInitialCursor()));
    }

    private BigInteger resolveScanStartBlock(BigInteger latestBlockNumber) {
        // 每轮都回扫一个确认窗口，确保未最终确认的原始交易能随着链头推进重新进入应用层，
        // 从而把 `DETECTED/CONFIRMING` 平稳推进到 `CONFIRMED`。
        // 若链头保持不变或回退，也继续按当前 canonical window 回扫，
        // 这样已确认交易在链重组后从窗口中消失时，listener 才能识别 reversal 候选。
        BigInteger confirmationWindow = BigInteger.valueOf(Math.max(1, chainProperties.getRequiredConfirmations()));
        BigInteger referenceHead = latestBlockNumber.min(lastProcessedBlockNumber);
        BigInteger rescanStart = referenceHead.subtract(confirmationWindow).add(BigInteger.TWO);
        if (rescanStart.compareTo(ONE) < 0) {
            return ONE;
        }
        if (rescanStart.compareTo(latestBlockNumber) > 0) {
            return latestBlockNumber;
        }
        return rescanStart;
    }

    private void processBlock(BigInteger blockNumber,
                              BigInteger latestBlockNumber,
                              Set<String> assignedAddressSnapshot,
                              Set<String> observedTransactionHashes) throws IOException {
        EthBlock.Block block = web3j.ethGetBlockByNumber(new DefaultBlockParameterNumber(blockNumber), true)
                .send()
                .getBlock();
        if (block == null) {
            throw new IOException("Missing block payload for " + blockNumber);
        }
        for (EthBlock.TransactionResult<?> transactionResult : block.getTransactions()) {
            Object payload = transactionResult.get();
            if (payload instanceof EthBlock.TransactionObject transactionObject) {
                processTransaction(transactionObject, latestBlockNumber, assignedAddressSnapshot, observedTransactionHashes);
            }
        }
    }

    private void processTransaction(EthBlock.TransactionObject transactionObject,
                                    BigInteger latestBlockNumber,
                                    Set<String> assignedAddressSnapshot,
                                    Set<String> observedTransactionHashes) throws IOException {
        if (transactionObject.getHash() != null && !transactionObject.getHash().isBlank()) {
            observedTransactionHashes.add(normalizeHash(transactionObject.getHash()));
        }
        String toAddress = transactionObject.getTo();
        if (toAddress == null || toAddress.isBlank()) {
            return;
        }
        if (transactionObject.getValue() == null || transactionObject.getValue().signum() <= 0) {
            return;
        }

        if (!assignedAddressSnapshot.contains(normalizeAddress(toAddress))) {
            return;
        }

        EthGetTransactionReceipt receiptResponse = web3j.ethGetTransactionReceipt(transactionObject.getHash()).send();
        Optional<TransactionReceipt> receipt = receiptResponse.getTransactionReceipt();
        if (receipt.isEmpty() || !receipt.get().isStatusOK()) {
            log.debug("wallet.listener.deposit.ignored chain={} txHash={} reason=receipt_not_ready_or_failed",
                    chainType,
                    transactionObject.getHash());
            return;
        }

        BigInteger transactionBlockNumber = transactionObject.getBlockNumber();
        int confirmations = latestBlockNumber.subtract(transactionBlockNumber).intValueExact() + 1;
        ObservedDepositTransaction observedDeposit = new ObservedDepositTransaction(
                chainType,
                resolveNativeTokenSymbol(),
                null,
                transactionObject.getHash(),
                0,
                transactionObject.getFrom(),
                toAddress,
                toDecimalAmount(transactionObject.getValue()),
                transactionBlockNumber.longValueExact(),
                confirmations,
                OffsetDateTime.now(ZoneOffset.UTC),
                false
        );

        // 监听层只负责识别链事实并做最小字段转换。
        // 去重、状态迁移、落库与低频事件发布仍由应用层串行编排，避免 listener 再维护第二套幂等逻辑。
        depositConsumer.accept(observedDeposit);
        log.info("wallet.listener.deposit.detected chain={} txHash={} toAddress={} confirmations={} amount={}",
                chainType,
                observedDeposit.txHash(),
                observedDeposit.toAddress(),
                observedDeposit.confirmations(),
                observedDeposit.amount());
    }

    private void reconcileMissingConfirmedTransactions(List<WalletDepositTransaction> trackedTransactionsInWindow,
                                                       Set<String> observedTransactionHashes) {
        // 当前对账只把“已 CONFIRMED 且在当前 canonical block window 中彻底消失”的记录视为 reversal 候选。
        // 未确认阶段的交易即使暂时消失，也保持既有状态等待后续链事实，避免 listener 提前做业务回滚判断。
        for (WalletDepositTransaction trackedTransaction : trackedTransactionsInWindow) {
            if (trackedTransaction.status() != WalletDepositStatus.CONFIRMED) {
                continue;
            }
            if (observedTransactionHashes.contains(normalizeHash(trackedTransaction.txHash()))) {
                continue;
            }
            ObservedDepositTransaction reversedObservation = new ObservedDepositTransaction(
                    trackedTransaction.chain(),
                    trackedTransaction.token(),
                    trackedTransaction.tokenContractAddress(),
                    trackedTransaction.txHash(),
                    trackedTransaction.logIndex(),
                    trackedTransaction.fromAddress(),
                    trackedTransaction.toAddress(),
                    trackedTransaction.amount(),
                    trackedTransaction.blockNumber(),
                    0,
                    OffsetDateTime.now(ZoneOffset.UTC),
                    true
            );
            depositConsumer.accept(reversedObservation);
            log.warn("wallet.listener.deposit.reversedDetected chain={} txHash={} blockNumber={} rpcUrl={}",
                    chainType,
                    trackedTransaction.txHash(),
                    trackedTransaction.blockNumber(),
                    chainProperties.getRpcUrl());
        }
    }

    private BigDecimal toDecimalAmount(BigInteger rawValue) {
        return new BigDecimal(rawValue).divide(EVM_DECIMAL_FACTOR, 8, RoundingMode.DOWN);
    }

    private String resolveNativeTokenSymbol() {
        return switch (chainType) {
            case ETH -> "ETH";
            case BSC -> "BNB";
            default -> chainType.name();
        };
    }

    private String normalizeAddress(String address) {
        return address == null ? null : address.toLowerCase(Locale.ROOT);
    }

    private String normalizeHash(String txHash) {
        return txHash == null ? null : txHash.toLowerCase(Locale.ROOT);
    }

    private void persistProcessedCursor(BigInteger processedBlockNumber) {
        walletChainCursorRepository.updateCursor(chainType, processedBlockNumber.toString());
        lastProcessedBlockNumber = processedBlockNumber;
    }

    @Override
    public void destroy() {
        if (pollingExecutor != null) {
            pollingExecutor.shutdownNow();
        }
        if (web3j != null) {
            web3j.shutdown();
        }
    }
}
