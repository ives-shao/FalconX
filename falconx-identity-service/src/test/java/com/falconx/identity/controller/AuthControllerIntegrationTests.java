package com.falconx.identity.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.falconx.identity.IdentityServiceApplication;
import com.falconx.identity.config.IdentityTraceContextFilter;
import com.falconx.identity.consumer.DepositCreditedEventConsumer;
import com.falconx.identity.repository.mapper.test.IdentityTestSupportMapper;
import com.falconx.identity.service.IdentityTokenService;
import com.falconx.trading.contract.event.DepositCreditedEventPayload;
import java.math.BigDecimal;
import java.util.Set;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * 认证控制器集成测试。
 *
 * <p>该测试覆盖 Stage 5 下的最小真实身份链路：
 * 注册 -> 登录 -> Refresh Token 轮换。
 */
@ActiveProfiles("stage5")
@SpringBootTest(
        classes = IdentityServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.datasource.url=jdbc:mysql://localhost:3306/falconx_identity_it?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                "spring.datasource.username=root",
                "spring.datasource.password=root"
        }
)
class AuthControllerIntegrationTests {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IdentityTraceContextFilter identityTraceContextFilter;

    @Autowired
    private IdentityTestSupportMapper identityTestSupportMapper;

    @Autowired
    private DepositCreditedEventConsumer depositCreditedEventConsumer;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private IdentityTokenService identityTokenService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        identityTestSupportMapper.clearOwnerTables();
        clearSecurityKeys();
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(identityTraceContextFilter)
                .build();
    }

    @Test
    void shouldRegisterLoginAndRefreshSuccessfully() throws Exception {
        MockHttpServletResponse registerResponse = postJson("/api/v1/auth/register", """
                {
                  "email": "alice@example.com",
                  "password": "Passw0rd!"
                }
                """);
        JsonNode registerJson = objectMapper.readTree(registerResponse.getContentAsString());
        Assertions.assertEquals(200, registerResponse.getStatus());
        Assertions.assertNotNull(registerResponse.getHeader("X-Trace-Id"));
        Assertions.assertEquals("0", registerJson.path("code").asText());
        Assertions.assertEquals("alice@example.com", registerJson.path("data").path("email").asText());
        Assertions.assertEquals("PENDING_DEPOSIT", registerJson.path("data").path("status").asText());

        MockHttpServletResponse pendingLoginResponse = postJson("/api/v1/auth/login", """
                {
                  "email": "alice@example.com",
                  "password": "Passw0rd!"
                }
                """);
        JsonNode pendingLoginJson = objectMapper.readTree(pendingLoginResponse.getContentAsString());
        Assertions.assertEquals("10011", pendingLoginJson.path("code").asText());

        long userId = registerJson.path("data").path("userId").asLong();
        depositCreditedEventConsumer.handle(
                "evt-auth-controller-activate-001",
                new DepositCreditedEventPayload(
                        91001L,
                        userId,
                        81001L,
                        "ETH",
                        "USDT",
                        "0xauthcontroller",
                        new BigDecimal("100.00000000"),
                        OffsetDateTime.now()
                )
        );

        MockHttpServletResponse loginResponse = postJson("/api/v1/auth/login", """
                {
                  "email": "alice@example.com",
                  "password": "Passw0rd!"
                }
                """);
        JsonNode loginJson = objectMapper.readTree(loginResponse.getContentAsString());
        Assertions.assertEquals("0", loginJson.path("code").asText());
        String refreshToken = loginJson.path("data").path("refreshToken").asText();

        MockHttpServletResponse refreshResponse = postJson("/api/v1/auth/refresh", """
                {
                  "refreshToken": "%s"
                }
                """.formatted(refreshToken));
        JsonNode refreshJson = objectMapper.readTree(refreshResponse.getContentAsString());
        Assertions.assertEquals("0", refreshJson.path("code").asText());
        Assertions.assertNotEquals(
                refreshToken,
                refreshJson.path("data").path("refreshToken").asText()
        );

        MockHttpServletResponse secondRefreshResponse = postJson("/api/v1/auth/refresh", """
                {
                  "refreshToken": "%s"
                }
                """.formatted(refreshToken));
        JsonNode secondRefreshJson = objectMapper.readTree(secondRefreshResponse.getContentAsString());
        Assertions.assertEquals("10006", secondRefreshJson.path("code").asText());
    }

    @Test
    void shouldRejectDuplicateRegistration() throws Exception {
        String body = """
                {
                  "email": "duplicate@example.com",
                  "password": "Passw0rd!"
                }
                """;

        MockHttpServletResponse firstRegisterResponse = postJson("/api/v1/auth/register", body);
        JsonNode firstRegisterJson = objectMapper.readTree(firstRegisterResponse.getContentAsString());
        Assertions.assertEquals("0", firstRegisterJson.path("code").asText());

        MockHttpServletResponse secondRegisterResponse = postJson("/api/v1/auth/register", body);
        JsonNode secondRegisterJson = objectMapper.readTree(secondRegisterResponse.getContentAsString());
        Assertions.assertEquals("10008", secondRegisterJson.path("code").asText());
    }

    @Test
    void shouldRateLimitLoginAfterFiveFailuresFromSameClientIp() throws Exception {
        String clientIp = "198.51.100.10";
        MockHttpServletResponse registerResponse = postJson("/api/v1/auth/register", """
                {
                  "email": "locked@example.com",
                  "password": "Passw0rd!"
                }
                """, clientIp);
        JsonNode registerJson = objectMapper.readTree(registerResponse.getContentAsString());
        long userId = registerJson.path("data").path("userId").asLong();
        depositCreditedEventConsumer.handle(
                "evt-auth-controller-activate-locked-001",
                new DepositCreditedEventPayload(
                        91002L,
                        userId,
                        81002L,
                        "ETH",
                        "USDT",
                        "0xauthcontrollerlocked",
                        new BigDecimal("100.00000000"),
                        OffsetDateTime.now()
                )
        );

        for (int attempt = 0; attempt < 5; attempt++) {
            MockHttpServletResponse failedLoginResponse = postJson("/api/v1/auth/login", """
                    {
                      "email": "locked@example.com",
                      "password": "WrongPassw0rd!"
                    }
                    """, clientIp);
            JsonNode failedLoginJson = objectMapper.readTree(failedLoginResponse.getContentAsString());
            Assertions.assertEquals("10005", failedLoginJson.path("code").asText());
        }

        MockHttpServletResponse lockedResponse = postJson("/api/v1/auth/login", """
                {
                  "email": "locked@example.com",
                  "password": "Passw0rd!"
                }
                """, clientIp);
        JsonNode lockedJson = objectMapper.readTree(lockedResponse.getContentAsString());
        Assertions.assertEquals("10003", lockedJson.path("code").asText());
        Assertions.assertNotNull(stringRedisTemplate.opsForValue().get("falconx:auth:login:fail:" + clientIp));
    }

    @Test
    void shouldRateLimitRegistrationAfterFiveRequestsFromSameClientIp() throws Exception {
        String clientIp = "198.51.100.20";
        for (int attempt = 1; attempt <= 5; attempt++) {
            MockHttpServletResponse response = postJson("/api/v1/auth/register", """
                    {
                      "email": "register-limit-%s@example.com",
                      "password": "Passw0rd!"
                    }
                    """.formatted(attempt), clientIp);
            JsonNode json = objectMapper.readTree(response.getContentAsString());
            Assertions.assertEquals("0", json.path("code").asText());
        }

        MockHttpServletResponse limitedResponse = postJson("/api/v1/auth/register", """
                {
                  "email": "register-limit-6@example.com",
                  "password": "Passw0rd!"
                }
                """, clientIp);
        JsonNode limitedJson = objectMapper.readTree(limitedResponse.getContentAsString());
        Assertions.assertEquals("10004", limitedJson.path("code").asText());
    }

    @Test
    void shouldBlacklistCurrentAccessTokenWhenLogoutSucceeds() throws Exception {
        MockHttpServletResponse registerResponse = postJson("/api/v1/auth/register", """
                {
                  "email": "logout@example.com",
                  "password": "Passw0rd!"
                }
                """);
        JsonNode registerJson = objectMapper.readTree(registerResponse.getContentAsString());
        long userId = registerJson.path("data").path("userId").asLong();
        depositCreditedEventConsumer.handle(
                "evt-auth-controller-activate-logout-001",
                new DepositCreditedEventPayload(
                        91003L,
                        userId,
                        81003L,
                        "ETH",
                        "USDT",
                        "0xauthcontrollerlogout",
                        new BigDecimal("100.00000000"),
                        OffsetDateTime.now()
                )
        );

        MockHttpServletResponse loginResponse = postJson("/api/v1/auth/login", """
                {
                  "email": "logout@example.com",
                  "password": "Passw0rd!"
                }
                """);
        JsonNode loginJson = objectMapper.readTree(loginResponse.getContentAsString());
        String accessToken = loginJson.path("data").path("accessToken").asText();
        IdentityTokenService.ValidatedAccessToken tokenDetails = identityTokenService.parseAndValidateAccessToken(accessToken);

        MockHttpServletResponse logoutResponse = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + accessToken))
                .andReturn()
                .getResponse();
        JsonNode logoutJson = objectMapper.readTree(logoutResponse.getContentAsString());

        Assertions.assertEquals(200, logoutResponse.getStatus());
        Assertions.assertEquals("0", logoutJson.path("code").asText());
        String blacklistKey = "falconx:auth:token:blacklist:" + tokenDetails.jti();
        Assertions.assertEquals("1", stringRedisTemplate.opsForValue().get(blacklistKey));
        Long blacklistTtlSeconds = stringRedisTemplate.getExpire(blacklistKey);
        Assertions.assertNotNull(blacklistTtlSeconds);
        Assertions.assertTrue(blacklistTtlSeconds > 0);
        // Redis TTL 以秒级返回，JWT 剩余 TTL 基于当前时刻计算，边界时允许 1 秒取整差。
        Assertions.assertTrue(blacklistTtlSeconds <= tokenDetails.remainingTtl().toSeconds() + 1);
    }

    @Test
    void shouldRejectLogoutWhenAuthorizationHeaderMissingOrInvalid() throws Exception {
        MockHttpServletResponse missingAuthorizationResponse = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/logout"))
                .andReturn()
                .getResponse();
        JsonNode missingAuthorizationJson = objectMapper.readTree(missingAuthorizationResponse.getContentAsString());
        Assertions.assertEquals("10001", missingAuthorizationJson.path("code").asText());

        MockHttpServletResponse invalidAuthorizationResponse = mockMvc.perform(
                        MockMvcRequestBuilders.post("/api/v1/auth/logout")
                                .header("Authorization", "Bearer invalid-token"))
                .andReturn()
                .getResponse();
        JsonNode invalidAuthorizationJson = objectMapper.readTree(invalidAuthorizationResponse.getContentAsString());
        Assertions.assertEquals("10001", invalidAuthorizationJson.path("code").asText());
    }

    private MockHttpServletResponse postJson(String path, String body) throws Exception {
        return postJson(path, body, null);
    }

    private MockHttpServletResponse postJson(String path, String body, String clientIp) throws Exception {
        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders.post(path)
                        .header("X-Client-Ip", clientIp == null ? "" : clientIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
        return mvcResult.getResponse();
    }

    private void clearSecurityKeys() {
        deleteKeysByPattern("falconx:auth:login:fail:*");
        deleteKeysByPattern("falconx:auth:register:limit:*");
        deleteKeysByPattern("falconx:auth:token:blacklist:*");
    }

    private void deleteKeysByPattern(String pattern) {
        Set<String> keys = stringRedisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }
}
