package com.example.hearu.common.security;

import java.io.IOException;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private SecurityServletErrorCode resolveErrorCode(AuthenticationException authException, HttpServletRequest request) {
        Throwable cause = authException.getCause();
        boolean hasAuthHeader = request.getHeader("Authorization") != null;

        // 1. Spring Security 자체 인증 실패 (cause == null)
        if (cause == null) {

            // 1-1) 잘못된 인증 정보 (아이디/비밀번호, 토큰 인증 실패 등)
            if (authException instanceof BadCredentialsException) {
                log.warn(
                    "[AUTH FAIL] 잘못된 인증 정보 - method={}, uri={}, message={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    authException.getMessage()
                );
                return SecurityServletErrorCode.UNAUTHORIZED;
            }

            // 1-2) 인증 정보 없이 보호 자원 접근
            if (!hasAuthHeader) {
                log.info(
                    "[AUTH BLOCKED] 인증 정보 없는 요청 차단 - method={}, uri={}, remoteAddr={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    request.getRemoteAddr()
                );
                return SecurityServletErrorCode.UNAUTHORIZED;
            }

            // 1-3) 인증 헤더는 있으나 cause 없이 실패 (비정상 흐름)
            log.warn(
                "[AUTH WARN] 인증 헤더 존재하나 인증 실패 - method={}, uri={}, message={}",
                request.getMethod(),
                request.getRequestURI(),
                authException.getMessage()
            );
            return SecurityServletErrorCode.UNAUTHORIZED;
        }

        // 2) jjwt 관련 예외 매핑
        if (cause instanceof SignatureException) {

            log.warn("Access Token이 잘못된 key로 서명 검증을 시도하였습니다: {}", cause.getMessage());
            return SecurityServletErrorCode.INVALID_JWT_SIGNATURE;
        } else if (cause instanceof MalformedJwtException) {

            log.warn("Access Token이 JWT 구조를 만족하지 않습니다: {}", cause.getMessage());
            return SecurityServletErrorCode.INVALID_JWT_FORMAT;
        } else if (cause instanceof ExpiredJwtException) {

            log.warn("Access Token이 만료되었습니다: {}", cause.getMessage());
            return SecurityServletErrorCode.EXPIRED_JWT_TOKEN;
        } else if (cause instanceof UnsupportedJwtException) {

            log.warn("서버에서 지원하지 않은 방식으로 서명된 JWT 토큰입니다.: {}", cause.getMessage());
            return SecurityServletErrorCode.UNSUPPORTED_JWT;
        } else {
            log.warn("Access Token 인증 과정 중 알 수 없는 서버 오류: {}", cause.getMessage(), cause);
            return SecurityServletErrorCode.INTERNAL_SERVER_ERROR;
        }
    }

    @Override
    public void commence(
        HttpServletRequest request,
        HttpServletResponse response,
        AuthenticationException authException
    ) throws IOException {

        SecurityServletErrorCode errorCode = resolveErrorCode(authException, request);

        SecurityResponseWriter.writeJsonErrorResponse(response, errorCode);
    }
}
