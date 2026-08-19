package com.example.hearu.auth.service;

import java.time.LocalDateTime;

import com.example.hearu.auth.domain.error.AuthErrorCode;
import com.example.hearu.auth.domain.policy.RefreshTokenPolicy;
import com.example.hearu.auth.domain.entity.RefreshToken;
import com.example.hearu.auth.infrastructure.repository.AuthRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
@Transactional
public class RefreshTokenService {

    private final AuthRepository authRepository;
    private final RefreshTokenPolicy refreshTokenPolicy;
    private final JwtProvider jwtProvider;

    public void issueInitialToken(User user, String newRefreshToken) {

        LocalDateTime expiresAt = jwtProvider.getRefreshTokenExpiresAt();

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
        log.debug("refresh token 발급/갱신 완료. userId={}, expiresAt={}", user.getUserId(), expiresAt);
    }

    private RefreshToken getRefreshToken(Long userId) {
        return authRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("refresh token을 가지고 있지 않습니다. userId={}", userId);
                    return new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
                });
    }

    public void deleteRefreshToken(Long userId) {
        authRepository.deleteById(userId);
        log.debug("refresh token 삭제 완료. userId={}", userId);
    }

    public RefreshTokenResponse getNewRefreshTokenAndAccessToken(RefreshTokenRequest request) {

        String inputToken = request.refreshToken();

        // 1. RefreshToken 검증 & Claim 획득
        Claims claims = refreshTokenPolicy.validateAndGetClaims(inputToken);

        Long userId = jwtProvider.extractUserId(claims);
        log.debug("refresh token 검증 통과. userId={}", userId);

        // 2. 저장된 토큰과 비교
        RefreshToken storedRefreshToken = getRefreshToken(userId);
        refreshTokenPolicy.validateStoredTokenMatch(inputToken, storedRefreshToken.getToken(), claims);

        // 3. 새 토큰 발급
        String accessToken = jwtProvider.createAccessToken(userId);
        String refreshToken = jwtProvider.createRefreshToken(userId);
        LocalDateTime newExpiresAt = jwtProvider.getRefreshTokenExpiresAt();

        // 4. Refresh token 저장
        storedRefreshToken.updateToken(refreshToken, newExpiresAt);
        log.debug("토큰 재발급 완료. userId={}, 새 만료시각={}", userId, newExpiresAt);

        // 5. Refresh token 반환
        return new RefreshTokenResponse(
                accessToken,
                refreshToken
        );
    }

    // 재시도(@Retryable)와 최종 실패 복구(@Recover)는 비트랜잭션 호출자인
    // RefreshTokenCleanupScheduler가 담당한다. 재시도를 이 메서드에 함께 두면 트랜잭션
    // 어드바이스와 순서가 모호해져, 롤백된 트랜잭션 안에서 재시도가 도는 위험이 있다.
    // 여기서는 트랜잭션 경계만 책임진다. (AiResponseCaller와 동일한 패턴)
    public void deleteExpiredRefreshTokens() {
        LocalDateTime now = LocalDateTime.now();
        int deletedCount = authRepository.deleteByExpiresAtBefore(now);

        if (deletedCount > 0) {
            log.info("만료 refresh token 삭제 - count={}", deletedCount);
        } else {
            log.debug("만료된 refresh token 없음. 기준시각={}", now);
        }
    }
}