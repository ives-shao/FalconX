package com.falconx.identity.command;

/**
 * 注册命令。
 *
 * @param email 注册邮箱
 * @param password 明文密码
 */
public record RegisterIdentityUserCommand(
        String email,
        String password
) {
}
