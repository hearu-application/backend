package com.example.hearu.common.security.jwt;

import java.io.IOException;

import io.jsonwebtoken.Claims;
import org.slf4j.MDC;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.JwtException;
import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;

    @Override
    protected void doFilterInternal(
        @Nonnull HttpServletRequest request,
        @Nonnull HttpServletResponse response,
        @Nonnull FilterChain chain
    ) throws ServletException, IOException {

        try {
            String token = jwtProvider.resolveToken(request);

            if (token == null) {
                log.debug("[JWT] Authorization 토큰 없음. 익명으로 진행. uri={}", request.getRequestURI());
            } else {

                Claims claims = jwtProvider.parseClaims(token);

                if (jwtProvider.isAccessToken(claims)) {
                    Long userId = jwtProvider.extractUserId(claims);
                    SecurityContextHolder.getContext().setAuthentication(
                            jwtProvider.createAuthentication(userId)
                    );
                    MDC.put("userId", userId.toString());
                    log.debug("[JWT] 인증 성공. userId={}, exp={}", userId, claims.getExpiration());
                } else {
                    // access token이 아닌 토큰(예: refresh token)으로 API를 호출한 경우.
                    // 인증 없이 진행되어 이후 401로 처리된다.
                    log.debug("[JWT] Access Token이 아니므로 인증하지 않음. uri={}", request.getRequestURI());
                }
            }

        } catch (JwtException e) {
            // 서명·만료·형식·클레임(iss) 오류. 종류별 응답 코드는 CustomAuthenticationEntryPoint가 정한다.
            log.debug("[JWT] 토큰 검증 실패. reason={}, message={}",
                    e.getClass().getSimpleName(), e.getMessage());
            throw new BadCredentialsException("Invalid JWT", e);
        } catch (Exception e) {
            log.error("[JWT FILTER ERROR] 알 수 없는 인증 처리 오류", e);
            throw new BadCredentialsException("Authentication processing failed", e);
        }
        chain.doFilter(request, response);
    }
}
