package com.falconx.gateway.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.falconx.gateway.config.GatewaySecurityProperties;
import com.falconx.infrastructure.security.RsaPemSupport;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.Signature;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * gateway JWT 校验器。
 *
 * <p>该组件负责使用 `identity-service` 对应的开发态 RSA 公钥验证 Access Token，
 * 并解析出 gateway 后续透传给下游的用户身份信息。
 */
@Component
public class GatewayJwtVerifier {

    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();
    private static final String ACCESS_TOKEN_BLACKLIST_KEY_PREFIX = "falconx:auth:token:blacklist:";

    private final ObjectMapper objectMapper;
    private final ReactiveStringRedisTemplate reactiveStringRedisTemplate;
    private final PublicKey publicKey;

    public GatewayJwtVerifier(ObjectMapper objectMapper,
                              ReactiveStringRedisTemplate reactiveStringRedisTemplate,
                              GatewaySecurityProperties properties) {
        this.objectMapper = objectMapper;
        this.reactiveStringRedisTemplate = reactiveStringRedisTemplate;
        this.publicKey = RsaPemSupport.parsePublicKey(properties.getPublicKeyPem());
    }

    /**
     * 验证并解析 Access Token。
     *
     * @param token JWT 文本
     * @return 解析后的最小主体
     */
    public Mono<GatewayAuthenticatedPrincipal> verifyAccessToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new IllegalStateException("Invalid token structure");
            }
            String signingInput = parts[0] + "." + parts[1];
            if (!verifySignature(signingInput, parts[2])) {
                throw new IllegalStateException("Invalid token signature");
            }

            JsonNode claims = objectMapper.readTree(BASE64_URL_DECODER.decode(parts[1]));
            if (!"access".equals(claims.path("typ").asText())) {
                throw new IllegalStateException("Token type is not access");
            }
            if (claims.path("exp").asLong(0) <= OffsetDateTime.now(ZoneOffset.UTC).toEpochSecond()) {
                throw new IllegalStateException("Token expired");
            }

            String userId = claims.path("sub").asText(null);
            String uid = claims.path("uid").asText(null);
            String status = claims.path("status").asText(null);
            String jti = claims.path("jti").asText(null);
            if (userId == null || uid == null || status == null || jti == null) {
                throw new IllegalStateException("Token payload missing required claims");
            }
            GatewayAuthenticatedPrincipal principal = new GatewayAuthenticatedPrincipal(userId, uid, status, jti);
            return reactiveStringRedisTemplate.hasKey(accessTokenBlacklistKey(jti))
                    .flatMap(blacklisted -> {
                        if (Boolean.TRUE.equals(blacklisted)) {
                            return Mono.error(new IllegalStateException("Access token blacklisted"));
                        }
                        return Mono.just(principal);
                    });
        } catch (Exception exception) {
            return Mono.error(new IllegalStateException("Invalid access token", exception));
        }
    }

    private boolean verifySignature(String signingInput, String encodedSignature) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey);
            signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
            return signature.verify(BASE64_URL_DECODER.decode(encodedSignature));
        } catch (GeneralSecurityException exception) {
            return false;
        }
    }

    private String accessTokenBlacklistKey(String jti) {
        return ACCESS_TOKEN_BLACKLIST_KEY_PREFIX + jti;
    }
}
