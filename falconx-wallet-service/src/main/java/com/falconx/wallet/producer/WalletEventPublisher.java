package com.falconx.wallet.producer;

import com.falconx.wallet.contract.event.WalletDepositConfirmedEventPayload;
import com.falconx.wallet.contract.event.WalletDepositDetectedEventPayload;
import com.falconx.wallet.contract.event.WalletDepositReversedEventPayload;

/**
 * 钱包事件发布抽象。
 *
 * <p>该接口冻结 wallet-service 对外发布原始链事实事件的方向，
 * 后续真实 Kafka 实现必须沿用该接口，不应让应用层直接依赖底层客户端。
 */
public interface WalletEventPublisher {

    /**
     * 发布“已检测到原始入金”事件。
     *
     * @param payload 事件 payload
     */
    void publishDepositDetected(WalletDepositDetectedEventPayload payload);

    /**
     * 发布“原始入金已确认”事件。
     *
     * @param payload 事件 payload
     */
    void publishDepositConfirmed(WalletDepositConfirmedEventPayload payload);

    /**
     * 发布“原始入金已回滚”事件。
     *
     * @param payload 事件 payload
     */
    void publishDepositReversed(WalletDepositReversedEventPayload payload);
}
