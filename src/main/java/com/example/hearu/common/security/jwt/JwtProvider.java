package com.example.hearu.common.security.jwt;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtProvider {

    @Value("${jwt.access-token.expire-time}")
    private Duration accessTokenExpireTime;

    @Value("${jwt.refresh-token.expire-time}")
    private Duration refreshTokenExpireTime;

    private static final String BEARER_PREFIX = "Bearer ";
    private static final Pattern BEARER_PATTERN = Pattern.compile("^Bearer\\s+[A-Za-z0-9-_.]+$");

    private final JwtKeyManager jwtKeyManager;
    private static final SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.HS256;


    public String createAccessToken(Long userId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpireTime.toMillis());

        return Jwts.builder()
            .setSubject(String.valueOf(userId))
            .claim("type", "ACCESS")
            .setExpiration(expiry)
            .setIssuedAt(now)
            .signWith(jwtKeyManager.getKey(), signatureAlgorithm)
            .compact();
    }

    public String createRefreshToken(Long userId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + refreshTokenExpireTime.toMillis());

        return Jwts.builder()
            .setSubject(String.valueOf(userId))
            .claim("type", "REFRESH")
            .setExpiration(expiry)
            .setIssuedAt(now)
            .signWith(jwtKeyManager.getKey(), signatureAlgorithm)
            .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parserBuilder()
            .setSigningKey(jwtKeyManager.getKey())
            .build()
            .parseClaimsJws(token)
            .getBody();
    }

    public LocalDateTime extractExpiration(Claims claims) {
        return LocalDateTime.ofInstant(
            claims .getExpiration().toInstant(),
            ZoneId.systemDefault()
        );
    }

    public LocalDateTime getRefreshTokenExpiresAt() {
        return LocalDateTime.now().plus(refreshTokenExpireTime);
    }

    public Authentication createAuthentication(Long userId) {
        return new UsernamePasswordAuthenticationToken(
                userId,
                null,
                List.of(Role.USER)
        );
    }

    public Long extractUserId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    public String extractTokenType(Claims claims) {
        return claims.get("type", String.class);
    }

    public boolean isAccessToken(Claims claims) {
        return "ACCESS".equals(extractTokenType(claims));
    }

    public boolean isRefreshToken(Claims claims) {
        return "REFRESH".equals(extractTokenType(claims));
    }

    public String resolveToken(HttpServletRequest request) {
        String authorizationHeader = request.getHeader("Authorization");

        if (authorizationHeader == null ||
                !BEARER_PATTERN.matcher(authorizationHeader).matches()) {
            return null;
        }
        return authorizationHeader.substring(BEARER_PREFIX.length());
    }
}