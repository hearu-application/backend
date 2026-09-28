package com.example.hearu.common.security.jwt;

import java.time.Duration;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.IncorrectClaimException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MissingClaimException;
import io.jsonwebtoken.SignatureAlgorithm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtProviderTest {

    // dev·prod가 같은 서명 키를 쓰는 상황을 재현하기 위해 두 Provider가 이 키를 공유한다.
    private static final String SHARED_SECRET =
        Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());

    private JwtKeyManager keyManager;
    private JwtProvider prodProvider;
    private JwtProvider devProvider;

    @BeforeEach
    void setUp() {
        keyManager = new JwtKeyManager(SHARED_SECRET);
        prodProvider = createProvider("hearu-prod");
        devProvider = createProvider("hearu-dev");
    }

    private JwtProvider createProvider(String issuer) {
        JwtProvider provider = new JwtProvider(keyManager);
        ReflectionTestUtils.setField(provider, "accessTokenExpireTime", Duration.ofMinutes(30));
        ReflectionTestUtils.setField(provider, "refreshTokenExpireTime", Duration.ofDays(14));
        ReflectionTestUtils.setField(provider, "issuer", issuer);
        return provider;
    }

    @Nested
    @DisplayName("issuer 검증")
    class IssuerValidation {

        @Test
        @DisplayName("같은 환경에서 발급한 토큰은 통과하고 iss가 담긴다")
        void sameIssuer_passes() {
            // given
            String token = prodProvider.createAccessToken(3L);

            // when
            Claims claims = prodProvider.parseClaims(token);

            // then
            assertThat(claims.getIssuer()).isEqualTo("hearu-prod");
            assertThat(prodProvider.extractUserId(claims)).isEqualTo(3L);
        }

        @Test
        @DisplayName("서명 키가 같아도 다른 환경에서 발급한 access token은 거부된다")
        void otherEnvironmentAccessToken_rejected() {
            // given
            String devToken = devProvider.createAccessToken(3L);

            // when & then
            assertThatThrownBy(() -> prodProvider.parseClaims(devToken))
                .isInstanceOf(IncorrectClaimException.class);
        }

        @Test
        @DisplayName("서명 키가 같아도 다른 환경에서 발급한 refresh token은 거부된다")
        void otherEnvironmentRefreshToken_rejected() {
            // given
            String devToken = devProvider.createRefreshToken(3L);

            // when & then
            assertThatThrownBy(() -> prodProvider.parseClaims(devToken))
                .isInstanceOf(IncorrectClaimException.class);
        }

        @Test
        @DisplayName("iss가 없는 토큰(issuer 도입 전 발급)은 거부된다")
        void missingIssuer_rejected() {
            // given
            String legacyToken = Jwts.builder()
                .setSubject("3")
                .claim("type", "ACCESS")
                .setExpiration(new java.util.Date(System.currentTimeMillis() + 60_000))
                .signWith(keyManager.getKey(), SignatureAlgorithm.HS256)
                .compact();

            // when & then
            assertThatThrownBy(() -> prodProvider.parseClaims(legacyToken))
                .isInstanceOf(MissingClaimException.class);
        }
    }
}
