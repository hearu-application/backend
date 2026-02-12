package com.example.hearu.auth.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.hearu.auth.service.RefreshTokenService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenCleanupScheduler {

    private final RefreshTokenService refreshTokenService;

    @Scheduled(cron = "${scheduler.refresh-token.cleanup.cron}")
    public void cleanupExpiredRefreshTokens() {
        try {
            log.info("[Scheduler][RefreshTokenCleanup] 시작");
            refreshTokenService.deleteExpiredRefreshTokens();
        } catch (Exception e) {
            log.error(
                "[Scheduler][RefreshTokenCleanup] 비재시도 예외 발생",
                e
            );
        }
    }
}
