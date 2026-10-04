package com.example.hearu.ai.response.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import com.example.hearu.common.client.discord.DiscordNotifierClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// AI 응답이 FAILED로 확정됐을 때의 로그·Discord 알림. 확정 지점이 두 곳(실행 실패, 시작 시 상한 소진)이라 한 곳에 모은다.
@Slf4j
@Component
@RequiredArgsConstructor
public class AiResponseFinalFailureNotifier {

    private final DiscordNotifierClient discordNotifierClient;

    // reason에는 예외 타입 이름처럼 외부 응답·일기 본문이 섞이지 않는 값만 넘긴다.
    public void notify(Long diaryId, Long userId, int attemptCount, String reason) {
        log.error("[AI][FinalFail] 실행 상한 도달로 FAILED 확정. diaryId={}, userId={}, attemptCount={}, reason={}",
            diaryId, userId, attemptCount, reason);
        discordNotifierClient.sendNotification("""
            [AI 응답 최종 실패]
            • diaryId: %d
            • 실행 횟수: %d
            • 원인: %s
            • 시각: %s
            """.formatted(
                diaryId,
                attemptCount,
                reason,
                LocalDateTime.now()
            )
        );
    }
}
