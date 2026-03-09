package com.example.hearu.auth.infrastructure.provider;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Slf4j
@Component
public class GoogleTokenVerifier {

    @Value("${oauth.google.client-id}")
    private String CLIENT_ID;

    public Payload verifyToken(String idToken) {

        // 1. Verifier 생성
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(CLIENT_ID))
                .build();

        try {
            // 2. 토큰 검증 (서명 + aud + exp + iss 모두 검증)
            GoogleIdToken verifiedIdToken = verifier.verify(idToken);

            if (verifiedIdToken != null) {
                // 3. Payload에서 사용자 정보 추출
                return verifiedIdToken.getPayload();
            } else {
                // 토큰이 유효하지 않음
                return null;
            }

        } catch (Exception e) {
            // 검증 실패
            log.error(e.getMessage());
            return null;
        }
    }
}