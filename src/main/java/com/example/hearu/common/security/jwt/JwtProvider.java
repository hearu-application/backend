package com.example.hearu.common.security.jwt;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
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

    // 환경(local/dev/prod)마다 다른 값. 서명 키가 환경 간에 공유되더라도
    // 다른 환경에서 발급된 토큰은 parseClaims에서 거부된다.
    @Value("${jwt.issuer}")
    private String issuer;

    private static final Pattern BEARER_PATTERN = Pattern.compile("^Bearer\\s+([A-Za-z0-9-_.]+)$");

    private final JwtKeyManager jwtKeyManager;

    public String createAccessToken(Long userId) {
        return createToken(userId, "ACCESS", accessTokenExpireTime);
    }

    public String createRefreshToken(Long userId) {
        return createToken(userId, "REFRESH", refreshTokenExpireTime);
    }

    private String createToken(Long userId, String type, Duration expireTime) {
        Date now = new Date();

        return Jwts.builder()
            .setIssuer(issuer)
            .setSubject(String.valueOf(userId))
            .claim("type", type)
            .setIssuedAt(now)
            .setExpiration(new Date(now.getTime() + expireTime.toMillis()))
            .signWith(jwtKeyManager.getKey(), SignatureAlgorithm.HS256)
            .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parserBuilder()
            .setSigningKey(jwtKeyManager.getKey())
            .requireIssuer(issuer)
            .build()
            .parseClaimsJws(token)
            .getBody();
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

    private String extractTokenType(Claims claims) {
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

        if (authorizationHeader == null) {
            return null;
        }
        Matcher matcher = BEARER_PATTERN.matcher(authorizationHeader);
        return matcher.matches() ? matcher.group(1) : null;
    }
}