package com.example.hearu.ai.response.scheduler;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.hearu.ai.response.service.AiResponseService;
import com.example.hearu.common.client.discord.DiscordNotifierClient;

@ExtendWith(MockitoExtension.class)
class AiResponseSweepSchedulerTest {

    @Mock
    AiResponseService aiResponseService;

    @Mock
    DiscordNotifierClient discordNotifierClient;

    AiResponseSweepScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new AiResponseSweepScheduler(aiResponseService, discordNotifierClient, Duration.ofMinutes(10));
    }

    @Test
    @DisplayName("stale-after 이전 시각을 cutoff로 조회하고, 같은 cutoff로 선점한다")
    void uses_stale_after_as_cutoff() {
        given(aiResponseService.findStaleTargetIds(any())).willReturn(List.of(1L));
        given(aiResponseService.claimAndRepublish(anyLong(), any())).willReturn(true);
        LocalDateTime before = LocalDateTime.now().minusMinutes(10);

        scheduler.sweepStalePending();

        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(aiResponseService).findStaleTargetIds(cutoffCaptor.capture());
        LocalDateTime cutoff = cutoffCaptor.getValue();
        assertThat(cutoff).isBetween(before, LocalDateTime.now().minusMinutes(10));
        verify(aiResponseService).claimAndRepublish(1L, cutoff);
    }

    @Test
    @DisplayName("대상을 한 건씩 재발행하고, 모두 성공하거나 선점에 밀리면 알림을 보내지 않는다")
    void republishes_each_target() {
        given(aiResponseService.findStaleTargetIds(any())).willReturn(List.of(1L, 2L));
        given(aiResponseService.claimAndRepublish(eq(1L), any())).willReturn(true);
        given(aiResponseService.claimAndRepublish(eq(2L), any())).willReturn(false);

        scheduler.sweepStalePending();

        verify(aiResponseService).claimAndRepublish(eq(1L), any());
        verify(aiResponseService).claimAndRepublish(eq(2L), any());
        verifyNoInteractions(discordNotifierClient);
    }

    @Test
    @DisplayName("한 건이 실패해도(큐 포화 등) 나머지를 계속 처리하고, 실패가 있으면 Discord 알림을 보낸다")
    void continues_after_failure_and_notifies() {
        given(aiResponseService.findStaleTargetIds(any())).willReturn(List.of(1L, 2L));
        given(aiResponseService.claimAndRepublish(eq(1L), any()))
            .willThrow(new RejectedExecutionException("saturated"));
        given(aiResponseService.claimAndRepublish(eq(2L), any())).willReturn(true);

        scheduler.sweepStalePending();

        verify(aiResponseService).claimAndRepublish(eq(2L), any());
        verify(discordNotifierClient).sendNotification(contains("aiResponseIds: [1]"));
    }

    @Test
    @DisplayName("대상이 없으면 아무것도 하지 않는다")
    void no_targets() {
        given(aiResponseService.findStaleTargetIds(any())).willReturn(List.of());

        scheduler.sweepStalePending();

        verify(aiResponseService, never()).claimAndRepublish(anyLong(), any());
        verifyNoInteractions(discordNotifierClient);
    }
}
