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
public class GoogleTokenVerifier {

    private static final String GOOGLE_JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";

    // Google은 iss를 scheme 유무 두 형태로 발급하므로 모두 허용한다.
    private static final List<String> GOOGLE_ISSUERS = List.of(
            "https://accounts.google.com",
            "accounts.google.com"
    );

    // iOS/Android 모두 serverClientId(웹 클라이언트 ID)로 토큰을 요청하므로 aud는 하나로 통일된다.
    @Value("${oauth.google.client-id}")
    private String googleClientId;

    private NimbusJwtDecoder jwtDecoder;

    @PostConstruct
    public void init() {
        this.jwtDecoder = NimbusJwtDecoder
                .withJwkSetUri(GOOGLE_JWKS_URI)
                .restOperations(JwksRestTemplateFactory.create())
                .build();
        this.jwtDecoder.setJwtValidator(createValidator());
    }

    OAuth2TokenValidator<Jwt> createValidator() {
        return new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                // 현재 Spring Security는 iss를 String으로 두지만, 버전에 따라 URL로 변환될 수
                // 있어 타입에 의존하지 않도록 문자열로 비교한다.
                new JwtClaimValidator<Object>("iss",
                        iss -> iss != null && GOOGLE_ISSUERS.contains(iss.toString())),
                new JwtClaimValidator<List<String>>("aud",
                        aud -> aud != null && aud.contains(googleClientId))
        );
    }

    public Payload verifyToken(String idToken) {
        log.debug("Google ID Token 검증 시작. idToken={}", LogMasker.token(idToken));

        // 1. 서명 검증 (자동으로 JWKS에서 공개키 가져와서 검증) + iss/aud/exp 검증
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(idToken);
        } catch (Exception e) {
            // 잘못된 토큰은 정상적으로 발생하는 케이스라 스택트레이스에 정보가 없다. 타입·메시지만 남긴다.
            log.warn("Google ID Token 검증 실패. reason={}, message={}",
                e.getClass().getSimpleName(), e.getMessage());
            throw new BusinessException(AuthErrorCode.INVALID_ID_TOKEN);
        }

        String sub = jwt.getSubject();
        String email = jwt.getClaimAsString("email");

        // 2. 필수 claim 검증 (email은 신규 가입 시에만 필요하므로 AuthService에서 검증)
        if (sub == null) {
            log.warn("Google ID Token에 sub claim이 없습니다.");
            throw new BusinessException(AuthErrorCode.MISSING_REQUIRED_CLAIMS);
        }

        log.debug("Google ID Token 검증 성공. sub={}, email={}, exp={}",
                LogMasker.sub(sub), LogMasker.email(email), jwt.getExpiresAt());

        return new Payload(sub, email);
    }

    public record Payload(
            String sub,
            String email
    ) {}

}
