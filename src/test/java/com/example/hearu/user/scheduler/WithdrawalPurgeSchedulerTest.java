package com.example.hearu.user.scheduler;

import static org.mockito.BDDMockito.*;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.hearu.common.client.discord.DiscordNotifierClient;
import com.example.hearu.user.domain.policy.WithdrawalPolicy;
import com.example.hearu.user.service.WithdrawalPurgeService;

@ExtendWith(MockitoExtension.class)
class WithdrawalPurgeSchedulerTest {

    @Mock
    WithdrawalPurgeService withdrawalPurgeService;

    @Spy
    WithdrawalPolicy withdrawalPolicy = new WithdrawalPolicy(Duration.ofHours(24));

    @Mock
    DiscordNotifierClient discordNotifierClient;

    @InjectMocks
    WithdrawalPurgeScheduler scheduler;

    @Test
    @DisplayName("대상 유저를 한 명씩 삭제하고, 모두 성공하면 알림을 보내지 않는다")
    void purges_each_target() {
        given(withdrawalPurgeService.findPurgeTargetIds(any())).willReturn(List.of(1L, 2L));
        given(withdrawalPurgeService.purge(anyLong(), any())).willReturn(true);

        scheduler.purgeExpiredWithdrawals();

        verify(withdrawalPurgeService).purge(eq(1L), any());
        verify(withdrawalPurgeService).purge(eq(2L), any());
        verifyNoInteractions(discordNotifierClient);
    }

    @Test
    @DisplayName("한 명이 실패해도 나머지를 계속 처리하고, 실패가 있으면 Discord 알림을 보낸다")
    void continues_after_failure_and_notifies() {
        given(withdrawalPurgeService.findPurgeTargetIds(any())).willReturn(List.of(1L, 2L));
        given(withdrawalPurgeService.purge(eq(1L), any())).willThrow(new IllegalStateException("FK 위반"));
        given(withdrawalPurgeService.purge(eq(2L), any())).willReturn(true);

        scheduler.purgeExpiredWithdrawals();

        verify(withdrawalPurgeService).purge(eq(2L), any());
        verify(discordNotifierClient).sendNotification(contains("userIds: [1]"));
    }

    @Test
    @DisplayName("대상이 없으면 아무것도 하지 않는다")
    void no_targets() {
        given(withdrawalPurgeService.findPurgeTargetIds(any())).willReturn(List.of());

        scheduler.purgeExpiredWithdrawals();

        verify(withdrawalPurgeService, never()).purge(anyLong(), any());
        verifyNoInteractions(discordNotifierClient);
    }
}
