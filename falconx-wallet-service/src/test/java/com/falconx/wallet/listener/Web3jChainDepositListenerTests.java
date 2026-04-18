package com.falconx.wallet.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthBlockNumber;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.utils.Numeric;

/**
 * {@link Web3jChainDepositListener} 测试。
 *
 * <p>当前只验证 Stage 6A 新补的最小真实链路：
 *
 * <ul>
 *   <li>首次启动若游标仍为初始值，只建立链头基线而不误扫全历史</li>
 *   <li>命中平台地址的原生币转账会被转成 `ObservedDepositTransaction` 并交给应用层</li>
 *   <li>链节点短时异常只记录告警，不把启动流程升级成失败</li>
 * </ul>
 */
class Web3jChainDepositListenerTests {

    @Test
    void shouldPrepareBootstrapBaselineWhenCursorStillInitialValue() throws Exception {
        WalletBlockchainClientFactory walletBlockchainClientFactory = Mockito.mock(WalletBlockchainClientFactory.class);
        WalletAddressRepository walletAddressRepository = Mockito.mock(WalletAddressRepository.class);
        WalletChainCursorRepository walletChainCursorRepository = Mockito.mock(WalletChainCursorRepository.class);
        WalletDepositTransactionRepository walletDepositTransactionRepository = Mockito.mock(WalletDepositTransactionRepository.class);
        Web3j web3j = Mockito.mock(Web3j.class);
        @SuppressWarnings("unchecked")
        Request<?, EthBlockNumber> blockNumberRequest = Mockito.mock(Request.class);
        EthBlockNumber response = new EthBlockNumber();
        response.setResult("0x10");
        when(walletBlockchainClientFactory.createEvmClient(URI.create("https://rpc.example.org"))).thenReturn(web3j);
        when(walletChainCursorRepository.findByChain(ChainType.ETH)).thenReturn(new WalletChainCursor(
                1L,
                ChainType.ETH,
                "block",
                "0",
                OffsetDateTime.now()
        ));
        doReturn(blockNumberRequest).when(web3j).ethBlockNumber();
        when(blockNumberRequest.send()).thenReturn(response);

        @SuppressWarnings("unchecked")
        Consumer<ObservedDepositTransaction> depositConsumer = Mockito.mock(Consumer.class);
        Web3jChainDepositListener listener = createListener(
                URI.create("https://rpc.example.org"),
                walletBlockchainClientFactory,
                walletAddressRepository,
                walletChainCursorRepository,
                walletDepositTransactionRepository,
                Duration.ofDays(1),
                12,
                "0"
        );
        try {
            listener.start(depositConsumer);
            verify(walletChainCursorRepository).updateCursor(ChainType.ETH, "16");
            verify(depositConsumer, never()).accept(any());
        } finally {
            listener.destroy();
        }
    }

    @Test
    void shouldObserveNativeTransferForAssignedPlatformAddress() throws Exception {
        WalletBlockchainClientFactory walletBlockchainClientFactory = Mockito.mock(WalletBlockchainClientFactory.class);
        WalletAddressRepository walletAddressRepository = Mockito.mock(WalletAddressRepository.class);
        WalletChainCursorRepository walletChainCursorRepository = Mockito.mock(WalletChainCursorRepository.class);
        WalletDepositTransactionRepository walletDepositTransactionRepository = Mockito.mock(WalletDepositTransactionRepository.class);
        Web3j web3j = Mockito.mock(Web3j.class);
        @SuppressWarnings("unchecked")
        Request<?, EthBlockNumber> blockNumberRequest = Mockito.mock(Request.class);
        @SuppressWarnings("unchecked")
        Request<?, EthBlock> blockRequest = Mockito.mock(Request.class);
        @SuppressWarnings("unchecked")
        Request<?, EthGetTransactionReceipt> receiptRequest = Mockito.mock(Request.class);

        EthBlockNumber blockNumberResponse = new EthBlockNumber();
        blockNumberResponse.setResult("0x10");
        EthBlock ethBlock = Mockito.mock(EthBlock.class);
        EthBlock.Block block = Mockito.mock(EthBlock.Block.class);
        EthBlock.TransactionObject transactionObject = Mockito.mock(EthBlock.TransactionObject.class);
        EthGetTransactionReceipt receiptResponse = Mockito.mock(EthGetTransactionReceipt.class);
        TransactionReceipt transactionReceipt = Mockito.mock(TransactionReceipt.class);

        when(walletBlockchainClientFactory.createEvmClient(URI.create("https://rpc.example.org"))).thenReturn(web3j);
        when(walletChainCursorRepository.findByChain(ChainType.ETH)).thenReturn(new WalletChainCursor(
                1L,
                ChainType.ETH,
                "block",
                "15",
                OffsetDateTime.now()
        ));
        doReturn(blockNumberRequest).when(web3j).ethBlockNumber();
        when(blockNumberRequest.send()).thenReturn(blockNumberResponse);
        doReturn(blockRequest).when(web3j).ethGetBlockByNumber(any(), eq(true));
        when(blockRequest.send()).thenReturn(ethBlock);
        when(ethBlock.getBlock()).thenReturn(block);
        when(block.getTransactions()).thenReturn(List.of((EthBlock.TransactionResult<?>) () -> transactionObject));
        when(transactionObject.getTo()).thenReturn("0xplatform");
        when(transactionObject.getFrom()).thenReturn("0xsource");
        when(transactionObject.getHash()).thenReturn("0xabc");
        when(transactionObject.getValue()).thenReturn(new java.math.BigInteger("1000000000000000000"));
        when(transactionObject.getBlockNumber()).thenReturn(java.math.BigInteger.valueOf(16));
        when(walletAddressRepository.findAssignedAddressesByChain(ChainType.ETH)).thenReturn(Set.of("0xplatform"));
        doReturn(receiptRequest).when(web3j).ethGetTransactionReceipt("0xabc");
        when(receiptRequest.send()).thenReturn(receiptResponse);
        when(receiptResponse.getTransactionReceipt()).thenReturn(Optional.of(transactionReceipt));
        when(transactionReceipt.isStatusOK()).thenReturn(true);
        when(walletDepositTransactionRepository.findByChainAndBlockRange(ChainType.ETH, 16L, 16L)).thenReturn(List.of());

        @SuppressWarnings("unchecked")
        Consumer<ObservedDepositTransaction> depositConsumer = Mockito.mock(Consumer.class);
        Web3jChainDepositListener listener = createListener(
                URI.create("https://rpc.example.org"),
                walletBlockchainClientFactory,
                walletAddressRepository,
                walletChainCursorRepository,
                walletDepositTransactionRepository,
                Duration.ofDays(1),
                1,
                "0"
        );
        try {
            listener.start(depositConsumer);

            ArgumentCaptor<ObservedDepositTransaction> observedCaptor = ArgumentCaptor.forClass(ObservedDepositTransaction.class);
            verify(depositConsumer).accept(observedCaptor.capture());
            verify(walletChainCursorRepository).updateCursor(ChainType.ETH, "16");

            ObservedDepositTransaction observedDeposit = observedCaptor.getValue();
            org.junit.jupiter.api.Assertions.assertEquals(ChainType.ETH, observedDeposit.chain());
            org.junit.jupiter.api.Assertions.assertEquals("ETH", observedDeposit.token());
            org.junit.jupiter.api.Assertions.assertEquals("0xabc", observedDeposit.txHash());
            org.junit.jupiter.api.Assertions.assertEquals("0xplatform", observedDeposit.toAddress());
            org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("1.00000000"), observedDeposit.amount());
            org.junit.jupiter.api.Assertions.assertEquals(1, observedDeposit.confirmations());
            verify(walletAddressRepository).findAssignedAddressesByChain(ChainType.ETH);
        } finally {
            listener.destroy();
        }
    }

    @Test
    void shouldKeepServiceAliveWhenChainHeadSyncFails() throws Exception {
        WalletBlockchainClientFactory walletBlockchainClientFactory = Mockito.mock(WalletBlockchainClientFactory.class);
        WalletAddressRepository walletAddressRepository = Mockito.mock(WalletAddressRepository.class);
        WalletChainCursorRepository walletChainCursorRepository = Mockito.mock(WalletChainCursorRepository.class);
        WalletDepositTransactionRepository walletDepositTransactionRepository = Mockito.mock(WalletDepositTransactionRepository.class);
        Web3j web3j = Mockito.mock(Web3j.class);
        @SuppressWarnings("unchecked")
        Request<?, EthBlockNumber> request = Mockito.mock(Request.class);
        when(walletBlockchainClientFactory.createEvmClient(URI.create("wss://eth-sepolia.example.org"))).thenReturn(web3j);
        when(walletChainCursorRepository.findByChain(ChainType.ETH)).thenReturn(new WalletChainCursor(
                1L,
                ChainType.ETH,
                "block",
                "15",
                OffsetDateTime.now()
        ));
        doReturn(request).when(web3j).ethBlockNumber();
        when(request.send()).thenThrow(new IOException("rpc offline"));

        @SuppressWarnings("unchecked")
        Consumer<ObservedDepositTransaction> depositConsumer = Mockito.mock(Consumer.class);
        Web3jChainDepositListener listener = createListener(
                URI.create("wss://eth-sepolia.example.org"),
                walletBlockchainClientFactory,
                walletAddressRepository,
                walletChainCursorRepository,
                walletDepositTransactionRepository,
                Duration.ofDays(1),
                12,
                "0"
        );
        try {
            listener.start(depositConsumer);
            verify(walletChainCursorRepository, never()).updateCursor(eq(ChainType.ETH), anyString());
            verify(depositConsumer, never()).accept(any());
        } finally {
            listener.destroy();
        }
    }

    @Test
    void shouldEmitReversedObservationWhenConfirmedTrackedTransactionDisappearsFromRescanWindow() throws Exception {
        WalletBlockchainClientFactory walletBlockchainClientFactory = Mockito.mock(WalletBlockchainClientFactory.class);
        WalletAddressRepository walletAddressRepository = Mockito.mock(WalletAddressRepository.class);
        WalletChainCursorRepository walletChainCursorRepository = Mockito.mock(WalletChainCursorRepository.class);
        WalletDepositTransactionRepository walletDepositTransactionRepository = Mockito.mock(WalletDepositTransactionRepository.class);
        Web3j web3j = Mockito.mock(Web3j.class);
        @SuppressWarnings("unchecked")
        Request<?, EthBlockNumber> blockNumberRequest = Mockito.mock(Request.class);
        @SuppressWarnings("unchecked")
        Request<?, EthBlock> blockRequest = Mockito.mock(Request.class);

        EthBlockNumber blockNumberResponse = new EthBlockNumber();
        blockNumberResponse.setResult("0x10");
        EthBlock ethBlock = Mockito.mock(EthBlock.class);
        EthBlock.Block block = Mockito.mock(EthBlock.Block.class);

        when(walletBlockchainClientFactory.createEvmClient(URI.create("https://rpc.example.org"))).thenReturn(web3j);
        when(walletChainCursorRepository.findByChain(ChainType.ETH)).thenReturn(new WalletChainCursor(
                1L,
                ChainType.ETH,
                "block",
                "16",
                OffsetDateTime.now()
        ));
        when(walletDepositTransactionRepository.findByChainAndBlockRange(ChainType.ETH, 16L, 16L)).thenReturn(List.of(
                new WalletDepositTransaction(
                        100L,
                        200L,
                        ChainType.ETH,
                        "ETH",
                        null,
                        "0xdeadbeef",
                        0,
                        "0xsource",
                        "0xplatform",
                        new BigDecimal("1.00000000"),
                        16L,
                        12,
                        12,
                        WalletDepositStatus.CONFIRMED,
                        OffsetDateTime.now().minusMinutes(5),
                        OffsetDateTime.now().minusMinutes(4),
                        OffsetDateTime.now().minusMinutes(1)
                )
        ));
        when(walletAddressRepository.findAssignedAddressesByChain(ChainType.ETH)).thenReturn(Set.of("0xplatform"));
        doReturn(blockNumberRequest).when(web3j).ethBlockNumber();
        when(blockNumberRequest.send()).thenReturn(blockNumberResponse);
        doReturn(blockRequest).when(web3j).ethGetBlockByNumber(any(), eq(true));
        when(blockRequest.send()).thenReturn(ethBlock);
        when(ethBlock.getBlock()).thenReturn(block);
        when(block.getTransactions()).thenReturn(List.of());

        @SuppressWarnings("unchecked")
        Consumer<ObservedDepositTransaction> depositConsumer = Mockito.mock(Consumer.class);
        Web3jChainDepositListener listener = createListener(
                URI.create("https://rpc.example.org"),
                walletBlockchainClientFactory,
                walletAddressRepository,
                walletChainCursorRepository,
                walletDepositTransactionRepository,
                Duration.ofDays(1),
                1,
                "0"
        );
        try {
            listener.start(depositConsumer);

            ArgumentCaptor<ObservedDepositTransaction> observedCaptor = ArgumentCaptor.forClass(ObservedDepositTransaction.class);
            verify(depositConsumer).accept(observedCaptor.capture());
            ObservedDepositTransaction observedDeposit = observedCaptor.getValue();
            org.junit.jupiter.api.Assertions.assertEquals("0xdeadbeef", observedDeposit.txHash());
            org.junit.jupiter.api.Assertions.assertTrue(observedDeposit.reversed());
            org.junit.jupiter.api.Assertions.assertEquals(0, observedDeposit.confirmations());
            verify(walletChainCursorRepository).updateCursor(ChainType.ETH, "16");
        } finally {
            listener.destroy();
        }
    }

    @Test
    void shouldObserveErc20TransferForAssignedPlatformAddress() throws Exception {
        String platformAddress = "0x00000000000000000000000000000000a11ce001";
        String sourceAddress = "0x00000000000000000000000000000000b0b00001";
        String tokenContractAddress = "0x00000000000000000000000000000000c0ffee01";
        WalletBlockchainClientFactory walletBlockchainClientFactory = Mockito.mock(WalletBlockchainClientFactory.class);
        WalletAddressRepository walletAddressRepository = Mockito.mock(WalletAddressRepository.class);
        WalletChainCursorRepository walletChainCursorRepository = Mockito.mock(WalletChainCursorRepository.class);
        WalletDepositTransactionRepository walletDepositTransactionRepository = Mockito.mock(WalletDepositTransactionRepository.class);
        Web3j web3j = Mockito.mock(Web3j.class);
        @SuppressWarnings("unchecked")
        Request<?, EthBlockNumber> blockNumberRequest = Mockito.mock(Request.class);
        @SuppressWarnings("unchecked")
        Request<?, EthBlock> blockRequest = Mockito.mock(Request.class);
        @SuppressWarnings("unchecked")
        Request<?, EthGetTransactionReceipt> receiptRequest = Mockito.mock(Request.class);
        @SuppressWarnings("unchecked")
        Request<?, EthCall> decimalsRequest = Mockito.mock(Request.class);
        @SuppressWarnings("unchecked")
        Request<?, EthCall> symbolRequest = Mockito.mock(Request.class);

        EthBlockNumber blockNumberResponse = new EthBlockNumber();
        blockNumberResponse.setResult("0x10");
        EthBlock ethBlock = Mockito.mock(EthBlock.class);
        EthBlock.Block block = Mockito.mock(EthBlock.Block.class);
        EthBlock.TransactionObject transactionObject = Mockito.mock(EthBlock.TransactionObject.class);
        EthGetTransactionReceipt receiptResponse = Mockito.mock(EthGetTransactionReceipt.class);
        TransactionReceipt transactionReceipt = Mockito.mock(TransactionReceipt.class);
        EthCall decimalsResponse = new EthCall();
        decimalsResponse.setResult("0x0000000000000000000000000000000000000000000000000000000000000006");
        EthCall symbolResponse = new EthCall();
        symbolResponse.setResult("0x5553445400000000000000000000000000000000000000000000000000000000");

        when(walletBlockchainClientFactory.createEvmClient(URI.create("https://rpc.example.org"))).thenReturn(web3j);
        when(walletChainCursorRepository.findByChain(ChainType.ETH)).thenReturn(new WalletChainCursor(
                1L,
                ChainType.ETH,
                "block",
                "15",
                OffsetDateTime.now()
        ));
        doReturn(blockNumberRequest).when(web3j).ethBlockNumber();
        when(blockNumberRequest.send()).thenReturn(blockNumberResponse);
        doReturn(blockRequest).when(web3j).ethGetBlockByNumber(any(), eq(true));
        when(blockRequest.send()).thenReturn(ethBlock);
        when(ethBlock.getBlock()).thenReturn(block);
        when(block.getTransactions()).thenReturn(List.of((EthBlock.TransactionResult<?>) () -> transactionObject));
        when(transactionObject.getHash()).thenReturn("0xerc20tx");
        when(transactionObject.getTo()).thenReturn(tokenContractAddress);
        when(transactionObject.getFrom()).thenReturn("0x00000000000000000000000000000000decaf001");
        when(transactionObject.getInput()).thenReturn("0xa9059cbb");
        when(transactionObject.getValue()).thenReturn(BigInteger.ZERO);
        when(transactionObject.getBlockNumber()).thenReturn(BigInteger.valueOf(16));
        when(walletAddressRepository.findAssignedAddressesByChain(ChainType.ETH)).thenReturn(Set.of(platformAddress));
        doReturn(receiptRequest).when(web3j).ethGetTransactionReceipt("0xerc20tx");
        when(receiptRequest.send()).thenReturn(receiptResponse);
        when(receiptResponse.getTransactionReceipt()).thenReturn(Optional.of(transactionReceipt));
        when(transactionReceipt.isStatusOK()).thenReturn(true);
        when(transactionReceipt.getLogs()).thenReturn(List.of(erc20TransferLog(
                "0xerc20tx",
                tokenContractAddress,
                sourceAddress,
                platformAddress,
                7,
                new BigInteger("1500000")
        )));
        doReturn(decimalsRequest, symbolRequest).when(web3j).ethCall(any(), eq(DefaultBlockParameterName.LATEST));
        when(decimalsRequest.send()).thenReturn(decimalsResponse);
        when(symbolRequest.send()).thenReturn(symbolResponse);
        when(walletDepositTransactionRepository.findByChainAndBlockRange(ChainType.ETH, 16L, 16L)).thenReturn(List.of());

        @SuppressWarnings("unchecked")
        Consumer<ObservedDepositTransaction> depositConsumer = Mockito.mock(Consumer.class);
        Web3jChainDepositListener listener = createListener(
                URI.create("https://rpc.example.org"),
                walletBlockchainClientFactory,
                walletAddressRepository,
                walletChainCursorRepository,
                walletDepositTransactionRepository,
                Duration.ofDays(1),
                1,
                "0"
        );
        try {
            listener.start(depositConsumer);

            ArgumentCaptor<ObservedDepositTransaction> observedCaptor = ArgumentCaptor.forClass(ObservedDepositTransaction.class);
            verify(depositConsumer).accept(observedCaptor.capture());
            ObservedDepositTransaction observedDeposit = observedCaptor.getValue();

            org.junit.jupiter.api.Assertions.assertEquals("USDT", observedDeposit.token());
            org.junit.jupiter.api.Assertions.assertEquals(tokenContractAddress, observedDeposit.tokenContractAddress());
            org.junit.jupiter.api.Assertions.assertEquals(7, observedDeposit.logIndex());
            org.junit.jupiter.api.Assertions.assertEquals(sourceAddress, observedDeposit.fromAddress());
            org.junit.jupiter.api.Assertions.assertEquals(platformAddress, observedDeposit.toAddress());
            org.junit.jupiter.api.Assertions.assertEquals(new BigDecimal("1.50000000"), observedDeposit.amount());
            org.junit.jupiter.api.Assertions.assertEquals(1, observedDeposit.confirmations());
            verify(walletChainCursorRepository).updateCursor(ChainType.ETH, "16");
        } finally {
            listener.destroy();
        }
    }

    @Test
    void shouldReverseTrackedErc20LogWhenSameTransactionStillExistsButLogIndexDisappears() throws Exception {
        String platformAddress = "0x00000000000000000000000000000000a11ce001";
        String sourceAddress = "0x00000000000000000000000000000000b0b00001";
        String tokenContractAddress = "0x00000000000000000000000000000000c0ffee01";
        String otherAddress = "0x00000000000000000000000000000000d15ea5e1";
        WalletBlockchainClientFactory walletBlockchainClientFactory = Mockito.mock(WalletBlockchainClientFactory.class);
        WalletAddressRepository walletAddressRepository = Mockito.mock(WalletAddressRepository.class);
        WalletChainCursorRepository walletChainCursorRepository = Mockito.mock(WalletChainCursorRepository.class);
        WalletDepositTransactionRepository walletDepositTransactionRepository = Mockito.mock(WalletDepositTransactionRepository.class);
        Web3j web3j = Mockito.mock(Web3j.class);
        @SuppressWarnings("unchecked")
        Request<?, EthBlockNumber> blockNumberRequest = Mockito.mock(Request.class);
        @SuppressWarnings("unchecked")
        Request<?, EthBlock> blockRequest = Mockito.mock(Request.class);
        @SuppressWarnings("unchecked")
        Request<?, EthGetTransactionReceipt> receiptRequest = Mockito.mock(Request.class);

        EthBlockNumber blockNumberResponse = new EthBlockNumber();
        blockNumberResponse.setResult("0x10");
        EthBlock ethBlock = Mockito.mock(EthBlock.class);
        EthBlock.Block block = Mockito.mock(EthBlock.Block.class);
        EthBlock.TransactionObject transactionObject = Mockito.mock(EthBlock.TransactionObject.class);
        EthGetTransactionReceipt receiptResponse = Mockito.mock(EthGetTransactionReceipt.class);
        TransactionReceipt transactionReceipt = Mockito.mock(TransactionReceipt.class);

        when(walletBlockchainClientFactory.createEvmClient(URI.create("https://rpc.example.org"))).thenReturn(web3j);
        when(walletChainCursorRepository.findByChain(ChainType.ETH)).thenReturn(new WalletChainCursor(
                1L,
                ChainType.ETH,
                "block",
                "16",
                OffsetDateTime.now()
        ));
        when(walletDepositTransactionRepository.findByChainAndBlockRange(ChainType.ETH, 16L, 16L)).thenReturn(List.of(
                new WalletDepositTransaction(
                        100L,
                        200L,
                        ChainType.ETH,
                        "USDT",
                        tokenContractAddress,
                        "0xmultilog",
                        7,
                        sourceAddress,
                        platformAddress,
                        new BigDecimal("1.00000000"),
                        16L,
                        12,
                        12,
                        WalletDepositStatus.CONFIRMED,
                        OffsetDateTime.now().minusMinutes(5),
                        OffsetDateTime.now().minusMinutes(4),
                        OffsetDateTime.now().minusMinutes(1)
                )
        ));
        when(walletAddressRepository.findAssignedAddressesByChain(ChainType.ETH)).thenReturn(Set.of(platformAddress));
        doReturn(blockNumberRequest).when(web3j).ethBlockNumber();
        when(blockNumberRequest.send()).thenReturn(blockNumberResponse);
        doReturn(blockRequest).when(web3j).ethGetBlockByNumber(any(), eq(true));
        when(blockRequest.send()).thenReturn(ethBlock);
        when(ethBlock.getBlock()).thenReturn(block);
        when(block.getTransactions()).thenReturn(List.of((EthBlock.TransactionResult<?>) () -> transactionObject));
        when(transactionObject.getHash()).thenReturn("0xmultilog");
        when(transactionObject.getTo()).thenReturn(tokenContractAddress);
        when(transactionObject.getInput()).thenReturn("0xa9059cbb");
        when(transactionObject.getValue()).thenReturn(BigInteger.ZERO);
        when(transactionObject.getBlockNumber()).thenReturn(BigInteger.valueOf(16));
        doReturn(receiptRequest).when(web3j).ethGetTransactionReceipt("0xmultilog");
        when(receiptRequest.send()).thenReturn(receiptResponse);
        when(receiptResponse.getTransactionReceipt()).thenReturn(Optional.of(transactionReceipt));
        when(transactionReceipt.isStatusOK()).thenReturn(true);
        when(transactionReceipt.getLogs()).thenReturn(List.of(erc20TransferLog(
                "0xmultilog",
                tokenContractAddress,
                sourceAddress,
                otherAddress,
                8,
                BigInteger.ONE
        )));

        @SuppressWarnings("unchecked")
        Consumer<ObservedDepositTransaction> depositConsumer = Mockito.mock(Consumer.class);
        Web3jChainDepositListener listener = createListener(
                URI.create("https://rpc.example.org"),
                walletBlockchainClientFactory,
                walletAddressRepository,
                walletChainCursorRepository,
                walletDepositTransactionRepository,
                Duration.ofDays(1),
                1,
                "0"
        );
        try {
            listener.start(depositConsumer);

            ArgumentCaptor<ObservedDepositTransaction> observedCaptor = ArgumentCaptor.forClass(ObservedDepositTransaction.class);
            verify(depositConsumer).accept(observedCaptor.capture());
            ObservedDepositTransaction observedDeposit = observedCaptor.getValue();

            org.junit.jupiter.api.Assertions.assertTrue(observedDeposit.reversed());
            org.junit.jupiter.api.Assertions.assertEquals("0xmultilog", observedDeposit.txHash());
            org.junit.jupiter.api.Assertions.assertEquals(7, observedDeposit.logIndex());
            verify(walletChainCursorRepository).updateCursor(ChainType.ETH, "16");
        } finally {
            listener.destroy();
        }
    }

    @Test
    void shouldSkipSingleErc20MetadataFailureWithoutBreakingWholeSync() throws Exception {
        String platformAddress = "0x00000000000000000000000000000000a11ce001";
        String sourceAddress = "0x00000000000000000000000000000000b0b00001";
        String badTokenAddress = "0x00000000000000000000000000000000bad70001";
        WalletBlockchainClientFactory walletBlockchainClientFactory = Mockito.mock(WalletBlockchainClientFactory.class);
        WalletAddressRepository walletAddressRepository = Mockito.mock(WalletAddressRepository.class);
        WalletChainCursorRepository walletChainCursorRepository = Mockito.mock(WalletChainCursorRepository.class);
        WalletDepositTransactionRepository walletDepositTransactionRepository = Mockito.mock(WalletDepositTransactionRepository.class);
        Web3j web3j = Mockito.mock(Web3j.class);
        @SuppressWarnings("unchecked")
        Request<?, EthBlockNumber> blockNumberRequest = Mockito.mock(Request.class);
        @SuppressWarnings("unchecked")
        Request<?, EthBlock> blockRequest = Mockito.mock(Request.class);
        @SuppressWarnings("unchecked")
        Request<?, EthGetTransactionReceipt> badReceiptRequest = Mockito.mock(Request.class);
        @SuppressWarnings("unchecked")
        Request<?, EthGetTransactionReceipt> nativeReceiptRequest = Mockito.mock(Request.class);
        @SuppressWarnings("unchecked")
        Request<?, EthCall> decimalsRequest = Mockito.mock(Request.class);

        EthBlockNumber blockNumberResponse = new EthBlockNumber();
        blockNumberResponse.setResult("0x10");
        EthBlock ethBlock = Mockito.mock(EthBlock.class);
        EthBlock.Block block = Mockito.mock(EthBlock.Block.class);
        EthBlock.TransactionObject erc20Transaction = Mockito.mock(EthBlock.TransactionObject.class);
        EthBlock.TransactionObject nativeTransaction = Mockito.mock(EthBlock.TransactionObject.class);
        EthGetTransactionReceipt badReceiptResponse = Mockito.mock(EthGetTransactionReceipt.class);
        EthGetTransactionReceipt nativeReceiptResponse = Mockito.mock(EthGetTransactionReceipt.class);
        TransactionReceipt badTransactionReceipt = Mockito.mock(TransactionReceipt.class);
        TransactionReceipt nativeTransactionReceipt = Mockito.mock(TransactionReceipt.class);

        when(walletBlockchainClientFactory.createEvmClient(URI.create("https://rpc.example.org"))).thenReturn(web3j);
        when(walletChainCursorRepository.findByChain(ChainType.ETH)).thenReturn(new WalletChainCursor(
                1L,
                ChainType.ETH,
                "block",
                "15",
                OffsetDateTime.now()
        ));
        when(walletAddressRepository.findAssignedAddressesByChain(ChainType.ETH)).thenReturn(Set.of(platformAddress));
        when(walletDepositTransactionRepository.findByChainAndBlockRange(ChainType.ETH, 16L, 16L)).thenReturn(List.of());
        doReturn(blockNumberRequest).when(web3j).ethBlockNumber();
        when(blockNumberRequest.send()).thenReturn(blockNumberResponse);
        doReturn(blockRequest).when(web3j).ethGetBlockByNumber(any(), eq(true));
        when(blockRequest.send()).thenReturn(ethBlock);
        when(ethBlock.getBlock()).thenReturn(block);
        when(block.getTransactions()).thenReturn(List.of(
                (EthBlock.TransactionResult<?>) () -> erc20Transaction,
                (EthBlock.TransactionResult<?>) () -> nativeTransaction
        ));

        when(erc20Transaction.getHash()).thenReturn("0xbad-erc20");
        when(erc20Transaction.getTo()).thenReturn(badTokenAddress);
        when(erc20Transaction.getFrom()).thenReturn("0x00000000000000000000000000000000decaf001");
        when(erc20Transaction.getInput()).thenReturn("0xa9059cbb");
        when(erc20Transaction.getValue()).thenReturn(BigInteger.ZERO);
        when(erc20Transaction.getBlockNumber()).thenReturn(BigInteger.valueOf(16));

        when(nativeTransaction.getHash()).thenReturn("0xgood-native");
        when(nativeTransaction.getTo()).thenReturn(platformAddress);
        when(nativeTransaction.getFrom()).thenReturn(sourceAddress);
        when(nativeTransaction.getInput()).thenReturn("0x");
        when(nativeTransaction.getValue()).thenReturn(new BigInteger("1000000000000000000"));
        when(nativeTransaction.getBlockNumber()).thenReturn(BigInteger.valueOf(16));

        doReturn(badReceiptRequest).when(web3j).ethGetTransactionReceipt("0xbad-erc20");
        doReturn(nativeReceiptRequest).when(web3j).ethGetTransactionReceipt("0xgood-native");
        when(badReceiptRequest.send()).thenReturn(badReceiptResponse);
        when(nativeReceiptRequest.send()).thenReturn(nativeReceiptResponse);
        when(badReceiptResponse.getTransactionReceipt()).thenReturn(Optional.of(badTransactionReceipt));
        when(nativeReceiptResponse.getTransactionReceipt()).thenReturn(Optional.of(nativeTransactionReceipt));
        when(badTransactionReceipt.isStatusOK()).thenReturn(true);
        when(nativeTransactionReceipt.isStatusOK()).thenReturn(true);
        when(badTransactionReceipt.getLogs()).thenReturn(List.of(erc20TransferLog(
                "0xbad-erc20",
                badTokenAddress,
                sourceAddress,
                platformAddress,
                1,
                new BigInteger("1000000")
        )));
        when(nativeTransactionReceipt.getLogs()).thenReturn(List.of());

        doReturn(decimalsRequest).when(web3j).ethCall(any(), eq(DefaultBlockParameterName.LATEST));
        when(decimalsRequest.send()).thenThrow(new IOException("metadata rpc failed"));

        @SuppressWarnings("unchecked")
        Consumer<ObservedDepositTransaction> depositConsumer = Mockito.mock(Consumer.class);
        Web3jChainDepositListener listener = createListener(
                URI.create("https://rpc.example.org"),
                walletBlockchainClientFactory,
                walletAddressRepository,
                walletChainCursorRepository,
                walletDepositTransactionRepository,
                Duration.ofDays(1),
                1,
                "0"
        );
        try {
            listener.start(depositConsumer);

            ArgumentCaptor<ObservedDepositTransaction> observedCaptor = ArgumentCaptor.forClass(ObservedDepositTransaction.class);
            verify(depositConsumer).accept(observedCaptor.capture());
            ObservedDepositTransaction observedDeposit = observedCaptor.getValue();

            org.junit.jupiter.api.Assertions.assertEquals("0xgood-native", observedDeposit.txHash());
            org.junit.jupiter.api.Assertions.assertEquals("ETH", observedDeposit.token());
            verify(walletChainCursorRepository).updateCursor(ChainType.ETH, "16");
        } finally {
            listener.destroy();
        }
    }

    private Web3jChainDepositListener createListener(URI rpcUrl,
                                                     WalletBlockchainClientFactory walletBlockchainClientFactory,
                                                     WalletAddressRepository walletAddressRepository,
                                                     WalletChainCursorRepository walletChainCursorRepository,
                                                     WalletDepositTransactionRepository walletDepositTransactionRepository,
                                                     Duration scanInterval,
                                                     int requiredConfirmations,
                                                     String initialCursor) {
        return new Web3jChainDepositListener(
                ChainType.ETH,
                new WalletServiceProperties.Chain(
                        rpcUrl,
                        scanInterval,
                        requiredConfirmations,
                        "block",
                        initialCursor
                ),
                walletBlockchainClientFactory,
                walletAddressRepository,
                walletChainCursorRepository,
                walletDepositTransactionRepository
        );
    }

    private Log erc20TransferLog(String txHash,
                                 String contractAddress,
                                 String fromAddress,
                                 String toAddress,
                                 int logIndex,
                                 BigInteger rawAmount) {
        Log log = new Log();
        log.setTransactionHash(txHash);
        log.setAddress(contractAddress);
        log.setLogIndex(Numeric.toHexStringWithPrefix(BigInteger.valueOf(logIndex)));
        log.setData(Numeric.toHexStringWithPrefixSafe(rawAmount));
        log.setTopics(List.of(
                "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef",
                indexedAddressTopic(fromAddress),
                indexedAddressTopic(toAddress)
        ));
        return log;
    }

    private String indexedAddressTopic(String address) {
        return "0x000000000000000000000000" + address.substring(2).toLowerCase();
    }
}
