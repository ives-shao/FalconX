package com.falconx.identity.error;

import com.falconx.common.error.ErrorCode;

/**
 * identity-service 业务错误码。
 *
 * <p>当前错误码集合与 REST 接口规范中的 `1xxxx` 保持一致，
 * 仅覆盖 Stage 3A 已实现的注册、登录、刷新和用户激活相关场景。
 */
public enum IdentityErrorCode implements ErrorCode {
    UNAUTHORIZED("10001", "Unauthorized"),
    USER_BANNED("10002", "User Banned"),
    INVALID_CREDENTIALS("10005", "Invalid Credentials"),
    REFRESH_TOKEN_INVALID("10006", "Refresh Token Invalid"),
    USER_FROZEN("10007", "User Frozen"),
    USER_ALREADY_EXISTS("10008", "User Already Exists"),
    EMAIL_FORMAT_INVALID("10009", "Email Format Invalid"),
    PASSWORD_TOO_WEAK("10010", "Password Too Weak"),
    USER_NOT_ACTIVATED("10011", "User Not Activated");

    private final String code;
    private final String message;

    IdentityErrorCode(String code, String message) {
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
