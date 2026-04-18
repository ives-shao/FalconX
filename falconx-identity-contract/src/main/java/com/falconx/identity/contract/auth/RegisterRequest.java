package com.falconx.identity.contract.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * 注册请求契约。
 *
 * <p>该对象定义 identity-service 对外暴露的最小注册请求结构。
 * 当前阶段只保留邮箱和密码两个核心字段，后续若扩展邀请码等信息，
 * 需要先更新接口规范和统一接口文档。
 *
 * @param email 注册邮箱
 * @param password 明文密码，仅在传输过程中存在，不得进入日志
 */
public record RegisterRequest(
        @NotBlank String email,
        @NotBlank String password
) {
}
