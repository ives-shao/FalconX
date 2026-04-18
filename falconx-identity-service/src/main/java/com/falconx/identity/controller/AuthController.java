package com.falconx.identity.controller;

import com.falconx.common.api.ApiResponse;
import com.falconx.identity.application.IdentityAuthenticationApplicationService;
import com.falconx.identity.application.IdentityRegistrationApplicationService;
import com.falconx.identity.command.LoginIdentityUserCommand;
import com.falconx.identity.command.RefreshIdentityTokenCommand;
import com.falconx.identity.command.RegisterIdentityUserCommand;
import com.falconx.identity.contract.auth.AuthTokenResponse;
import com.falconx.identity.contract.auth.LoginRequest;
import com.falconx.identity.contract.auth.RefreshTokenRequest;
import com.falconx.identity.contract.auth.RegisterRequest;
import com.falconx.identity.contract.auth.RegisterResponse;
import com.falconx.infrastructure.trace.TraceIdConstants;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 身份认证控制器。
 *
 * <p>该控制器对外暴露 Stage 3A 已落地的最小身份接口：
 *
 * <ul>
 *   <li>注册</li>
 *   <li>登录</li>
 *   <li>刷新 token</li>
 * </ul>
 *
 * <p>当前阶段仍是 identity-service 直出接口，后续在 Stage 4 会由 gateway 统一接入。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final IdentityRegistrationApplicationService identityRegistrationApplicationService;
    private final IdentityAuthenticationApplicationService identityAuthenticationApplicationService;

    public AuthController(IdentityRegistrationApplicationService identityRegistrationApplicationService,
                          IdentityAuthenticationApplicationService identityAuthenticationApplicationService) {
        this.identityRegistrationApplicationService = identityRegistrationApplicationService;
        this.identityAuthenticationApplicationService = identityAuthenticationApplicationService;
    }

    /**
     * 注册接口。
     *
     * @param request 注册请求
     * @return 注册结果
     */
    @PostMapping("/register")
    public ApiResponse<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("identity.http.register.received");
        RegisterResponse response = identityRegistrationApplicationService.register(
                new RegisterIdentityUserCommand(request.email(), request.password())
        );
        return success(response);
    }

    /**
     * 登录接口。
     *
     * @param request 登录请求
     * @return token 结果
     */
    @PostMapping("/login")
    public ApiResponse<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("identity.http.login.received");
        AuthTokenResponse response = identityAuthenticationApplicationService.login(
                new LoginIdentityUserCommand(request.email(), request.password())
        );
        return success(response);
    }

    /**
     * Refresh Token 刷新接口。
     *
     * @param request 刷新请求
     * @return 新 token 结果
     */
    @PostMapping("/refresh")
    public ApiResponse<AuthTokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        log.info("identity.http.refresh.received");
        AuthTokenResponse response = identityAuthenticationApplicationService.refresh(
                new RefreshIdentityTokenCommand(request.refreshToken())
        );
        return success(response);
    }

    private <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(
                "0",
                "success",
                data,
                OffsetDateTime.now(),
                MDC.get(TraceIdConstants.TRACE_ID_MDC_KEY)
        );
    }
}
