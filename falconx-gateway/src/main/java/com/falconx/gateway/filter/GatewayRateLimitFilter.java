package com.falconx.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.falconx.common.api.ApiResponse;
import com.falconx.gateway.config.GatewaySecurityProperties;
import com.falconx.infrastructure.trace.TraceIdConstants;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * gateway 认证入口限流过滤器。
 *
 * <p>当前 Stage 6B 先对公开认证接口落地每 IP 每分钟限流，
 * 用来补齐登录与注册的第一层防刷保护。
 */
@Component
public class GatewayRateLimitFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(GatewayRateLimitFilter.class);
    private static final Set<String> AUTH_LIMITED_PATHS = Set.of("/api/v1/auth/login", "/api/v1/auth/register");
    private static final String AUTH_RATE_LIMIT_KEY_PREFIX = "falconx:gateway:rate:auth:";

    private final ReactiveStringRedisTemplate reactiveStringRedisTemplate;
    private final GatewaySecurityProperties securityProperties;
    private final ObjectMapper objectMapper;

    public GatewayRateLimitFilter(ReactiveStringRedisTemplate reactiveStringRedisTemplate,
                                  GatewaySecurityProperties securityProperties,
                                  ObjectMapper objectMapper) {
        this.reactiveStringRedisTemplate = reactiveStringRedisTemplate;
        this.securityProperties = securityProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!AUTH_LIMITED_PATHS.contains(path)) {
            return chain.filter(exchange);
        }

        String clientIp = resolveClientIp(exchange.getRequest());
        String key = rateLimitKey(path, clientIp);
        return reactiveStringRedisTemplate.opsForValue().increment(key)
                .flatMap(currentCount -> reactiveStringRedisTemplate.expire(key, Duration.ofMinutes(2))
                        .thenReturn(currentCount))
                .flatMap(currentCount -> {
                    if (currentCount != null && currentCount > securityProperties.getAuthRequestRateLimitPerMinute()) {
                        log.warn("gateway.auth.rate-limited path={} clientIp={} currentCount={}", path, clientIp, currentCount);
                        if ("/api/v1/auth/register".equals(path)) {
                            return writeError(exchange, HttpStatus.TOO_MANY_REQUESTS, "10004", "Register Rate Limited");
                        }
                        return writeError(exchange, HttpStatus.TOO_MANY_REQUESTS, "10003", "Login Rate Limited");
                    }
                    return chain.filter(exchange);
                });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 5;
    }

    private String rateLimitKey(String path, String clientIp) {
        long currentMinuteBucket = Instant.now().getEpochSecond() / 60;
        return AUTH_RATE_LIMIT_KEY_PREFIX + sanitizePath(path) + ":" + clientIp + ":" + currentMinuteBucket;
    }

    private String sanitizePath(String path) {
        return path.replace('/', ':');
    }

    private String resolveClientIp(ServerHttpRequest request) {
        String clientIpHeader = request.getHeaders().getFirst(securityProperties.getClientIpHeader());
        if (clientIpHeader != null && !clientIpHeader.isBlank()) {
            return clientIpHeader.trim();
        }
        if (request.getRemoteAddress() != null && request.getRemoteAddress().getAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }
        return "unknown";
    }

    private Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status, String code, String message) {
        String traceId = exchange.getResponse().getHeaders().getFirst(TraceIdConstants.TRACE_ID_HEADER);
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        ApiResponse<Void> responseBody = new ApiResponse<>(code, message, null, OffsetDateTime.now(), traceId);
        try {
            byte[] bytes = objectMapper.writeValueAsString(responseBody).getBytes(StandardCharsets.UTF_8);
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (JsonProcessingException exception) {
            return exchange.getResponse().setComplete();
        }
    }
}
