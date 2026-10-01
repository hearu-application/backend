package com.example.hearu.auth.service;

import com.example.hearu.auth.domain.OauthProvider;
import com.example.hearu.auth.domain.OauthProviderFactory;
import com.example.hearu.auth.domain.ProviderType;
import com.example.hearu.auth.dto.request.OauthRequest;
import com.example.hearu.auth.dto.response.AuthResponse;
import com.example.hearu.auth.dto.response.OauthUserInfo;
import com.example.hearu.auth.domain.error.AuthErrorCode;
import com.example.hearu.common.security.jwt.JwtProvider;
import com.example.hearu.common.util.exception.BusinessException;
import com.example.hearu.user.domain.User;
import com.example.hearu.user.domain.policy.WithdrawalPolicy;
import com.example.hearu.user.infrastructure.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {

    @Mock RefreshTokenService refreshTokenService;
    @Mock UserRepository userRepository;
    @Mock OauthProviderFactory oauthProviderFactory;
    @Mock JwtProvider jwtProvider;
    @Mock OauthProvider oauthProvider;
    @Spy WithdrawalPolicy withdrawalPolicy = new WithdrawalPolicy(Duration.ofHours(24));

    @InjectMocks
    AuthService authService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.create("example@naver.com", ProviderType.KAKAO, "1234567890");
    }

    @Nested
    @DisplayName("회원가입 또는 로그인")
    class RegisterOrLogin {

        private final OauthRequest request = new OauthRequest("id_token_value", false);
        private final String accessToken = "access_token";
        private final String refreshToken = "refresh_token";

        @BeforeEach
        void setUp() {
            given(oauthProviderFactory.getProvider(ProviderType.KAKAO)).willReturn(oauthProvider);
        }

        @Test
        @DisplayName("신규 회원 - 회원가입 후 토큰 발급 (닉네임 없음)")
        void register_new_user() {
            // given
            OauthUserInfo userInfo = new OauthUserInfo("9999999999", "newuser@naver.com");
            User newUser = User.create(userInfo.email(), ProviderType.KAKAO, userInfo.sub());
            ReflectionTestUtils.setField(newUser, "userId", 1L);

            given(oauthProvider.getUserInfoFromOauthServer(request)).willReturn(userInfo);
            given(userRepository.findByProviderAndProviderUserIdAndDeletedAtIsNull(ProviderType.KAKAO, userInfo.sub()))
                    .willReturn(Optional.empty());
            given(userRepository.save(any(User.class))).willReturn(newUser);
            given(jwtProvider.createAccessToken(newUser.getUserId())).willReturn(accessToken);
            given(jwtProvider.createRefreshToken(newUser.getUserId())).willReturn(refreshToken);

            // when
            AuthResponse authResponse = authService.registerOrLogin(ProviderType.KAKAO, request);

            // then
            verify(refreshTokenService).issueInitialToken(newUser, refreshToken);
            assertThat(authResponse.accessToken()).isEqualTo(accessToken);
            assertThat(authResponse.refreshToken()).isEqualTo(refreshToken);
            assertThat(authResponse.nickname()).isNull();
        }

        @Test
        @DisplayName("기존 회원 - 로그인 후 토큰 및 닉네임 반환")
        void login_existing_user() {
            // given
            OauthUserInfo userInfo = new OauthUserInfo("1234567890", "example@naver.com");
            ReflectionTestUtils.setField(user, "userId", 1L);
            user.updateNickname("테스트닉네임");

            given(oauthProvider.getUserInfoFromOauthServer(request)).willReturn(userInfo);
            given(userRepository.findByProviderAndProviderUserIdAndDeletedAtIsNull(ProviderType.KAKAO, userInfo.sub()))
                    .willReturn(Optional.of(user));
            given(jwtProvider.createAccessToken(user.getUserId())).willReturn(accessToken);
            given(jwtProvider.createRefreshToken(user.getUserId())).willReturn(refreshToken);

            // when
            AuthResponse authResponse = authService.registerOrLogin(ProviderType.KAKAO, request);

            // then
            verify(refreshTokenService).issueInitialToken(user, refreshToken);
            assertThat(authResponse.accessToken()).isEqualTo(accessToken);
            assertThat(authResponse.refreshToken()).isEqualTo(refreshToken);
            assertThat(authResponse.nickname()).isEqualTo("테스트닉네임");
        }

        @Test
        @DisplayName("기존 회원 - email이 없어도 로그인에 성공한다 (Apple 재로그인 시나리오)")
        void login_existing_user_without_email() {
            // given - Apple은 최초 인증 시에만 email claim을 내려준다
            OauthUserInfo userInfo = new OauthUserInfo("1234567890", null);
            ReflectionTestUtils.setField(user, "userId", 1L);

            given(oauthProvider.getUserInfoFromOauthServer(request)).willReturn(userInfo);
            given(userRepository.findByProviderAndProviderUserIdAndDeletedAtIsNull(ProviderType.KAKAO, userInfo.sub()))
                    .willReturn(Optional.of(user));
            given(jwtProvider.createAccessToken(user.getUserId())).willReturn(accessToken);
            given(jwtProvider.createRefreshToken(user.getUserId())).willReturn(refreshToken);

            // when
            AuthResponse authResponse = authService.registerOrLogin(ProviderType.KAKAO, request);

            // then
            assertThat(authResponse.accessToken()).isEqualTo(accessToken);
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("신규 회원 - email이 없으면 MISSING_REQUIRED_CLAIMS를 던진다")
        void register_new_user_without_email_throws() {
            // given
            OauthUserInfo userInfo = new OauthUserInfo("9999999999", null);

            given(oauthProvider.getUserInfoFromOauthServer(request)).willReturn(userInfo);
            given(userRepository.findByProviderAndProviderUserIdAndDeletedAtIsNull(ProviderType.KAKAO, userInfo.sub()))
                    .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> authService.registerOrLogin(ProviderType.KAKAO, request))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.MISSING_REQUIRED_CLAIMS);

            verify(userRepository, never()).save(any(User.class));
        }
    }

    @Nested
    @DisplayName("회원가입 또는 로그인 - 탈퇴 유예 중")
    class RegisterOrLoginDuringWithdrawalGrace {

        private final String sub = "1234567890";
        private final OauthUserInfo userInfo = new OauthUserInfo(sub, "example@naver.com");
        private final String accessToken = "access_token";
        private final String refreshToken = "refresh_token";
        private User withdrawnUser;

        @BeforeEach
        void setUp() {
            given(oauthProviderFactory.getProvider(ProviderType.KAKAO)).willReturn(oauthProvider);
            given(oauthProvider.getUserInfoFromOauthServer(any(OauthRequest.class))).willReturn(userInfo);
            given(userRepository.findByProviderAndProviderUserIdAndDeletedAtIsNull(ProviderType.KAKAO, sub))
                    .willReturn(Optional.empty());

            withdrawnUser = User.create("example@naver.com", ProviderType.KAKAO, sub);
            ReflectionTestUtils.setField(withdrawnUser, "userId", 1L);
            withdrawnUser.softDelete();
        }

        private void givenNewUserSaved() {
            User newUser = User.create(userInfo.email(), ProviderType.KAKAO, sub);
            ReflectionTestUtils.setField(newUser, "userId", 2L);
            given(userRepository.save(any(User.class))).willReturn(newUser);
            given(jwtProvider.createAccessToken(2L)).willReturn(accessToken);
            given(jwtProvider.createRefreshToken(2L)).willReturn(refreshToken);
        }

        @Test
        @DisplayName("구버전 앱(플래그 없음) - 유예 중 계정을 조회하지 않고 신규 가입한다")
        void legacy_app_registers_new_user() {
            // given
            givenNewUserSaved();

            // when
            AuthResponse authResponse = authService.registerOrLogin(
                    ProviderType.KAKAO, new OauthRequest("id_token_value", false));

            // then
            assertThat(authResponse.accessToken()).isEqualTo(accessToken);
            assertThat(authResponse.pendingWithdrawal()).isNull();
            verify(userRepository, never()).findLatestRestorableForUpdate(any(), any(), any());
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("플래그 true + 유예 중 계정 있음 - 토큰 발급·가입 없이 삭제 예정 시각만 반환한다")
        void supported_app_receives_pending_withdrawal() {
            // given
            given(userRepository.findLatestRestorableForUpdate(eq(ProviderType.KAKAO), eq(sub), any()))
                    .willReturn(Optional.of(withdrawnUser));

            // when
            AuthResponse authResponse = authService.registerOrLogin(
                    ProviderType.KAKAO, new OauthRequest("id_token_value", true));

            // then
            assertThat(authResponse.accessToken()).isNull();
            assertThat(authResponse.refreshToken()).isNull();
            assertThat(authResponse.pendingWithdrawal().purgeAt())
                    .isEqualTo(withdrawnUser.getDeletedAt().plusHours(24));
            verify(userRepository, never()).save(any(User.class));
            verify(jwtProvider, never()).createAccessToken(any());
            verify(refreshTokenService, never()).issueInitialToken(any(), any());
        }

        @Test
        @DisplayName("플래그 true + 유예 중 계정 없음(만료 포함) - 신규 가입한다")
        void supported_app_without_restorable_registers_new_user() {
            // given
            given(userRepository.findLatestRestorableForUpdate(eq(ProviderType.KAKAO), eq(sub), any()))
                    .willReturn(Optional.empty());
            givenNewUserSaved();

            // when
            AuthResponse authResponse = authService.registerOrLogin(
                    ProviderType.KAKAO, new OauthRequest("id_token_value", true));

            // then
            assertThat(authResponse.accessToken()).isEqualTo(accessToken);
            assertThat(authResponse.pendingWithdrawal()).isNull();
        }

        @Test
        @DisplayName("플래그 false(새로 시작 선택) - 유예 중 계정을 조회하지 않고 신규 가입한다")
        void start_fresh_registers_new_user() {
            // given
            givenNewUserSaved();

            // when
            AuthResponse authResponse = authService.registerOrLogin(
                    ProviderType.KAKAO, new OauthRequest("id_token_value", false));

            // then
            assertThat(authResponse.accessToken()).isEqualTo(accessToken);
            verify(userRepository, never()).findLatestRestorableForUpdate(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("탈퇴 계정 복구")
    class Restore {

        private final String sub = "1234567890";
        private final OauthRequest request = new OauthRequest("id_token_value", true);
        private final String accessToken = "access_token";
        private final String refreshToken = "refresh_token";

        @BeforeEach
        void setUp() {
            given(oauthProviderFactory.getProvider(ProviderType.KAKAO)).willReturn(oauthProvider);
            given(oauthProvider.getUserInfoFromOauthServer(request)).willReturn(new OauthUserInfo(sub, null));
        }

        @Test
        @DisplayName("성공 - deletedAt과 providerUserId를 되돌리고 토큰을 발급한다")
        void restore_success() {
            // given
            ReflectionTestUtils.setField(user, "userId", 1L);
            user.updateNickname("테스트닉네임");
            user.softDelete();

            given(userRepository.findByProviderAndProviderUserIdAndDeletedAtIsNull(ProviderType.KAKAO, sub))
                    .willReturn(Optional.empty());
            given(userRepository.findLatestRestorableForUpdate(eq(ProviderType.KAKAO), eq(sub), any(LocalDateTime.class)))
                    .willReturn(Optional.of(user));
            given(jwtProvider.createAccessToken(1L)).willReturn(accessToken);
            given(jwtProvider.createRefreshToken(1L)).willReturn(refreshToken);

            // when
            AuthResponse authResponse = authService.restore(ProviderType.KAKAO, request);

            // then
            assertThat(user.getDeletedAt()).isNull();
            assertThat(user.getProviderUserId()).isEqualTo(sub);
            verify(refreshTokenService).issueInitialToken(user, refreshToken);
            assertThat(authResponse.accessToken()).isEqualTo(accessToken);
            assertThat(authResponse.nickname()).isEqualTo("테스트닉네임");
        }

        @Test
        @DisplayName("유예 중 계정이 없으면 WITHDRAWAL_NOT_RESTORABLE을 던진다")
        void restore_not_restorable() {
            // given
            given(userRepository.findByProviderAndProviderUserIdAndDeletedAtIsNull(ProviderType.KAKAO, sub))
                    .willReturn(Optional.empty());
            given(userRepository.findLatestRestorableForUpdate(eq(ProviderType.KAKAO), eq(sub), any(LocalDateTime.class)))
                    .willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> authService.restore(ProviderType.KAKAO, request))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.WITHDRAWAL_NOT_RESTORABLE);
            verify(refreshTokenService, never()).issueInitialToken(any(), any());
        }

        @Test
        @DisplayName("활성 계정이 이미 있으면 복구하지 않고 그 계정으로 로그인한다")
        void restore_with_active_user_logs_in() {
            // given
            ReflectionTestUtils.setField(user, "userId", 1L);
            given(userRepository.findByProviderAndProviderUserIdAndDeletedAtIsNull(ProviderType.KAKAO, sub))
                    .willReturn(Optional.of(user));
            given(jwtProvider.createAccessToken(1L)).willReturn(accessToken);
            given(jwtProvider.createRefreshToken(1L)).willReturn(refreshToken);

            // when
            AuthResponse authResponse = authService.restore(ProviderType.KAKAO, request);

            // then
            assertThat(authResponse.accessToken()).isEqualTo(accessToken);
            verify(userRepository, never()).findLatestRestorableForUpdate(any(), any(), any());
        }
    }
}
