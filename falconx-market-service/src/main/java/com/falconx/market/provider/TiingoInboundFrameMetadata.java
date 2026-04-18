package com.falconx.market.provider;

/**
 * Tiingo 入站帧元数据。
 *
 * <p>该对象只承载当前市场链路在连接管理层真正需要关心的元信息，
 * 不参与标准报价计算。它的用途有两个：
 *
 * <ol>
 *   <li>识别订阅确认、心跳、错误等非报价帧</li>
 *   <li>让 provider 可以在不污染报价对象的前提下输出连接级日志</li>
 * </ol>
 *
 * <p>当前字段设计保持克制，只保留：
 *
 * <ul>
 *   <li>`messageType`：Tiingo 文档中的 `A/U/D/I/E/H`</li>
 *   <li>`service`：当前服务代码，例如 `fx`</li>
 *   <li>`subscriptionId`：订阅成功后可能返回的连接级订阅标识</li>
 *   <li>`responseCode / responseMessage`：信息帧、心跳帧、错误帧中常见的响应元数据</li>
 * </ul>
 */
public record TiingoInboundFrameMetadata(
        String messageType,
        String service,
        Long subscriptionId,
        Integer responseCode,
        String responseMessage
) {

    /**
     * 是否为订阅成功后的信息帧。
     */
    public boolean isInformational() {
        return "I".equalsIgnoreCase(messageType);
    }

    /**
     * 是否为心跳帧。
     */
    public boolean isHeartbeat() {
        return "H".equalsIgnoreCase(messageType);
    }

    /**
     * 是否为错误帧。
     */
    public boolean isError() {
        return "E".equalsIgnoreCase(messageType);
    }
}
