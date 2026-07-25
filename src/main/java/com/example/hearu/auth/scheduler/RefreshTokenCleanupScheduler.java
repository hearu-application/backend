package com.example.hearu.auth.scheduler;

import java.time.LocalDateTime;

import org.springframework.dao.DataAccessException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.hearu.auth.service.RefreshTokenService;
import com.example.hearu.common.client.slack.SlackNotifierClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenCleanupScheduler {

    private final RefreshTokenService refreshTokenService;
    private final SlackNotifierClient slackNotifierClient;

    // 재시도(@Retryable)를 트랜잭션 바깥(비트랜잭션 스케줄러)에 두고, 실제 DB 작업은 별개 빈인
    // RefreshTokenService의 @Transactional 메서드에 위임한다. 이렇게 하면 재시도할 때마다 새
    // 트랜잭션이 열려, 롤백된 트랜잭션 안에서 재시도가 도는 문제가 없다. (AiResponseCaller와 동일 패턴)
    // DataAccessException 외의 예외는 재시도 대상이 아니며, Spring 스케줄러의 에러 핸들러가 ERROR로 남긴다.
    @Scheduled(cron = "${scheduler.refresh-token.cleanup.cron}")
    @Retryable(
        retryFor = { DataAccessException.class },
        maxAttempts = 2, // 최초 1회 + 재시도 1회
        backoff = @Backoff(delay = 1000)
    )
    public void cleanupExpiredRefreshTokens() {
        long startedAt = System.currentTimeMillis();
        log.info("[Scheduler][RefreshTokenCleanup] 시작");
        refreshTokenService.deleteExpiredRefreshTokens();
        log.info("[Scheduler][RefreshTokenCleanup] 완료. elapsed={}ms",
            System.currentTimeMillis() - startedAt);
    }

    // DataAccessException으로 재시도까지 소진한 뒤의 최종 복구 경로.
    @Recover
    public void recover(DataAccessException e) {
        log.error(
            "[Scheduler][RefreshTokenCleanup] 재시도 1회 후 최종 실패 - reason={}",
            e.getMessage(),
            e
        );

        slackNotifierClient.sendNotification("""
        [Refresh token 정리 스케줄 실패]
        • 작업: RefreshTokenCleanup
        • 재시도: 1회 후 실패
        • 원인: %s
        • 시각: %s
        """.formatted(
                e.getMessage(),
                LocalDateTime.now()
            )
        );
    }
}
