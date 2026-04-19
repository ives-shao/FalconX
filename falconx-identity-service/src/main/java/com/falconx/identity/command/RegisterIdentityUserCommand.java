package com.falconx.identity.command;

/**
 * 注册命令。
 *
 * @param email 注册邮箱
 * @param password 明文密码
 * @param clientIp 客户端 IP
 */
public record RegisterIdentityUserCommand(
        String email,
        String password,
        String clientIp
) {
}
