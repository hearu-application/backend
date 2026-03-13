package com.example.hearu.auth.service;

import static org.assertj.core.api.AssertionsForClassTypes.*;
import static org.mockito.BDDMockito.*;

import java.time.LocalDateTime;
import java.util.Optional;

import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.hearu.ai.character.domain.Companion;
import com.example.hearu.auth.domain.ProviderType;
import com.example.hearu.auth.domain.entity.RefreshToken;
import com.example.hearu.auth.domain.error.AuthErrorCode;
import com.example.hearu.auth.domain.policy.RefreshTokenPolicy;
import com.example.hearu.auth.dto.request.RefreshTokenRequest;
import com.example.hearu.auth.dto.response.RefreshTokenResponse;
import com.example.hearu.auth.infrastructure.repository.AuthRepository;
import com.example.hearu.common.client.slack.SlackNotifierClient;
import com.example.hearu.common.security.jwt.JwtProvider;
import com.example.hearu.common.util.exception.BusinessException;
import com.example.hearu.user.domain.User;

import io.jsonwebtoken.Claims;

@ExtendWith(MockitoExtension.class)
public class RefreshTokenServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDateTime FIXED_EXPIRES_AT = LocalDateTime.of(2026, 12, 31, 0, 0);

    @Mock
    SlackNotifierClient slackNotifierClient;

    @Mock
    AuthRepository authRepository;

    @Mock
    RefreshTokenPolicy refreshTokenPolicy;

    @Mock
    JwtProvider jwtProvider;

    @InjectMocks
    RefreshTokenService refreshTokenService;

    private User createUserWithId() {
        User user = User.create("example@naver.com", ProviderType.KAKAO, "1234567890", mock(Companion.class));
        ReflectionTestUtils.setField(user, "userId", RefreshTokenServiceTest.USER_ID);
        return user;
    }

    private void assertInvalidRefreshToken(ThrowableAssert.ThrowingCallable callable) {
        assertThatThrownBy(callable)
            .isInstanceOf(BusinessException.class)
            .hasMessage(AuthErrorCode.INVALID_REFRESH_TOKEN.getMessage());
    }

    @Nested
    @DisplayName("토큰 초기 발행")
    class IssueInitialToken {

        private User user;
        private String tokenToIssue;

        @BeforeEach
        void setUp() {
            user = createUserWithId();
            tokenToIssue = "new_refresh_token";
        }

        @Test
        @DisplayName("refresh token 존재 시 update")
        void update_when_token_exists() {
            // given
            RefreshToken refreshToken = RefreshToken.create(USER_ID, "refresh_token", FIXED_EXPIRES_AT);
            given(authRepository.findById(USER_ID)).willReturn(Optional.of(refreshToken));

            // when
            refreshTokenService.issueInitialToken(user, tokenToIssue);

            // then
            assertThat(refreshToken.getToken()).isEqualTo(tokenToIssue);
            verify(authRepository).save(any(RefreshToken.class));
            verify(jwtProvider, never()).parseClaims(any());
        }

        @Test
        @DisplayName("refresh token 미존재 시 신규 생성")
        void create_when_token_not_exists() {
            // given
            Claims claims = mock(Claims.class);

            given(authRepository.findById(USER_ID)).willReturn(Optional.empty());
            given(jwtProvider.parseClaims(tokenToIssue)).willReturn(claims);
            given(jwtProvider.extractExpiration(claims)).willReturn(FIXED_EXPIRES_AT);

            // when
            refreshTokenService.issueInitialToken(user, tokenToIssue);

            // then
            verify(authRepository).save(any(RefreshToken.class));
        }
    }

    @Nested
    @DisplayName("refresh token 반환")
    class GetRefreshToken {

        @Test
        @DisplayName("refresh token 없는 경우, 예외 처리")
        void throw_when_token_not_found() {
            // given
            given(authRepository.findById(USER_ID)).willReturn(Optional.empty());

            // when & then
            assertInvalidRefreshToken(() -> refreshTokenService.getRefreshToken(USER_ID));
        }

        @Test
        @DisplayName("refresh token 있는 경우 반환")
        void return_when_token_found() {
            // given
            RefreshToken refreshToken = RefreshToken.create(USER_ID, "refresh_token", FIXED_EXPIRES_AT);
            given(authRepository.findById(USER_ID)).willReturn(Optional.of(refreshToken));

            // when
            RefreshToken result = refreshTokenService.getRefreshToken(USER_ID);

            // then
            assertThat(result.getToken()).isEqualTo(refreshToken.getToken());
        }
    }

    @Nested
    @DisplayName("refresh token 삭제")
    class DeleteRefreshToken {

        @Test
        @DisplayName("refresh token 제거")
        void delete_refresh_token() {
            // when
            refreshTokenService.deleteRefreshToken(USER_ID);

            // then
            verify(authRepository).deleteById(USER_ID);
        }
    }

    @Nested
    @DisplayName("refresh token으로 토큰 재발급")
    class GetNewRefreshTokenAndAccessToken {

        @Test
        @DisplayName("token 검증 실패 시, 예외 처리")
        void throw_when_token_invalid() {
            // given
            RefreshTokenRequest request = new RefreshTokenRequest("invalidToken");

            given(refreshTokenPolicy.validateAndGetClaims("invalidToken"))
                .willThrow(new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));

            // when
            assertInvalidRefreshToken(() -> refreshTokenService.getNewRefreshTokenAndAccessToken(request));

            // then
            verify(jwtProvider, never()).parseClaims(any());
            verify(jwtProvider, never()).createAccessToken(any(Long.class));
            verify(jwtProvider, never()).createRefreshToken(any(Long.class));
        }

        @Test
        @DisplayName("저장된 refresh token과 일치하지 않는 경우, 예외 처리")
        void throw_when_token_not_match() {
            // given
            RefreshToken inputToken = RefreshToken.create(USER_ID, "refresh_token", FIXED_EXPIRES_AT);
            RefreshToken storedToken = RefreshToken.create(USER_ID, "stored_refresh_token", FIXED_EXPIRES_AT);
            RefreshTokenRequest request = new RefreshTokenRequest(inputToken.getToken());
            Claims claims = mock(Claims.class);

            given(refreshTokenPolicy.validateAndGetClaims(request.refreshToken())).willReturn(claims);
            given(jwtProvider.extractUserId(claims)).willReturn(USER_ID);
            given(authRepository.findById(USER_ID)).willReturn(Optional.of(storedToken));
            willThrow(new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN))
                .given(refreshTokenPolicy)
                .validateStoredTokenMatch(inputToken.getToken(), storedToken.getToken(), claims);

            // when
            assertInvalidRefreshToken(() -> refreshTokenService.getNewRefreshTokenAndAccessToken(request));

            // then
            verify(jwtProvider, never()).createAccessToken(any(Long.class));
            verify(jwtProvider, never()).createRefreshToken(any(Long.class));
        }

        @Test
        @DisplayName("access & refresh token 발급")
        void issue_access_and_refresh_token() {
            // given
            RefreshToken refreshToken = RefreshToken.create(USER_ID, "refresh_token", FIXED_EXPIRES_AT);
            String originalToken = refreshToken.getToken();
            RefreshTokenRequest request = new RefreshTokenRequest(originalToken);
            Claims claims = mock(Claims.class);

            given(refreshTokenPolicy.validateAndGetClaims(originalToken)).willReturn(claims);
            given(jwtProvider.extractUserId(claims)).willReturn(USER_ID);
            given(authRepository.findById(USER_ID)).willReturn(Optional.of(refreshToken));
            given(jwtProvider.createAccessToken(USER_ID)).willReturn("new_access_token");
            given(jwtProvider.createRefreshToken(USER_ID)).willReturn("new_refresh_token");

            // when
            RefreshTokenResponse response = refreshTokenService.getNewRefreshTokenAndAccessToken(request);

            // then
            assertThat(response.accessToken()).isEqualTo("new_access_token");
            assertThat(response.refreshToken()).isEqualTo("new_refresh_token");
            assertThat(refreshToken.getToken()).isEqualTo("new_refresh_token");
            verify(refreshTokenPolicy).validateStoredTokenMatch(originalToken, originalToken, claims);
        }
    }

    @Nested
    @DisplayName("만료된 refresh tokens 삭제")
    class DeleteExpiredRefreshTokens {

        @Test
        @DisplayName("만료된 토큰이 있으면 삭제")
        void delete_expired_tokens() {
            // given
            given(authRepository.deleteByExpiresAtBefore(any(LocalDateTime.class))).willReturn(3);

            // when
            refreshTokenService.deleteExpiredRefreshTokens();

            // then
            verify(authRepository).deleteByExpiresAtBefore(any(LocalDateTime.class));
        }

        @Test
        @DisplayName("슬랙 알림을 발송한다")
        void send_slack_notification() {
            // given
            DataAccessException exception = new DataAccessException("DB 연결 실패") {};

            // when
            refreshTokenService.recover(exception);

            // then
            verify(slackNotifierClient).sendNotification(anyString());
        }
    }
}
