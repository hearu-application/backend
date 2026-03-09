package com.example.hearu.common.security.jwt;

import java.security.Key;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.security.Keys;
import lombok.Getter;

@Getter
@Component
public class JwtKeyManager {

    private final Key key;

    public JwtKeyManager(@Value("${jwt.secret-key}") String secretKey) {
        byte[] bytes = Base64.getDecoder().decode(secretKey);

        if (bytes.length < 32) {
            throw new IllegalArgumentException("JWT 서명키는 최소 256bit 이상이어야 합니다.");
        }

        this.key = Keys.hmacShaKeyFor(bytes);
    }
}

