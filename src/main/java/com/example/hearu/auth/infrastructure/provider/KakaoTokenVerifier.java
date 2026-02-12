package com.example.hearu.auth.infrastructure.provider;

import com.example.hearu.auth.domain.error.AuthErrorCode;
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

    @Value("${oauth.kakao.client-id}")
    private String kakaoClientId;

    private JwtDecoder jwtDecoder;

    @PostConstruct
    public void init() {
        this.jwtDecoder = NimbusJwtDecoder
                .withJwkSetUri("https://kauth.kakao.com/.well-known/jwks.json")
                .build();

        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                new JwtIssuerValidator("https://kauth.kakao.com"),
                new JwtClaimValidator<List<String>>("aud", aud -> aud != null && aud.contains(kakaoClientId))
        );
        ((NimbusJwtDecoder) this.jwtDecoder).setJwtValidator(validator);
    }

    public Payload verifyToken(String idToken) {
        // 1. 서명 검증 (자동으로 JWKS에서 공개키 가져와서 검증)
        Jwt jwt = jwtDecoder.decode(idToken);

        String sub = jwt.getSubject();
        String email = jwt.getClaimAsString("email");

        if (sub == null || email == null) {
            log.warn("누락된 정보 - sub: {}, email: {}", sub != null, email != null);
            throw new BusinessException(AuthErrorCode.MISSING_REQUIRED_CLAIMS);
        }

        return new Payload(sub, email);
    }

    public record Payload(
            String sub,
            String email
    ) {}

}
