package com.falconx.common.error;

/**
 * 通用系统级错误码骨架。
 *
 * <p>这里只放跨服务都可能复用的最小系统错误码，
 * 具体业务错误码仍应按 REST 规范放到对应服务的错误码枚举中。
 */
public enum CommonErrorCode implements ErrorCode {
    INTERNAL_ERROR("90001", "internal error"),
    DEPENDENCY_TIMEOUT("90002", "dependency timeout"),
    EVENT_PUBLISH_FAILED("90003", "event publish failed"),
    INVALID_REQUEST_PAYLOAD("90004", "invalid request payload");

    private final String code;
    private final String message;

    CommonErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
