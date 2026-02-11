package com.example.hearu.common.security.jwt;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import org.springframework.beans.factory.annotation.Value;
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
}
