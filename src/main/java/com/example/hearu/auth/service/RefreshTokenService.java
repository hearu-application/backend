package com.example.hearu.auth.service;

import java.time.LocalDateTime;

import com.example.hearu.auth.domain.error.AuthErrorCode;
import com.example.hearu.auth.domain.policy.RefreshTokenPolicy;
import com.example.hearu.auth.domain.entity.RefreshToken;
import com.example.hearu.auth.infrastructure.repository.AuthRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.hearu.common.client.slack.SlackNotifierClient;
import com.example.hearu.common.security.jwt.JwtProvider;
import com.example.hearu.user.domain.User;
import com.example.hearu.auth.dto.request.RefreshTokenRequest;
import com.example.hearu.auth.dto.response.RefreshTokenResponse;
import com.example.hearu.common.util.exception.BusinessException;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final SlackNotifierClient slackNotifierClient;
    private final AuthRepository authRepository;
    private final RefreshTokenPolicy refreshTokenPolicy;
    private final JwtProvider jwtProvider;

    @Transactional
    public void issueInitialToken(User user, String newRefreshToken) {

        Claims claims = jwtProvider.parseClaims(newRefreshToken);
        LocalDateTime expiresAt = jwtProvider.extractExpiration(claims);

        RefreshToken refreshToken = authRepository.findById(user.getUserId())
            .map(existing -> {
                existing.updateToken(newRefreshToken, expiresAt);
                return existing;
            })
            .orElseGet(() -> RefreshToken.create(
                user.getUserId(),
                newRefreshToken,
                expiresAt
            ));

        authRepository.save(refreshToken);
    }

    @Transactional(readOnly = true)
    protected RefreshToken getRefreshToken(Long userId) {
        return authRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("refresh token을 가지고 있지 않습니다. userId={}", userId);
                    return new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
                });
    }

    @Transactional
    public void deleteRefreshToken(Long userId) {
        authRepository.deleteById(userId);
    }

    @Transactional
    public RefreshTokenResponse getNewRefreshTokenAndAccessToken(RefreshTokenRequest request) {

        String inputToken = request.refreshToken();

        // 1. RefreshToken 검증 & Claim 획득
        Claims claims = refreshTokenPolicy.validateAndGetClaims(inputToken);

        Long userId = jwtProvider.extractUserId(claims);

        // 2. 저장된 토큰과 비교
        RefreshToken storedRefreshToken = getRefreshToken(userId);
        refreshTokenPolicy.validateStoredTokenMatch(inputToken, storedRefreshToken.getToken(), claims);

        // 3. 새 토큰 발급
        String accessToken = jwtProvider.createAccessToken(userId);
        String refreshToken = jwtProvider.createRefreshToken(userId);
        LocalDateTime newExpiresAt = jwtProvider.getRefreshTokenExpiresAt();

        // 4. Refresh token 저장
        storedRefreshToken.updateToken(refreshToken, newExpiresAt);

        // 5. Refresh token 반환
        return new RefreshTokenResponse(
                accessToken,
                refreshToken
        );
    }

    @Retryable(
            retryFor = { DataAccessException.class },
            maxAttempts = 2, // 최초 1회 + 재시도 1회
            backoff = @Backoff(delay = 1000)
    )
    @Transactional
    public void deleteExpiredRefreshTokens() {
        LocalDateTime now = LocalDateTime.now();
        int deletedCount = authRepository.deleteByExpiresAtBefore(now);

        if (deletedCount > 0) {
            log.info("만료 refresh token 삭제 - count={}", deletedCount);
        }
    }

    @Recover
    public void recover(DataAccessException e) {
        log.error(
                "[Scheduler][RefreshTokenCleanup] 재시도 1회 후 최종 실패 - reason={}",
                e.getMessage(),
                e
        );

        slackNotifierClient.sendNotification("""
        [Refresh token 정리 스케줄 실패]
        • 작업: RefreshTokenCleanup
        • 재시도: 1회 후 실패
        • 원인: %s
        • 시각: %s
        """.formatted(
                        e.getMessage(),
                        LocalDateTime.now()
                )
        );
    }
}