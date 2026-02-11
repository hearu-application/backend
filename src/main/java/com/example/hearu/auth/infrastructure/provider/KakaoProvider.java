package com.example.hearu.auth.infrastructure.provider;

import com.example.hearu.auth.domain.OauthProvider;
import org.springframework.stereotype.Component;

import com.example.hearu.auth.domain.ProviderType;
import com.example.hearu.auth.dto.request.OauthRequest;
import com.example.hearu.auth.dto.response.OauthUserInfo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoProvider implements OauthProvider {

    private final KakaoTokenVerifier kakaoTokenVerifier;

    @Override
    public ProviderType getProviderType() {
        return ProviderType.KAKAO;
    }

    @Override
    public OauthUserInfo getUserInfoFromOauthServer(OauthRequest request) {

        KakaoTokenVerifier.Payload payload = kakaoTokenVerifier.verifyToken(request.idToken());

        String sub = payload.sub();
        String email = payload.email();

        return new OauthUserInfo(
            sub,
            email
        );
    }
}
