package com.falconx.identity.application;

import com.falconx.domain.enums.UserStatus;
import com.falconx.identity.command.RegisterIdentityUserCommand;
import com.falconx.identity.contract.auth.RegisterResponse;
import com.falconx.identity.entity.IdentityUser;
import com.falconx.identity.error.IdentityBusinessException;
import com.falconx.identity.error.IdentityErrorCode;
import com.falconx.identity.repository.IdentityUserRepository;
import com.falconx.identity.service.IdentitySecurityPolicyService;
import com.falconx.identity.service.PasswordHashService;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 注册应用服务。
 *
 * <p>该服务负责把注册请求串成 Stage 3A 的最小注册链路：
 *
 * <ol>
 *   <li>归一化邮箱并校验格式</li>
 *   <li>校验密码强度</li>
 *   <li>检查邮箱唯一性</li>
 *   <li>生成密码哈希并保存用户</li>
 * </ol>
 */
@Service
public class IdentityRegistrationApplicationService {

    private static final Logger log = LoggerFactory.getLogger(IdentityRegistrationApplicationService.class);
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final IdentityUserRepository identityUserRepository;
    private final IdentitySecurityPolicyService identitySecurityPolicyService;
    private final PasswordHashService passwordHashService;

    public IdentityRegistrationApplicationService(IdentityUserRepository identityUserRepository,
                                                  IdentitySecurityPolicyService identitySecurityPolicyService,
                                                  PasswordHashService passwordHashService) {
        this.identityUserRepository = identityUserRepository;
        this.identitySecurityPolicyService = identitySecurityPolicyService;
        this.passwordHashService = passwordHashService;
    }

    /**
     * 执行用户注册。
     *
     * @param command 注册命令
     * @return 注册结果
     */
    @Transactional
    public RegisterResponse register(RegisterIdentityUserCommand command) {
        String normalizedEmail = normalizeEmail(command.email());
        validateEmail(normalizedEmail);
        validatePassword(command.password());
        identitySecurityPolicyService.consumeRegisterQuota(command.clientIp());

        log.info("identity.register.request email={} clientIp={}", normalizedEmail, command.clientIp());
        identityUserRepository.findByEmail(normalizedEmail).ifPresent(existing -> {
            throw new IdentityBusinessException(IdentityErrorCode.USER_ALREADY_EXISTS);
        });

        OffsetDateTime now = OffsetDateTime.now();
        IdentityUser user = new IdentityUser(
                null,
                null,
                normalizedEmail,
                passwordHashService.hash(command.password()),
                UserStatus.PENDING_DEPOSIT,
                null,
                null,
                now,
                now
        );
        IdentityUser persisted = identityUserRepository.save(user);
        log.info("identity.register.completed userId={} uid={} status={} clientIp={}",
                persisted.id(),
                persisted.uid(),
                persisted.status(),
                command.clientIp());
        return new RegisterResponse(
                persisted.id(),
                persisted.uid(),
                persisted.email(),
                persisted.status().name()
        );
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void validateEmail(String email) {
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IdentityBusinessException(IdentityErrorCode.EMAIL_FORMAT_INVALID);
        }
    }

    private void validatePassword(String password) {
        int length = password.length();
        if (length < 8 || length > 64) {
            throw new IdentityBusinessException(IdentityErrorCode.PASSWORD_TOO_WEAK);
        }
    }
}
