package com.dothis.fintris.global.security;

import com.dothis.fintris.global.config.properties.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private static final String CLAIM_TOKEN_TYPE = "typ";
    public static final String TOKEN_TYPE_ACCESS = "ACCESS";
    public static final String TOKEN_TYPE_REFRESH = "REFRESH";

    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final long accessExpMs;
    private final long refreshExpMs;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        String secret = jwtProperties.getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("jwt.secret 설정이 비어 있습니다.");
        }
        // secret은 HS256 기준 32바이트 이상이어야 한다
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("jwt.secret은 UTF-8 기준 " + MIN_SECRET_BYTES + "바이트 이상이어야 합니다.");
        }
        if (jwtProperties.getAccessExpMs() <= 0 || jwtProperties.getRefreshExpMs() <= 0) {
            throw new IllegalStateException("jwt.access-exp-ms와 jwt.refresh-exp-ms는 양수여야 합니다.");
        }

        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.accessExpMs = jwtProperties.getAccessExpMs();
        this.refreshExpMs = jwtProperties.getRefreshExpMs();
    }

    public String createAccessToken(Long userId) {
        return buildToken(userId, TOKEN_TYPE_ACCESS, accessExpMs);
    }

    public String createRefreshToken(Long userId) {
        return buildToken(userId, TOKEN_TYPE_REFRESH, refreshExpMs);
    }

    private String buildToken(Long userId, String type, long ttlMs) {
        Date now = new Date();
        return Jwts.builder()
                .id(UUID.randomUUID().toString()) // 같은 초에 발급해도 토큰이 겹치지 않게 한다
                .subject(String.valueOf(userId))
                .claim(CLAIM_TOKEN_TYPE, type)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMs))
                .signWith(key)
                .compact();
    }

    // 서명·만료 검증 후 claims를 반환하고, 유효하지 않으면 null
    public Claims parseOrNull(String token) {
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public Long getUserId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    public boolean isType(Claims claims, String type) {
        return type.equals(claims.get(CLAIM_TOKEN_TYPE, String.class));
    }

    public long getAccessExpMs() {
        return accessExpMs;
    }

    public long getRefreshExpMs() {
        return refreshExpMs;
    }
}
