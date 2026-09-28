package com.example.hearu.common.security.jwt;

import java.time.Duration;
import java.util.Base64;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.hearu.common.security.CustomAuthenticationEntryPoint;

import io.jsonwebtoken.InvalidClaimException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class JwtFilterTest {

    private static final String SHARED_SECRET =
        Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());

    private final JwtKeyManager keyManager = new JwtKeyManager(SHARED_SECRET);

    private JwtProvider createProvider(String issuer) {
        JwtProvider provider = new JwtProvider(keyManager);
        ReflectionTestUtils.setField(provider, "accessTokenExpireTime", Duration.ofMinutes(30));
        ReflectionTestUtils.setField(provider, "refreshTokenExpireTime", Duration.ofDays(14));
        ReflectionTestUtils.setField(provider, "issuer", issuer);
        return provider;
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("다른 환경에서 발급한 토큰은 인증되지 않고 500이 아닌 401 INVALID_JWT_ISSUER로 응답한다")
    void otherEnvironmentToken_returns401() throws Exception {
        // given
        String devToken = createProvider("hearu-dev").createAccessToken(3L);
        JwtFilter prodFilter = new JwtFilter(createProvider("hearu-prod"));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/diaries");
        request.addHeader("Authorization", "Bearer " + devToken);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        BadCredentialsException ex = catchThrowableOfType(
            BadCredentialsException.class,
            () -> prodFilter.doFilter(request, response, new MockFilterChain())
        );
        new CustomAuthenticationEntryPoint().commence(request, response, ex);

        // then
        assertThat(ex.getCause()).isInstanceOf(InvalidClaimException.class);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("INVALID_JWT_ISSUER");
    }
}
