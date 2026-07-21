package com.example.hearu.auth.infrastructure.provider;

import com.example.hearu.auth.domain.OauthProvider;
import org.springframework.stereotype.Component;

import com.example.hearu.auth.domain.ProviderType;
import com.example.hearu.auth.dto.request.OauthRequest;
import com.example.hearu.auth.dto.response.OauthUserInfo;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AppleProvider implements OauthProvider {

    private final AppleTokenVerifier appleTokenVerifier;

    @Override
    public ProviderType getProviderType() {
        return ProviderType.APPLE;
    }

    @Override
    public OauthUserInfo getUserInfoFromOauthServer(OauthRequest request) {

        AppleTokenVerifier.Payload payload = appleTokenVerifier.verifyToken(request.idToken());

        String sub = payload.sub();
        String email = payload.email();

        return new OauthUserInfo(
            sub,
            email
        );
    }
}
