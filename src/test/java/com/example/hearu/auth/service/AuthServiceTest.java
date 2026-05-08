package com.example.hearu.auth.service;

import com.example.hearu.auth.domain.OauthProvider;
import com.example.hearu.auth.domain.OauthProviderFactory;
import com.example.hearu.auth.domain.ProviderType;
import com.example.hearu.auth.dto.request.OauthRequest;
import com.example.hearu.auth.dto.response.AuthResponse;
import com.example.hearu.auth.dto.response.OauthUserInfo;
import com.example.hearu.common.security.jwt.JwtProvider;
import com.example.hearu.user.domain.User;
import com.example.hearu.user.infrastructure.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {

    @Mock RefreshTokenService refreshTokenService;
    @Mock UserRepository userRepository;
    @Mock OauthProviderFactory oauthProviderFactory;
    @Mock JwtProvider jwtProvider;
    @Mock OauthProvider oauthProvider;

    @InjectMocks
    AuthService authService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.create("example@naver.com", ProviderType.KAKAO, "1234567890");
    }

    @Nested
    @DisplayName("사용자 찾으면 반환, 없으면 생성")
    class FindOrCreateUserBy {

        @Test
        @DisplayName("기존 사용자 조회 성공")
        void user_found() {
            // given
            given(userRepository.findByProviderAndProviderUserIdAndDeletedAtIsNull(user.getProvider(), user.getProviderUserId()))
                    .willReturn(Optional.of(user));

            // when
            User findUser = authService.findOrCreateUserBy(user.getProvider(), user.getProviderUserId(), user.getEmail());

            // then
            assertThat(findUser.getProviderUserId()).isEqualTo(user.getProviderUserId());
            assertThat(findUser.getProvider()).isEqualTo(user.getProvider());
            assertThat(findUser.getEmail()).isEqualTo(user.getEmail());
        }

        @Test
        @DisplayName("사용자 조회 시 없으면 유저 생성")
        void user_not_found_create_user() {
            // given
            ProviderType provider = ProviderType.KAKAO;
            String sub = "0987654321";
            String email = "example2@naver.com";
            User newUser = User.create(email, provider, sub);
            given(userRepository.findByProviderAndProviderUserIdAndDeletedAtIsNull(provider, sub))
                    .willReturn(Optional.empty());
            given(userRepository.save(any(User.class))).willReturn(newUser);

            // when
            User createUser = authService.findOrCreateUserBy(provider, sub, email);

            // then
            assertThat(createUser.getEmail()).isEqualTo(email);
            assertThat(createUser.getProvider()).isEqualTo(provider);
            assertThat(createUser.getProviderUserId()).isEqualTo(sub);
            verify(userRepository).save(any(User.class));
        }
    }

    @Nested
    @DisplayName("회원가입 또는 로그인")
    class RegisterOrLogin {

        private final OauthRequest request = new OauthRequest("id_token_value");
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
    }
}
