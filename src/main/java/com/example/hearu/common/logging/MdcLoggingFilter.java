package com.example.hearu.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MdcLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startedAt = System.currentTimeMillis();
        try {
            MDC.put("requestId", UUID.randomUUID().toString().substring(0, 8));

            // 이 필터는 HIGHEST_PRECEDENCE라 JwtFilter보다 먼저 실행된다.
            // 따라서 요청 시작 시점에는 userId가 아직 MDC에 없고, 종료 로그에서만 확인된다.
            log.debug("[REQ][Start] {} {}{}",
                    request.getMethod(),
                    request.getRequestURI(),
                    request.getQueryString() == null ? "" : "?" + request.getQueryString());

            filterChain.doFilter(request, response);
        } finally {
            long elapsedMs = System.currentTimeMillis() - startedAt;
            log.debug("[REQ][End] {} {} -> status={}, elapsed={}ms",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    elapsedMs);
            MDC.clear();
        }
    }

    /**
     * 헬스체크는 수 초 간격으로 반복 호출되어 DEBUG 로그를 가득 채우므로 제외한다.
     */
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator");
    }
}
