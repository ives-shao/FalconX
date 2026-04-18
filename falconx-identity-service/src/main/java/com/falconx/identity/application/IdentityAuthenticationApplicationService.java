package com.falconx.identity.application;

import com.falconx.domain.enums.UserStatus;
import com.falconx.identity.command.LoginIdentityUserCommand;
import com.falconx.identity.command.RefreshIdentityTokenCommand;
import com.falconx.identity.contract.auth.AuthTokenResponse;
import com.falconx.identity.entity.IdentityUser;
import com.falconx.identity.error.IdentityBusinessException;
import com.falconx.identity.error.IdentityErrorCode;
import com.falconx.identity.repository.IdentityUserRepository;
import com.falconx.identity.service.IdentityTokenService;
import com.falconx.identity.service.PasswordHashService;
import com.falconx.identity.service.model.AuthTokenBundle;
import java.time.OffsetDateTime;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证应用服务。
 *
 * <p>该服务负责把登录和刷新请求串成最小认证链路：
 *
 * <ol>
 *   <li>查询用户并校验密码</li>
 *   <li>检查用户状态</li>
 *   <li>签发或轮换 token</li>
 *   <li>记录最近登录时间</li>
 * </ol>
 */
@Service
public class IdentityAuthenticationApplicationService {

    private static final Logger log = LoggerFactory.getLogger(IdentityAuthenticationApplicationService.class);

    private final IdentityUserRepository identityUserRepository;
    private final PasswordHashService passwordHashService;
    private final IdentityTokenService identityTokenService;

    public IdentityAuthenticationApplicationService(IdentityUserRepository identityUserRepository,
                                                    PasswordHashService passwordHashService,
                                                    IdentityTokenService identityTokenService) {
        this.identityUserRepository = identityUserRepository;
        this.passwordHashService = passwordHashService;
        this.identityTokenService = identityTokenService;
    }

    /**
     * 执行邮箱密码登录。
     *
     * @param command 登录命令
     * @return token 结果
     */
    @Transactional
    public AuthTokenResponse login(LoginIdentityUserCommand command) {
        String normalizedEmail = command.email().trim().toLowerCase(Locale.ROOT);
        log.info("identity.login.request email={}", normalizedEmail);

        IdentityUser user = identityUserRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IdentityBusinessException(IdentityErrorCode.INVALID_CREDENTIALS));
        if (!passwordHashService.matches(command.password(), user.passwordHash())) {
            throw new IdentityBusinessException(IdentityErrorCode.INVALID_CREDENTIALS);
        }
        if (user.status() == UserStatus.PENDING_DEPOSIT) {
            throw new IdentityBusinessException(IdentityErrorCode.USER_NOT_ACTIVATED);
        }
        if (user.status() == UserStatus.FROZEN) {
            throw new IdentityBusinessException(IdentityErrorCode.USER_FROZEN);
        }
        if (user.status() == UserStatus.BANNED) {
            throw new IdentityBusinessException(IdentityErrorCode.USER_BANNED);
        }

        IdentityUser passwordUpgradedUser = user;
        if (passwordHashService.needsRehash(user.passwordHash())) {
            passwordUpgradedUser = identityUserRepository.save(new IdentityUser(
                    user.id(),
                    user.uid(),
                    user.email(),
                    passwordHashService.hash(command.password()),
                    user.status(),
                    user.activatedAt(),
                    user.lastLoginAt(),
                    user.createdAt(),
                    OffsetDateTime.now()
            ));
        }

        IdentityUser updatedUser = identityUserRepository.save(new IdentityUser(
                passwordUpgradedUser.id(),
                passwordUpgradedUser.uid(),
                passwordUpgradedUser.email(),
                passwordUpgradedUser.passwordHash(),
                passwordUpgradedUser.status(),
                passwordUpgradedUser.activatedAt(),
                OffsetDateTime.now(),
                passwordUpgradedUser.createdAt(),
                OffsetDateTime.now()
        ));
        AuthTokenBundle tokenBundle = identityTokenService.issueTokens(updatedUser);
        log.info("identity.login.completed userId={} status={}", updatedUser.id(), updatedUser.status());
        return toResponse(tokenBundle);
    }

    /**
     * 使用 Refresh Token 刷新 token 对。
     *
     * @param command 刷新命令
     * @return 新 token 结果
     */
    @Transactional
    public AuthTokenResponse refresh(RefreshIdentityTokenCommand command) {
        log.info("identity.refresh.request");
        AuthTokenBundle tokenBundle = identityTokenService.refresh(command.refreshToken());
        log.info("identity.refresh.completed userStatus={}", tokenBundle.userStatus());
        return toResponse(tokenBundle);
    }

    private AuthTokenResponse toResponse(AuthTokenBundle tokenBundle) {
        return new AuthTokenResponse(
                tokenBundle.accessToken(),
                tokenBundle.refreshToken(),
                tokenBundle.accessTokenExpiresIn(),
                tokenBundle.refreshTokenExpiresIn(),
                tokenBundle.userStatus()
        );
    }
}
