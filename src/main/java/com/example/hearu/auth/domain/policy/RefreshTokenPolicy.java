package com.example.hearu.auth.domain.policy;

import java.util.Date;

import com.example.hearu.auth.domain.error.AuthErrorCode;
import org.springframework.stereotype.Component;

import com.example.hearu.common.security.jwt.JwtProvider;
import com.example.hearu.common.util.exception.BusinessException;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenPolicy {

    private final JwtProvider jwtProvider;

    public Claims validateAndGetClaims(String token) {
        Claims claims = parseClaimsSafely(token);
        validateRefreshTokenType(claims);
        return claims;
    }

    private Claims parseClaimsSafely(String token) {
        try {
            return jwtProvider.parseClaims(token);

        } catch (ExpiredJwtException e) {
            Date exp = e.getClaims() != null ? e.getClaims().getExpiration() : null;
            log.warn("만료된 Refresh Token입니다. exp={}", exp);
            throw new BusinessException(AuthErrorCode.EXPIRED_REFRESH_TOKEN);

        } catch (JwtException | IllegalArgumentException e) {
            // MalformedJwtException, UnsupportedJwtException 포함
            log.warn("유효하지 않은 Refresh Token입니다. message={}", e.getMessage());
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }
    }

    private void validateRefreshTokenType(Claims claims) {
        String tokenType = jwtProvider.extractTokenType(claims);

        if (!jwtProvider.getRefreshTokenTypeValue().equals(tokenType)) {
            log.warn("Token Type이 Refresh가 아닙니다. 요청 token type={}", tokenType);
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }
    }

    public void validateStoredTokenMatch(String inputToken, String storedToken, Claims claims) {
        Long userId = jwtProvider.extractUserId(claims);

        if (!inputToken.equals(storedToken)) {
            log.warn("저장된 refresh token과 불일치합니다. userId={}", userId);
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }
    }
}

