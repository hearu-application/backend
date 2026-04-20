package com.example.hearu.auth.infrastructure.provider;

import com.example.hearu.auth.domain.OauthProvider;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import org.springframework.stereotype.Component;

import com.example.hearu.auth.domain.ProviderType;
import com.example.hearu.auth.dto.request.OauthRequest;
import com.example.hearu.auth.dto.response.OauthUserInfo;

import lombok.RequiredArgsConstructor;

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

        String sub = payload.getSubject();
        String email = payload.getEmail();

        return new OauthUserInfo(
            sub,
            email
        );
    }
}
