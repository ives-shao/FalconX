package com.falconx.identity;

import com.falconx.identity.application.IdentityAuthenticationApplicationService;
import com.falconx.identity.application.IdentityRegistrationApplicationService;
import com.falconx.identity.command.LoginIdentityUserCommand;
import com.falconx.identity.command.RefreshIdentityTokenCommand;
import com.falconx.identity.command.RegisterIdentityUserCommand;
import com.falconx.identity.consumer.DepositCreditedEventConsumer;
import com.falconx.identity.contract.auth.AuthTokenResponse;
import com.falconx.identity.contract.auth.RegisterResponse;
import com.falconx.identity.error.IdentityBusinessException;
import com.falconx.identity.repository.mapper.test.IdentityTestSupportMapper;
import com.falconx.trading.contract.event.DepositCreditedEventPayload;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * identity-service 真实持久化集成测试。
 *
 * <p>该测试直接命中 `falconx_identity` schema，用来验证 Stage 5
 * 对身份服务的两个关键目标已经成立：
 *
 * <ol>
 *   <li>`t_user` 与 `t_refresh_token_session` 已经由真实应用链路写入</li>
 *   <li>`deposit.credited` 消费后的用户激活和 `t_inbox` 幂等落库可用</li>
 * </ol>
 */
@ActiveProfiles("stage5")
@SpringBootTest(
        classes = IdentityServiceApplication.class,
        properties = {
                "spring.datasource.url=jdbc:mysql://localhost:3306/falconx_identity_it?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                "spring.datasource.username=root",
                "spring.datasource.password=root"
        }
)
class IdentityPersistenceIntegrationTests {

    @Autowired
    private IdentityRegistrationApplicationService identityRegistrationApplicationService;

    @Autowired
    private IdentityAuthenticationApplicationService identityAuthenticationApplicationService;

    @Autowired
    private DepositCreditedEventConsumer depositCreditedEventConsumer;

    @Autowired
    private IdentityTestSupportMapper identityTestSupportMapper;

    @BeforeEach
    void cleanOwnerTables() {
        identityTestSupportMapper.clearOwnerTables();
    }

    @Test
    void shouldPersistUserTokenSessionAndInboxRecords() {
        RegisterResponse registerResponse = identityRegistrationApplicationService.register(
                new RegisterIdentityUserCommand("stage5.identity@example.com", "Passw0rd!")
        );

        Assertions.assertThrows(IdentityBusinessException.class, () -> identityAuthenticationApplicationService.login(
                new LoginIdentityUserCommand("stage5.identity@example.com", "Passw0rd!")
        ));

        depositCreditedEventConsumer.handle(
                "evt-stage5-identity-001",
                new DepositCreditedEventPayload(
                        91001L,
                        registerResponse.userId(),
                        81001L,
                        "ETH",
                        "USDT",
                        "0xidentitystage5",
                        new BigDecimal("100.00000000"),
                        OffsetDateTime.now()
                )
        );

        AuthTokenResponse loginResponse = identityAuthenticationApplicationService.login(
                new LoginIdentityUserCommand("stage5.identity@example.com", "Passw0rd!")
        );

        identityAuthenticationApplicationService.refresh(
                new RefreshIdentityTokenCommand(loginResponse.refreshToken())
        );

        Integer userStatus = identityTestSupportMapper.selectUserStatusById(registerResponse.userId());
        Integer refreshTokenCount = identityTestSupportMapper.countRefreshTokenSession();
        Integer usedRefreshTokenCount = identityTestSupportMapper.countUsedRefreshTokenSession();
        Integer inboxCount = identityTestSupportMapper.countProcessedInboxByEventId("evt-stage5-identity-001");

        Assertions.assertEquals(1, userStatus);
        Assertions.assertEquals(2, refreshTokenCount);
        Assertions.assertEquals(1, usedRefreshTokenCount);
        Assertions.assertEquals(1, inboxCount);
    }

    @Test
    void shouldNotMarkInboxProcessedWhenActivationUserIsMissing() {
        IllegalStateException exception = Assertions.assertThrows(IllegalStateException.class, () -> depositCreditedEventConsumer.handle(
                "evt-stage5-identity-missing-user",
                new DepositCreditedEventPayload(
                        91002L,
                        999999L,
                        81002L,
                        "ETH",
                        "USDT",
                        "0xidentitystage5missing",
                        new BigDecimal("12.34000000"),
                        OffsetDateTime.now()
                )
        ));

        Integer inboxCount = identityTestSupportMapper.countProcessedInboxByEventId("evt-stage5-identity-missing-user");

        Assertions.assertTrue(exception.getMessage().contains("Identity user not found"));
        Assertions.assertEquals(0, inboxCount);
    }
}
