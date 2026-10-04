package com.example.hearu.ai.response.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.hearu.common.client.discord.DiscordNotifierClient;

@ExtendWith(MockitoExtension.class)
class AiResponseFinalFailureNotifierTest {

    @Mock
    DiscordNotifierClient discordNotifierClient;

    @InjectMocks
    AiResponseFinalFailureNotifier notifier;

    @Test
    @DisplayName("diaryId·실행 횟수·원인을 Discord로 보낸다")
    void sends_discord_notification() {
        notifier.notify(1L, 2L, 3, "ResourceAccessException");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(discordNotifierClient).sendNotification(captor.capture());
        assertThat(captor.getValue())
            .contains("diaryId: 1")
            .contains("실행 횟수: 3")
            .contains("원인: ResourceAccessException");
    }
}
