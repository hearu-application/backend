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
public class AppleTokenVerifier {

    private static final String APPLE_ISSUER = "https://appleid.apple.com";
    private static final String APPLE_JWKS_URI = APPLE_ISSUER + "/auth/keys";

    // iOS Bundle ID, Android Service ID 등 복수 audience 허용 (콤마 구분)
    @Value("${oauth.apple.client-ids}")
    private List<String> appleClientIds;

    private NimbusJwtDecoder jwtDecoder;

    @PostConstruct
    public void init() {
        this.jwtDecoder = NimbusJwtDecoder
                .withJwkSetUri(APPLE_JWKS_URI)
                .restOperations(JwksRestTemplateFactory.create())
                .build();

        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                new JwtIssuerValidator(APPLE_ISSUER),
                // 등록된 client-id 중 하나라도 token의 aud에 포함되면 통과 (iOS/Android 동시 지원)
                new JwtClaimValidator<List<String>>("aud",
                        aud -> aud != null && aud.stream().anyMatch(appleClientIds::contains))
        );
        this.jwtDecoder.setJwtValidator(validator);
    }

    public Payload verifyToken(String idToken) {
        // 1. 서명 검증 (자동으로 JWKS에서 공개키 가져와서 검증) + iss/aud/exp 검증
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(idToken);
        } catch (Exception e) {
            log.warn("Apple ID Token 검증 실패. message={}", e.getMessage(), e);
            throw new BusinessException(AuthErrorCode.INVALID_ID_TOKEN);
        }

        String sub = jwt.getSubject();
        // Apple은 최초 인증 시에만 email claim을 내려주므로 재로그인 시 null일 수 있다.
        // 신규 가입에 필요한 email 검증은 AuthService에서 수행한다.
        String email = jwt.getClaimAsString("email");

        // 2. 필수 claim 검증
        if (sub == null) {
            log.warn("Apple ID Token에 sub claim이 없습니다.");
            throw new BusinessException(AuthErrorCode.MISSING_REQUIRED_CLAIMS);
        }

        return new Payload(sub, email);
    }

    public record Payload(
            String sub,
            String email
    ) {}

}
