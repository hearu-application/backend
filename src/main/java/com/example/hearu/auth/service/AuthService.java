package com.example.hearu.auth.service;

import com.example.hearu.auth.domain.ProviderType;
import com.example.hearu.common.logging.LogMasker;
import com.example.hearu.user.domain.policy.WithdrawalPolicy;
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

import java.time.LocalDateTime;
import java.util.Optional;

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
    private final WithdrawalPolicy withdrawalPolicy;

    public AuthResponse registerOrLogin(
        ProviderType providerType,
        OauthRequest request
    ) {
        // 1. oauth provider 확인 후, 알맞은 oauth로 사용자 정보 가져오기
        OauthUserInfo userInfo = getUserInfo(providerType, request);

        // 2. 활성 사용자 조회
        Optional<User> activeUser = userRepository.findByProviderAndProviderUserIdAndDeletedAtIsNull(
                providerType, userInfo.sub());

        // 3. 활성 사용자가 없고 앱이 복구 안내를 지원하면, 유예 중인 탈퇴 계정이 있는지 먼저 알린다.
        //    구버전 앱은 안내 화면이 없으므로 이 분기를 타지 않고 기존처럼 신규 가입한다.
        if (activeUser.isEmpty() && request.withdrawalRestoreSupported()) {
            Optional<User> withdrawn = findRestorable(providerType, userInfo.sub());
            if (withdrawn.isPresent()) {
                LocalDateTime purgeAt = withdrawalPolicy.purgeAt(withdrawn.get().getDeletedAt());
                log.info("유예 중인 탈퇴 계정이 있어 복구 여부 선택을 요청. userId={}, provider={}",
                        withdrawn.get().getUserId(), providerType);
                return AuthResponse.withdrawalPending(purgeAt);
            }
        }

        // 4. 사용자 생성
        User user = activeUser
                .map(existing -> {
                    log.debug("기존 사용자 로그인. userId={}, provider={}", existing.getUserId(), providerType);
                    return existing;
                })
                .orElseGet(() -> createUser(providerType, userInfo.sub(), userInfo.email()));

        // 5. 토큰 발행 및 DB에 세션(refresh token) 저장
        AuthResponse response = issueTokens(user);
        log.info("OAuth 인증 완료. userId={}, provider={}", user.getUserId(), providerType);
        return response;
    }

    public AuthResponse restore(
        ProviderType providerType,
        OauthRequest request
    ) {
        // 1. oauth provider 확인 후, 알맞은 oauth로 사용자 정보 가져오기
        OauthUserInfo userInfo = getUserInfo(providerType, request);

        // 2. 이미 활성 계정이 있으면 복구하지 않고 그 계정으로 로그인한다.
        //    (중복 요청이거나, 유예 중 구버전 앱으로 이미 재가입한 경우)
        Optional<User> activeUser = userRepository.findByProviderAndProviderUserIdAndDeletedAtIsNull(
                providerType, userInfo.sub());
        if (activeUser.isPresent()) {
            log.debug("활성 계정이 있어 복구 없이 로그인. userId={}, provider={}",
                    activeUser.get().getUserId(), providerType);
            return issueTokens(activeUser.get());
        }

        // 3. 유예 중인 탈퇴 계정 조회 및 복구
        User user = findRestorable(providerType, userInfo.sub())
                .orElseThrow(() -> {
                    log.warn("복구할 수 있는 탈퇴 계정이 없습니다. provider={}", providerType);
                    return new BusinessException(AuthErrorCode.WITHDRAWAL_NOT_RESTORABLE);
                });
        user.restore(userInfo.sub());
        log.info("탈퇴 유예 중 계정 복구. userId={}, provider={}", user.getUserId(), providerType);

        // 4. 토큰 발행 및 DB에 세션(refresh token) 저장
        return issueTokens(user);
    }

    private OauthUserInfo getUserInfo(ProviderType providerType, OauthRequest request) {
        log.debug("OAuth 인증 시작. provider={}", providerType);

        OauthProvider oauthProvider = providerFactory.getProvider(providerType);
        OauthUserInfo userInfo = oauthProvider.getUserInfoFromOauthServer(request);
        log.debug("OAuth 사용자 정보 획득. provider={}, sub={}, email={}",
                providerType, LogMasker.sub(userInfo.sub()), LogMasker.email(userInfo.email()));
        return userInfo;
    }

    private Optional<User> findRestorable(ProviderType providerType, String sub) {
        LocalDateTime cutoff = withdrawalPolicy.graceCutoff(LocalDateTime.now());
        return userRepository.findLatestRestorableForUpdate(providerType, sub, cutoff);
    }

    private User createUser(ProviderType provider, String sub, String email) {
        // 신규 가입 시에만 email이 필수 (기존 사용자 로그인은 sub로 식별한다)
        if (email == null) {
            log.warn("신규 가입에 필요한 email이 없습니다. provider={}", provider);
            throw new BusinessException(AuthErrorCode.MISSING_REQUIRED_CLAIMS);
        }
        User created = userRepository.save(User.create(email, provider, sub));
        log.info("신규 사용자 가입. userId={}, provider={}", created.getUserId(), provider);
        return created;
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtProvider.createAccessToken(user.getUserId());
        String refreshToken = jwtProvider.createRefreshToken(user.getUserId());
        log.debug("토큰 발행 완료. userId={}", user.getUserId());

        refreshTokenService.issueInitialToken(user, refreshToken);

        return AuthResponse.issued(accessToken, refreshToken, user.getNickname());
    }
}
