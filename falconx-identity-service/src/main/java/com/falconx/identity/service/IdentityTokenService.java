package com.falconx.identity.service;

import com.falconx.identity.entity.IdentityUser;
import com.falconx.identity.service.model.AuthTokenBundle;

/**
 * 身份令牌服务抽象。
 *
 * <p>该服务负责 Access Token / Refresh Token 的签发与轮换，
 * 当前阶段由 identity-service 自己持有签发私钥。
 */
public interface IdentityTokenService {

    /**
     * 为指定用户签发新的令牌对。
     *
     * @param user 用户对象
     * @return 令牌对
     */
    AuthTokenBundle issueTokens(IdentityUser user);

    /**
     * 使用 Refresh Token 换取新的令牌对。
     *
     * @param refreshToken Refresh Token
     * @return 新令牌对
     */
    AuthTokenBundle refresh(String refreshToken);
}
