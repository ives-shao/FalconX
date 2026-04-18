package com.falconx.identity.command;

/**
 * 登录命令。
 *
 * @param email 登录邮箱
 * @param password 明文密码
 */
public record LoginIdentityUserCommand(
        String email,
        String password
) {
}
