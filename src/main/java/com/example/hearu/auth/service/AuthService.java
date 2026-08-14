package com.example.hearu.auth.service;

import com.example.hearu.auth.domain.ProviderType;
import com.example.hearu.common.logging.LogMasker;
import com.example.hearu.user.infrastructure.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.hearu.auth.domain.OauthProvider;
import com.example.hearu.auth.domain.error.AuthErrorCode;
import com.example.hearu.common.security.jwt.JwtProvider;
import com.example.hearu.common.util.exception.BusinessException;
import com.example.hearu.auth.domain.OauthProviderFactory;
import com.example.hearu.auth.dto.request.OauthRequest;
import com.example.hearu.auth.dto.response.AuthResponse;
import com.example.hearu.auth.dto.response.OauthUserInfo;
import com.example.hearu.user.domain.User;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
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
        log.debug("OAuth 인증 시작. provider={}", providerType);

        // 1. oauth provider 확인 후, 알맞은 oauth로 사용자 정보 가져오기
        OauthProvider oauthProvider = providerFactory.getProvider(providerType);
        OauthUserInfo userInfo = oauthProvider.getUserInfoFromOauthServer(request);
        log.debug("OAuth 사용자 정보 획득. provider={}, sub={}, email={}",
                providerType, LogMasker.sub(userInfo.sub()), LogMasker.email(userInfo.email()));

        // 2. 사용자 생성
        User user = findOrCreateUserBy(providerType, userInfo.sub(), userInfo.email());

        // 3. 토큰 발행
        String accessToken = jwtProvider.createAccessToken(user.getUserId());
        String refreshToken = jwtProvider.createRefreshToken(user.getUserId());
        log.debug("토큰 발행 완료. userId={}", user.getUserId());

        // 4. DB에 세션(refresh token) 저장
        refreshTokenService.issueInitialToken(user, refreshToken);

        log.info("OAuth 인증 완료. userId={}, provider={}", user.getUserId(), providerType);

        return new AuthResponse(
            accessToken,
            refreshToken,
            user.getNickname()
        );
    }

    private User findOrCreateUserBy(ProviderType provider, String sub, String email) {
        return userRepository.findByProviderAndProviderUserIdAndDeletedAtIsNull(provider, sub)
                .map(existing -> {
                    log.debug("기존 사용자 로그인. userId={}, provider={}", existing.getUserId(), provider);
                    return existing;
                })
                .orElseGet(() -> {
                    // 신규 가입 시에만 email이 필수 (기존 사용자 로그인은 sub로 식별한다)
                    if (email == null) {
                        log.warn("신규 가입에 필요한 email이 없습니다. provider={}", provider);
                        throw new BusinessException(AuthErrorCode.MISSING_REQUIRED_CLAIMS);
                    }
                    User created = userRepository.save(User.create(email, provider, sub));
                    log.info("신규 사용자 가입. userId={}, provider={}", created.getUserId(), provider);
                    return created;
                });
    }
}
