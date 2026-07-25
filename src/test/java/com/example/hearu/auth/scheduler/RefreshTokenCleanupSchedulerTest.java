package com.example.hearu.auth.scheduler;

import static org.mockito.BDDMockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;

import com.example.hearu.auth.service.RefreshTokenService;
import com.example.hearu.common.client.slack.SlackNotifierClient;

@ExtendWith(MockitoExtension.class)
class RefreshTokenCleanupSchedulerTest {

    @Mock
    RefreshTokenService refreshTokenService;

    @Mock
    SlackNotifierClient slackNotifierClient;

    @InjectMocks
    RefreshTokenCleanupScheduler scheduler;

    @Test
    @DisplayName("정상 실행 시 만료 토큰 삭제를 위임한다")
    void delegates_to_service() {
        scheduler.cleanupExpiredRefreshTokens();

        verify(refreshTokenService).deleteExpiredRefreshTokens();
        verifyNoInteractions(slackNotifierClient);
    }

    @Test
    @DisplayName("재시도 소진(recover) 시 슬랙 알림을 발송한다")
    void recover_sends_slack_notification() {
        DataAccessException exception = new DataAccessException("DB 연결 실패") {};

        scheduler.recover(exception);

        verify(slackNotifierClient).sendNotification(anyString());
    }
}
