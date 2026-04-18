package com.falconx.wallet.listener;

import com.falconx.domain.enums.ChainType;
import com.falconx.wallet.client.WalletBlockchainClientFactory;
import com.falconx.wallet.config.WalletServiceProperties;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.tron.trident.core.ApiWrapper;

/**
 * 基于 Trident 的 Tron 链监听器骨架。
 *
 * <p>该监听器已经切换为真实 Tron SDK 客户端入口，
 * 后续只需要在此基础上补充扫块、TRC20 解析与回调应用层逻辑。
 */
public class TronApiChainDepositListener implements ChainDepositListener, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(TronApiChainDepositListener.class);

    private final WalletServiceProperties.Chain chainProperties;
    private final WalletBlockchainClientFactory walletBlockchainClientFactory;

    private ApiWrapper apiWrapper;

    public TronApiChainDepositListener(WalletServiceProperties.Chain chainProperties,
                                       WalletBlockchainClientFactory walletBlockchainClientFactory) {
        this.chainProperties = chainProperties;
        this.walletBlockchainClientFactory = walletBlockchainClientFactory;
    }

    @Override
    public ChainType chainType() {
        return ChainType.TRON;
    }

    @Override
    public void start(Consumer<ObservedDepositTransaction> depositConsumer) {
        if (apiWrapper == null) {
            apiWrapper = walletBlockchainClientFactory.createTronClient(chainProperties);
        }
        log.info("wallet.listener.started chain={} client=trident grpcTarget={} solidityTarget={} requiredConfirmations={} scanInterval={}",
                ChainType.TRON,
                chainProperties.getRpcUrl(),
                chainProperties.getSolidityRpcUrl(),
                chainProperties.getRequiredConfirmations(),
                chainProperties.getScanInterval());
    }

    @Override
    public void destroy() {
        if (apiWrapper != null) {
            apiWrapper.close();
        }
    }
}
