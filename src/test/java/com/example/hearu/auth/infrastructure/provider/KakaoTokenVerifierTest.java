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

@DisplayName("KakaoTokenVerifier")
class KakaoTokenVerifierTest {

    private static final String CLIENT_ID = "test-kakao-client-id";

    private KakaoTokenVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new KakaoTokenVerifier();
        ReflectionTestUtils.setField(verifier, "kakaoClientId", CLIENT_ID);
    }

    private Jwt.Builder validJwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("kakao-sub-123")
                .claim("email", "user@example.com")
                .issuer("https://kauth.kakao.com")
                .audience(List.of(CLIENT_ID))
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
        @DisplayName("유효한 issuer와 audience면 통과한다")
        void valid_token_passes() {
            assertThat(validate(validJwt().build()).hasErrors()).isFalse();
        }

        @Test
        @DisplayName("등록되지 않은 audience는 거부한다")
        void unregistered_audience_rejected() {
            Jwt jwt = validJwt().audience(List.of("other-client-id")).build();

            assertThat(validate(jwt).hasErrors()).isTrue();
        }

        @Test
        @DisplayName("issuer가 Kakao가 아니면 거부한다")
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

            KakaoTokenVerifier.Payload payload = verifier.verifyToken("token");

            assertThat(payload.sub()).isEqualTo("kakao-sub-123");
            assertThat(payload.email()).isEqualTo("user@example.com");
        }

        @Test
        @DisplayName("디코딩에 실패하면 500이 아니라 INVALID_ID_TOKEN(401)을 던진다")
        void decode_failure_throws_invalid_token() {
            given(jwtDecoder.decode(anyString())).willThrow(new JwtException("invalid"));

            assertThatThrownBy(() -> verifier.verifyToken("token"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.INVALID_ID_TOKEN);
        }

        @Test
        @DisplayName("sub 또는 email이 없으면 MISSING_REQUIRED_CLAIMS를 던진다")
        void missing_claims_throws() {
            Jwt jwt = Jwt.withTokenValue("token")
                    .header("alg", "RS256")
                    .subject("kakao-sub-123")
                    .build();
            given(jwtDecoder.decode(anyString())).willReturn(jwt);

            assertThatThrownBy(() -> verifier.verifyToken("token"))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.MISSING_REQUIRED_CLAIMS);
        }
    }
}
