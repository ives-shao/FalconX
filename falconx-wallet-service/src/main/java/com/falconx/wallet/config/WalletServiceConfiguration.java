package com.falconx.wallet.config;

import com.falconx.domain.enums.ChainType;
import com.falconx.infrastructure.id.IdGenerator;
import com.falconx.wallet.client.WalletBlockchainClientFactory;
import com.falconx.wallet.listener.ChainDepositListener;
import com.falconx.wallet.listener.SolanaRpcChainDepositListener;
import com.falconx.wallet.listener.TronApiChainDepositListener;
import com.falconx.wallet.listener.Web3jChainDepositListener;
import com.falconx.wallet.producer.KafkaWalletEventPublisher;
import com.falconx.wallet.producer.OutboxBackedWalletEventPublisher;
import com.falconx.wallet.producer.WalletEventPublisher;
import com.falconx.wallet.producer.WalletOutboxEventPublisher;
import com.falconx.wallet.repository.WalletAddressRepository;
import com.falconx.wallet.repository.WalletChainCursorRepository;
import com.falconx.wallet.repository.WalletDepositTransactionRepository;
import com.falconx.wallet.repository.WalletOutboxRepository;
import com.falconx.wallet.service.WalletAddressAllocationService;
import com.falconx.wallet.service.WalletDepositStatusService;
import com.falconx.wallet.service.impl.DefaultWalletDepositStatusService;
import com.falconx.wallet.service.impl.SequentialWalletAddressAllocationService;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import java.util.List;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * wallet-service 基础配置入口。
 *
 * <p>当前阶段钱包 owner 数据已经切换到真实数据库仓储，
 * 事件发布和链监听入口也已切到真实 Kafka / 链 SDK 骨架。
 * 后续补全真实链轮询逻辑时，只允许继续扩展 listener / client 包，不应改变这里的装配边界。
 */
@Configuration
@EnableConfigurationProperties(WalletServiceProperties.class)
public class WalletServiceConfiguration {

    /**
     * 注册 wallet-service 自用的 Jackson `ObjectMapper`。
     *
     * <p>钱包服务内部的 Kafka 发布和链监听骨架使用 `com.fasterxml.jackson` 体系序列化
     * 事件 payload。这里显式统一 mapper，确保 Java Time 字段和运行时 Bean 装配一致。
     *
     * @return 统一 JSON 序列化器
     */
    @Bean
    ObjectMapper objectMapper() {
        return JsonMapper.builder()
                .findAndAddModules()
                .build();
    }

    @Bean
    WalletAddressAllocationService walletAddressAllocationService(WalletServiceProperties properties,
                                                                  WalletAddressRepository walletAddressRepository) {
        return new SequentialWalletAddressAllocationService(properties, walletAddressRepository);
    }

    @Bean
    WalletDepositStatusService walletDepositStatusService(WalletServiceProperties properties) {
        return new DefaultWalletDepositStatusService(properties);
    }

    @Bean
    WalletEventPublisher walletEventPublisher(WalletOutboxRepository walletOutboxRepository) {
        return new OutboxBackedWalletEventPublisher(walletOutboxRepository);
    }

    @Bean
    WalletOutboxEventPublisher walletOutboxEventPublisher(WalletServiceProperties properties,
                                                          KafkaTemplate<String, String> kafkaTemplate,
                                                          ObjectMapper objectMapper,
                                                          IdGenerator idGenerator) {
        return new KafkaWalletEventPublisher(properties, kafkaTemplate, objectMapper, idGenerator);
    }

    @Bean
    WalletBlockchainClientFactory walletBlockchainClientFactory() {
        return new WalletBlockchainClientFactory();
    }

    @Bean
    List<ChainDepositListener> chainDepositListeners(WalletServiceProperties properties,
                                                     WalletBlockchainClientFactory walletBlockchainClientFactory,
                                                     WalletAddressRepository walletAddressRepository,
                                                     WalletChainCursorRepository walletChainCursorRepository,
                                                     WalletDepositTransactionRepository walletDepositTransactionRepository) {
        return List.of(
                new Web3jChainDepositListener(
                        ChainType.ETH,
                        properties.chain(ChainType.ETH),
                        walletBlockchainClientFactory,
                        walletAddressRepository,
                        walletChainCursorRepository,
                        walletDepositTransactionRepository
                ),
                new Web3jChainDepositListener(
                        ChainType.BSC,
                        properties.chain(ChainType.BSC),
                        walletBlockchainClientFactory,
                        walletAddressRepository,
                        walletChainCursorRepository,
                        walletDepositTransactionRepository
                ),
                new TronApiChainDepositListener(properties.chain(ChainType.TRON), walletBlockchainClientFactory),
                new SolanaRpcChainDepositListener(properties.chain(ChainType.SOL), walletBlockchainClientFactory)
        );
    }
}
