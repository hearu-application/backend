package com.example.hearu.auth.infrastructure.provider;

import com.example.hearu.auth.domain.error.AuthErrorCode;
import com.example.hearu.common.logging.LogMasker;
import com.example.hearu.common.util.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class KakaoTokenVerifier {

    private static final String KAKAO_ISSUER = "https://kauth.kakao.com";
    private static final String KAKAO_JWKS_URI = KAKAO_ISSUER + "/.well-known/jwks.json";

    @Value("${oauth.kakao.client-id}")
    private String kakaoClientId;

    private NimbusJwtDecoder jwtDecoder;

    @PostConstruct
    public void init() {
        this.jwtDecoder = NimbusJwtDecoder
                .withJwkSetUri(KAKAO_JWKS_URI)
                .restOperations(JwksRestTemplateFactory.create())
                .build();
        this.jwtDecoder.setJwtValidator(createValidator());
    }

    OAuth2TokenValidator<Jwt> createValidator() {
        return new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                new JwtIssuerValidator(KAKAO_ISSUER),
                new JwtClaimValidator<List<String>>("aud", aud -> aud != null && aud.contains(kakaoClientId))
        );
    }

    public Payload verifyToken(String idToken) {
        log.debug("Kakao ID Token 검증 시작. idToken={}", LogMasker.token(idToken));

        // 1. 서명 검증 (자동으로 JWKS에서 공개키 가져와서 검증) + iss/aud/exp 검증
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(idToken);
        } catch (Exception e) {
            // 잘못된 토큰은 정상적으로 발생하는 케이스라 스택트레이스에 정보가 없다. 타입·메시지만 남긴다.
            log.warn("Kakao ID Token 검증 실패. reason={}, message={}",
                e.getClass().getSimpleName(), e.getMessage());
            throw new BusinessException(AuthErrorCode.INVALID_ID_TOKEN);
        }

        String sub = jwt.getSubject();
        String email = jwt.getClaimAsString("email");

        if (sub == null || email == null) {
            log.warn("Kakao ID Token 필수 claim 누락 - sub 존재={}, email 존재={}", sub != null, email != null);
            throw new BusinessException(AuthErrorCode.MISSING_REQUIRED_CLAIMS);
        }

        log.debug("Kakao ID Token 검증 성공. sub={}, email={}, exp={}",
                LogMasker.sub(sub), LogMasker.email(email), jwt.getExpiresAt());

        return new Payload(sub, email);
    }

    public record Payload(
            String sub,
            String email
    ) {}

}
