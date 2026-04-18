package com.falconx.domain.enums;

/**
 * FalconX 一期用户状态枚举。
 *
 * <p>该状态机与状态机规范保持一致，用于跨服务统一表达用户是否已入金激活。
 */
public enum UserStatus {
    PENDING_DEPOSIT,
    ACTIVE,
    FROZEN,
    BANNED
}
