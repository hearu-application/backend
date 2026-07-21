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
        this.jwtDecoder.setJwtValidator(createValidator());
    }

    OAuth2TokenValidator<Jwt> createValidator() {
        return new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                new JwtIssuerValidator(APPLE_ISSUER),
                // 등록된 client-id 중 하나라도 token의 aud에 포함되면 통과 (iOS/Android 동시 지원)
                new JwtClaimValidator<List<String>>("aud",
                        aud -> aud != null && aud.stream().anyMatch(appleClientIds::contains))
        );
    }

    public Payload verifyToken(String idToken) {
        log.debug("Apple ID Token 검증 시작. idToken={}", LogMasker.token(idToken));

        // 1. 서명 검증 (자동으로 JWKS에서 공개키 가져와서 검증) + iss/aud/exp 검증
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(idToken);
        } catch (Exception e) {
            // 잘못된 토큰은 정상적으로 발생하는 케이스이므로 WARN에는 메시지만 남기고,
            // 전체 스택트레이스는 디버깅이 필요한 dev/local(DEBUG)에서만 확인한다.
            log.warn("Apple ID Token 검증 실패. message={}", e.getMessage());
            log.debug("Apple ID Token 검증 실패 상세", e);
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

        // Apple은 재로그인 시 email을 내려주지 않으므로, 존재 여부가 디버깅에 중요하다.
        log.debug("Apple ID Token 검증 성공. sub={}, email={}, exp={}",
                LogMasker.sub(sub), LogMasker.email(email), jwt.getExpiresAt());

        return new Payload(sub, email);
    }

    public record Payload(
            String sub,
            String email
    ) {}

}
