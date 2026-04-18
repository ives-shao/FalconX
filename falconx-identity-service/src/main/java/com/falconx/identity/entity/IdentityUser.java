package com.falconx.identity.entity;

import com.falconx.domain.enums.UserStatus;
import java.time.OffsetDateTime;

/**
 * identity-service 用户实体。
 *
 * <p>该对象对应 `falconx_identity.t_user` 的内存骨架表示，
 * 用于冻结用户注册、登录、状态迁移和激活的最小字段形态。
 *
 * @param id 用户主键 ID
 * @param uid 对外展示 UID
 * @param email 归一化邮箱
 * @param passwordHash bcrypt 密码哈希
 * @param status 当前用户状态
 * @param activatedAt 激活时间
 * @param lastLoginAt 最近登录时间
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
public record IdentityUser(
        Long id,
        String uid,
        String email,
        String passwordHash,
        UserStatus status,
        OffsetDateTime activatedAt,
        OffsetDateTime lastLoginAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
