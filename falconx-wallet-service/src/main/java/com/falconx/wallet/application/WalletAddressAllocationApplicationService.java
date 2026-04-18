package com.falconx.wallet.application;

import com.falconx.domain.enums.ChainType;
import com.falconx.wallet.entity.WalletAddressAssignment;
import com.falconx.wallet.service.WalletAddressAllocationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 钱包地址分配应用服务。
 *
 * <p>该应用服务负责把“用户申请链地址”串成 Stage 2B 的最小调用链：
 *
 * <ol>
 *   <li>接收地址申请请求</li>
 *   <li>调用地址分配领域服务</li>
 *   <li>返回分配结果并输出关键日志</li>
 * </ol>
 *
 * <p>当前阶段不暴露 HTTP 接口，只冻结应用层编排方向，便于后续 controller 接入。
 */
@Service
public class WalletAddressAllocationApplicationService {

    private static final Logger log = LoggerFactory.getLogger(WalletAddressAllocationApplicationService.class);

    private final WalletAddressAllocationService walletAddressAllocationService;

    public WalletAddressAllocationApplicationService(WalletAddressAllocationService walletAddressAllocationService) {
        this.walletAddressAllocationService = walletAddressAllocationService;
    }

    /**
     * 为用户申请指定链地址。
     *
     * @param userId 用户 ID
     * @param chain 链类型
     * @return 地址分配结果
     */
    @Transactional
    public WalletAddressAssignment allocateAddress(long userId, ChainType chain) {
        log.info("wallet.address.allocate.request userId={} chain={}", userId, chain);
        WalletAddressAssignment assignment = walletAddressAllocationService.allocateAddress(userId, chain);
        log.info("wallet.address.allocate.completed userId={} chain={} address={} addressIndex={}",
                userId,
                chain,
                assignment.address(),
                assignment.addressIndex());
        return assignment;
    }
}
