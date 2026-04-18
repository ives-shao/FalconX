package com.falconx.wallet.service.impl;

import com.falconx.domain.enums.ChainType;
import com.falconx.wallet.config.WalletServiceProperties;
import com.falconx.wallet.entity.WalletAddressAssignment;
import com.falconx.wallet.entity.WalletAddressStatus;
import com.falconx.wallet.repository.WalletAddressRepository;
import com.falconx.wallet.service.WalletAddressAllocationService;
import java.time.OffsetDateTime;
import org.springframework.dao.DuplicateKeyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 顺序地址分配服务。
 *
 * <p>该实现用于 Stage 2B 冻结地址申请的最小策略：
 * 先查用户在该链上是否已有地址，若已有则直接返回；
 * 若没有，则按链维度递增地址索引并生成一个 stub 地址。
 */
public class SequentialWalletAddressAllocationService implements WalletAddressAllocationService {

    private static final Logger log = LoggerFactory.getLogger(SequentialWalletAddressAllocationService.class);

    private final WalletServiceProperties properties;
    private final WalletAddressRepository walletAddressRepository;

    public SequentialWalletAddressAllocationService(WalletServiceProperties properties,
                                                    WalletAddressRepository walletAddressRepository) {
        this.properties = properties;
        this.walletAddressRepository = walletAddressRepository;
    }

    @Override
    public WalletAddressAssignment allocateAddress(long userId, ChainType chain) {
        WalletAddressAssignment existing = walletAddressRepository.findByUserAndChainForUpdate(userId, chain).orElse(null);
        if (existing != null) {
            return existing;
        }
        return createNewAssignment(userId, chain);
    }

    private WalletAddressAssignment createNewAssignment(long userId, ChainType chain) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            int nextIndex = walletAddressRepository.nextAddressIndex(chain, properties.getAllocation().getStartIndex());
            String generatedAddress = "%s-%s-%06d".formatted(
                    properties.getAllocation().getAddressPrefix(),
                    chain.name().toLowerCase(),
                    nextIndex
            );
            WalletAddressAssignment assignment = new WalletAddressAssignment(
                    null,
                    userId,
                    chain,
                    generatedAddress,
                    nextIndex,
                    WalletAddressStatus.ASSIGNED,
                    OffsetDateTime.now()
            );
            try {
                return walletAddressRepository.save(assignment);
            } catch (DuplicateKeyException exception) {
                WalletAddressAssignment existing = walletAddressRepository.findByUserAndChainForUpdate(userId, chain).orElse(null);
                if (existing != null) {
                    return existing;
                }
                log.warn("wallet.address.allocate.retry userId={} chain={} attempt={} reason=duplicate_key",
                        userId,
                        chain,
                        attempt);
                if (attempt == 3) {
                    throw exception;
                }
            }
        }
        throw new IllegalStateException("Unable to allocate wallet address after retries");
    }
}
