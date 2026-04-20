package com.example.hearu.auth.infrastructure.provider;

import com.example.hearu.auth.domain.error.AuthErrorCode;
import com.example.hearu.common.util.exception.BusinessException;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Slf4j
@Component
public class GoogleTokenVerifier {

    @Value("${oauth.google.client-id}")
    private String CLIENT_ID;

    private GoogleIdTokenVerifier verifier;

    @PostConstruct
    public void init() {
        this.verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(CLIENT_ID))
                .build();
    }

    public Payload verifyToken(String idToken) {
        GoogleIdToken verifiedIdToken;
        try {
            // 1. 토큰 검증 (서명 + aud + exp + iss 모두 검증)
            verifiedIdToken = verifier.verify(idToken);
        } catch (Exception e) {
            log.warn("Google ID Token 검증 실패. message={}", e.getMessage(), e);
            throw new BusinessException(AuthErrorCode.INVALID_ID_TOKEN);
        }

        if (verifiedIdToken == null) {
            log.warn("Google ID Token이 유효하지 않습니다.");
            throw new BusinessException(AuthErrorCode.INVALID_ID_TOKEN);
        }

        // 2. Payload에서 사용자 정보 추출
        return verifiedIdToken.getPayload();
    }
}