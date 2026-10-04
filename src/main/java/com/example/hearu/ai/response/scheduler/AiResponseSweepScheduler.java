package com.example.hearu.ai.response.scheduler;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.hearu.ai.response.service.AiResponseService;
import com.example.hearu.common.client.discord.DiscordNotifierClient;

import lombok.extern.slf4j.Slf4j;

// 고착된 PENDING AI 응답을 회수해 다시 실행한다. 실패한 실행(PENDING 유지)과
// 큐 포화·강제 종료로 유실된 작업이 모두 여기로 회수된다 (docs/plan/ai-response-stuck-sweep.md).
@Slf4j
@Component
public class AiResponseSweepScheduler {

    private final AiResponseService aiResponseService;
    private final DiscordNotifierClient discordNotifierClient;
    private final Duration staleAfter;

    public AiResponseSweepScheduler(
        AiResponseService aiResponseService,
        DiscordNotifierClient discordNotifierClient,
        @Value("${scheduler.ai-response.sweep.stale-after}") Duration staleAfter
    ) {
        this.aiResponseService = aiResponseService;
        this.discordNotifierClient = discordNotifierClient;
        this.staleAfter = staleAfter;
    }

    // 실패한 건은 대상 조건을 그대로 만족해 다음 실행에서 다시 회수되므로 @Retryable을 두지 않는다.
    @Scheduled(cron = "${scheduler.ai-response.sweep.cron}")
    public void sweepStalePending() {
        long startedAt = System.currentTimeMillis();
        LocalDateTime cutoff = LocalDateTime.now().minus(staleAfter);
        List<Long> targetIds = aiResponseService.findStaleTargetIds(cutoff);

        int republished = 0;
        int skipped = 0;
        List<Long> failedIds = new ArrayList<>();
        for (Long aiResponseId : targetIds) {
            try {
                if (aiResponseService.claimAndRepublish(aiResponseId, cutoff)) {
                    republished++;
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                // 워커 큐 포화(RejectedExecutionException)도 여기로 온다. 행은 PENDING으로 남아 다음 실행에서 다시 회수된다.
                log.error("[Scheduler][AiResponseSweep] 회수 실패. aiResponseId={}", aiResponseId, e);
                failedIds.add(aiResponseId);
            }
        }

        log.info("[Scheduler][AiResponseSweep] 완료. targets={}, republished={}, skipped={}, failed={}, elapsed={}ms",
            targetIds.size(), republished, skipped, failedIds.size(), System.currentTimeMillis() - startedAt);

        if (!failedIds.isEmpty()) {
            discordNotifierClient.sendNotification("""
            [AI 응답 회수 실패]
            • 작업: AiResponseSweep
            • 실패: %d건 (다음 실행에서 재시도)
            • aiResponseIds: %s
            • 시각: %s
            """.formatted(
                    failedIds.size(),
                    failedIds,
                    LocalDateTime.now()
                )
            );
        }
    }
}
