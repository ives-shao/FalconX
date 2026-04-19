package com.falconx.identity.service.impl;

import com.falconx.identity.config.IdentityServiceProperties;
import com.falconx.identity.error.IdentityBusinessException;
import com.falconx.identity.error.IdentityErrorCode;
import com.falconx.identity.service.IdentitySecurityPolicyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 基于 Redis 的身份安全策略实现。
 *
 * <p>该实现只处理 Stage 6B 必须落地的两类 IP 级防护：
 *
 * <ul>
 *   <li>登录失败计数与临时锁定</li>
 *   <li>注册频率限制</li>
 * </ul>
 */
@Service
public class RedisIdentitySecurityPolicyService implements IdentitySecurityPolicyService {

    private static final Logger log = LoggerFactory.getLogger(RedisIdentitySecurityPolicyService.class);
    private static final String LOGIN_FAILURE_KEY_PREFIX = "falconx:auth:login:fail:";
    private static final String REGISTER_RATE_LIMIT_KEY_PREFIX = "falconx:auth:register:limit:";

    private final StringRedisTemplate stringRedisTemplate;
    private final IdentityServiceProperties properties;

    public RedisIdentitySecurityPolicyService(StringRedisTemplate stringRedisTemplate,
                                              IdentityServiceProperties properties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.properties = properties;
    }

    @Override
    public void ensureLoginAllowed(String clientIp) {
        Integer failureCount = readInteger(loginFailureKey(clientIp));
        if (failureCount != null && failureCount >= properties.getSecurity().getLoginFailureLimit()) {
            log.warn("identity.login.rate-limited clientIp={} failureCount={}", normalizeClientIp(clientIp), failureCount);
            throw new IdentityBusinessException(IdentityErrorCode.LOGIN_RATE_LIMITED);
        }
    }

    @Override
    public void recordLoginFailure(String clientIp) {
        String key = loginFailureKey(clientIp);
        stringRedisTemplate.opsForValue().increment(key);
        stringRedisTemplate.expire(key, properties.getSecurity().getLoginLockDuration());
    }

    @Override
    public void clearLoginFailures(String clientIp) {
        stringRedisTemplate.delete(loginFailureKey(clientIp));
    }

    @Override
    public void consumeRegisterQuota(String clientIp) {
        String key = registerRateLimitKey(clientIp);
        Long requestCount = stringRedisTemplate.opsForValue().increment(key);
        stringRedisTemplate.expire(key, properties.getSecurity().getRegisterWindow());
        if (requestCount != null && requestCount > properties.getSecurity().getRegisterLimit()) {
            log.warn("identity.register.rate-limited clientIp={} requestCount={}",
                    normalizeClientIp(clientIp),
                    requestCount);
            throw new IdentityBusinessException(IdentityErrorCode.REGISTER_RATE_LIMITED);
        }
    }

    private Integer readInteger(String key) {
        String value = stringRedisTemplate.opsForValue().get(key);
        if (value == null) {
            return null;
        }
        return Integer.parseInt(value);
    }

    private String loginFailureKey(String clientIp) {
        return LOGIN_FAILURE_KEY_PREFIX + normalizeClientIp(clientIp);
    }

    private String registerRateLimitKey(String clientIp) {
        return REGISTER_RATE_LIMIT_KEY_PREFIX + normalizeClientIp(clientIp);
    }

    private String normalizeClientIp(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return "unknown";
        }
        return clientIp.trim();
    }
}
