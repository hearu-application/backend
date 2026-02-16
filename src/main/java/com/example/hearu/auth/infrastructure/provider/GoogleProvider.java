package com.example.hearu.auth.infrastructure.provider;

import com.example.hearu.auth.domain.OauthProvider;
import com.example.hearu.auth.domain.error.AuthErrorCode;
import com.example.hearu.common.util.exception.BusinessException;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import org.springframework.stereotype.Component;

import com.example.hearu.auth.domain.ProviderType;
import com.example.hearu.auth.dto.request.OauthRequest;
import com.example.hearu.auth.dto.response.OauthUserInfo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleProvider implements OauthProvider {

    private final GoogleTokenVerifier googleTokenVerifier;

    @Override
    public ProviderType getProviderType() {
        return ProviderType.GOOGLE;
    }

    @Override
    public OauthUserInfo getUserInfoFromOauthServer(OauthRequest request) {

        GoogleIdToken.Payload payload = googleTokenVerifier.verifyToken(request.idToken());

        if (payload == null) {
            log.warn("Google ID Token 검증 실패");
            throw new BusinessException(AuthErrorCode.INVALID_ID_TOKEN);
        }

        String sub = payload.getSubject();
        String email = payload.getEmail();

        return new OauthUserInfo(
            sub,
            email
        );
    }
}
