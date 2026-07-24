package com.example.hearu.auth.infrastructure.provider;

import com.example.hearu.auth.domain.error.AuthErrorCode;
import com.example.hearu.common.util.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@DisplayName("AppleTokenVerifier")
class AppleTokenVerifierTest {

    private static final String IOS_BUNDLE_ID = "com.example.hearu";
    private static final String ANDROID_SERVICE_ID = "com.example.hearu.signin";

    private AppleTokenVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new AppleTokenVerifier();
        ReflectionTestUtils.setField(verifier, "appleClientIds",
                List.of(IOS_BUNDLE_ID, ANDROID_SERVICE_ID));
    }

    private Jwt.Builder validJwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("apple-sub-123")
                .claim("email", "user@privaterelay.appleid.com")
                .issuer("https://appleid.apple.com")
                .audience(List.of(IOS_BUNDLE_ID))
                .issuedAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(600));
    }

    private OAuth2TokenValidatorResult validate(Jwt jwt) {
        return verifier.createValidator().validate(jwt);
    }

    @Nested
    @DisplayName("토큰 검증 규칙")
    class Validator {

        @Test
        @DisplayName("iOS Bundle ID로 발급된 토큰을 통과시킨다")
        void ios_bundle_id_passes() {
            Jwt jwt = validJwt().audience(List.of(IOS_BUNDLE_ID)).build();

            assertThat(validate(jwt).hasErrors()).isFalse();
        }

        @Test
        @DisplayName("Android Service ID로 발급된 토큰을 통과시킨다")
        void android_service_id_passes() {
            Jwt jwt = validJwt().audience(List.of(ANDROID_SERVICE_ID)).build();

            assertThat(validate(jwt).hasErrors()).isFalse();
        }

        @Test
        @DisplayName("등록되지 않은 audience는 거부한다")
        void unregistered_audience_rejected() {
            Jwt jwt = validJwt().audience(List.of("com.attacker.app")).build();

            assertThat(validate(jwt).hasErrors()).isTrue();
        }

        @Test
        @DisplayName("issuer가 Apple이 아니면 거부한다")
        void invalid_issuer_rejected() {
            Jwt jwt = validJwt().issuer("https://evil.example.com").build();

            assertThat(validate(jwt).hasErrors()).isTrue();
        }

        @Test
        @DisplayName("만료된 토큰은 거부한다")
        void expired_token_rejected() {
            Jwt jwt = validJwt()
                    .issuedAt(Instant.now().minusSeconds(900))
                    .expiresAt(Instant.now().minusSeconds(300))
                    .build();

            assertThat(validate(jwt).hasErrors()).isTrue();
        }
    }

    @Nested
    @DisplayName("토큰 파싱")
    class VerifyToken {

        private NimbusJwtDecoder jwtDecoder;

        @BeforeEach
        void setUp() {
            jwtDecoder = mock(NimbusJwtDecoder.class);
            ReflectionTestUtils.setField(verifier, "jwtDecoder", jwtDecoder);
        }

        @Test
        @DisplayName("정상 토큰이면 sub와 email을 반환한다")
        void returns_payload() {
            given(jwtDecoder.decode(anyString())).willReturn(validJwt().build());

            AppleTokenVerifier.Payload payload = verifier.verifyToken("token");

            assertThat(payload.sub()).isEqualTo("apple-sub-123");
            assertThat(payload.email()).isEqualTo("user@privaterelay.appleid.com");
        }

        @Test
        @DisplayName("email이 없어도 통과한다 - Apple은 최초 인증 시에만 email을 내려준다")
        void missing_email_is_allowed() {
            Jwt jwt = Jwt.withTokenValue("token")
                    .header("alg", "RS256")
                    .subject("apple-sub-123")
                    .build();
            given(jwtDecoder.decode(anyString())).willReturn(jwt);

            AppleTokenVerifier.Payload payload = verifier.verifyToken("token");

            assertThat(payload.sub()).isEqualTo("apple-sub-123");
            assertThat(payload.email()).isNull();
        }

        @Test
        @DisplayName("sub가 없으면 MISSING_REQUIRED_CLAIMS를 던진다")
        void missing_sub_throws() {
            Jwt jwt = Jwt.withTokenValue("token")
                    .header("alg", "RS256")
                    .claim("email", "user@example.com")
                    .build();
            given(jwtDecoder.decode(anyString())).willReturn(jwt);

            assertThatThrownBy(() -> verifier.verifyToken("token"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.MISSING_REQUIRED_CLAIMS);
        }

        @Test
        @DisplayName("디코딩에 실패하면 INVALID_ID_TOKEN을 던진다")
        void decode_failure_throws_invalid_token() {
            given(jwtDecoder.decode(anyString())).willThrow(new JwtException("invalid"));

            assertThatThrownBy(() -> verifier.verifyToken("token"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.INVALID_ID_TOKEN);
        }
    }
}
