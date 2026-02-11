package com.example.hearu.auth.service;

import java.time.LocalDateTime;

import com.example.hearu.auth.domain.entity.RefreshToken;
import com.example.hearu.auth.infrastructure.repository.AuthRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.hearu.common.security.jwt.JwtProvider;
import com.example.hearu.user.domain.User;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final AuthRepository authRepository;
    private final JwtProvider jwtProvider;

    @Transactional
    public void issueInitialToken(User user, String newRefreshToken) {

        RefreshToken refreshToken = authRepository.findById(user.getUserId())
            .map(existing -> {
                existing.updateToken(newRefreshToken);
                return existing;
            })
            .orElseGet(() -> {
                Claims claims = jwtProvider.parseClaims(newRefreshToken);
                LocalDateTime expiresAt = jwtProvider.extractExpiration(claims);

                return RefreshToken.create(
                    user.getUserId(),
                    newRefreshToken,
                    expiresAt
                );
            });

        authRepository.save(refreshToken);
    }
}