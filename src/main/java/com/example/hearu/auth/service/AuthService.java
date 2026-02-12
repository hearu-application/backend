package com.example.hearu.auth.service;

import com.example.hearu.auth.domain.ProviderType;
import com.example.hearu.user.infrastructure.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.hearu.auth.domain.OauthProvider;
import com.example.hearu.common.security.jwt.JwtProvider;
import com.example.hearu.auth.domain.OauthProviderFactory;
import com.example.hearu.auth.dto.request.OauthRequest;
import com.example.hearu.auth.dto.response.AuthResponse;
import com.example.hearu.auth.dto.response.OauthUserInfo;
import com.example.hearu.user.domain.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final RefreshTokenService refreshTokenService;
    private final UserRepository userRepository;
    private final OauthProviderFactory providerFactory;
    private final JwtProvider jwtProvider;

    @Transactional
    public AuthResponse registerOrLogin(
        ProviderType providerType,
        OauthRequest request
    ) {
        // 1. oauth provider 확인 후, 알맞은 oauth로 사용자 정보 가져오기
        OauthProvider oauthProvider = providerFactory.getProvider(providerType);
        OauthUserInfo userInfo = oauthProvider.getUserInfoFromOauthServer(request);

        // 2. 사용자 생성
        User user = findOrCreateUserBy(providerType, userInfo.sub(), userInfo.email());

        // 3. 토큰 발행
        String accessToken = jwtProvider.createAccessToken(user.getUserId());
        String refreshToken = jwtProvider.createRefreshToken(user.getUserId());

        // 4. DB에 세션(refresh token) 저장
        refreshTokenService.issueInitialToken(user, refreshToken);

        return new AuthResponse(
            accessToken,
            refreshToken
        );
    }

    @Transactional
    public User findOrCreateUserBy(ProviderType provider, String sub, String email) {
        return userRepository.findByProviderAndProviderUserId(provider, sub)
                .orElseGet(() -> userRepository.save(User.create(email, provider, sub)));
    }
}
