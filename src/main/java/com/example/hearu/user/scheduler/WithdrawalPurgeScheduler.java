package com.example.hearu.user.scheduler;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.hearu.common.client.discord.DiscordNotifierClient;
import com.example.hearu.user.domain.policy.WithdrawalPolicy;
import com.example.hearu.user.service.WithdrawalPurgeService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class WithdrawalPurgeScheduler {

    private final WithdrawalPurgeService withdrawalPurgeService;
    private final WithdrawalPolicy withdrawalPolicy;
    private final DiscordNotifierClient discordNotifierClient;

    // 실패한 유저는 대상 조건을 그대로 만족해 다음 실행에서 다시 시도되므로 @Retryable을 두지 않는다.
    @Scheduled(cron = "${scheduler.withdrawal.purge.cron}")
    public void purgeExpiredWithdrawals() {
        long startedAt = System.currentTimeMillis();
        LocalDateTime cutoff = withdrawalPolicy.graceCutoff(LocalDateTime.now());
        List<Long> targetIds = withdrawalPurgeService.findPurgeTargetIds(cutoff);

        int purged = 0;
        int skipped = 0;
        List<Long> failedIds = new ArrayList<>();
        for (Long userId : targetIds) {
            try {
                if (withdrawalPurgeService.purge(userId, cutoff)) {
                    purged++;
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                log.error("[Scheduler][WithdrawalPurge] 하드 삭제 실패. userId={}", userId, e);
                failedIds.add(userId);
            }
        }

        log.info("[Scheduler][WithdrawalPurge] 완료. targets={}, purged={}, skipped={}, failed={}, elapsed={}ms",
            targetIds.size(), purged, skipped, failedIds.size(), System.currentTimeMillis() - startedAt);

        if (!failedIds.isEmpty()) {
            discordNotifierClient.sendNotification("""
            [탈퇴 유저 하드 삭제 실패]
            • 작업: WithdrawalPurge
            • 실패: %d명 (다음 실행에서 재시도)
            • userIds: %s
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
