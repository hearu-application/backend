package com.example.hearu.common.cert;

import static org.mockito.BDDMockito.*;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.hearu.common.client.discord.DiscordNotifierClient;

@ExtendWith(MockitoExtension.class)
class CertExpiryMonitorTest {

    @Mock
    TlsCertificateInspector tlsCertificateInspector;

    @Mock
    DiscordNotifierClient discordNotifierClient;

    CertExpiryMonitor monitor;

    @BeforeEach
    void setUp() {
        CertProperties certProperties = new CertProperties();
        certProperties.setTargetHost("nginx");
        certProperties.setTargetPort(443);
        certProperties.setSniHost("hearu.p-e.kr");
        certProperties.setWarnThresholdDays(21);
        certProperties.setTimeout(Duration.ofSeconds(3));

        monitor = new CertExpiryMonitor(tlsCertificateInspector, discordNotifierClient, certProperties);
    }

    @Test
    @DisplayName("남은 일수가 임계치 이상이면 알림을 보내지 않는다")
    void doesNotNotifyWhenHealthy() {
        given(tlsCertificateInspector.readNotAfter(anyString(), anyInt(), anyString(), any()))
            .willReturn(Instant.now().plus(30, ChronoUnit.DAYS));

        monitor.checkCertificateExpiry();

        verifyNoInteractions(discordNotifierClient);
    }

    @Test
    @DisplayName("임계치 미만이고 알림 주기(마지막 주)에 해당하면 Discord로 알린다")
    void notifiesWhenBelowThresholdOnMilestone() {
        given(tlsCertificateInspector.readNotAfter(anyString(), anyInt(), anyString(), any()))
            .willReturn(Instant.now().plus(5, ChronoUnit.DAYS));

        monitor.checkCertificateExpiry();

        verify(discordNotifierClient).sendNotification(anyString());
    }

    @Test
    @DisplayName("임계치 미만이어도 알림 주기가 아니면 Discord 알림을 생략한다")
    void skipsNotificationWhenBelowThresholdButNotMilestone() {
        // 13일: 임계치(21) 미만이나 마지막 주(<=7)도 알림 간격(5의 배수)도 아니라 생략된다.
        // plusHours(2)로 여유를 줘 now 드리프트로 toDays()가 12로 절삭되는 것을 막는다.
        given(tlsCertificateInspector.readNotAfter(anyString(), anyInt(), anyString(), any()))
            .willReturn(Instant.now().plus(13, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS));

        monitor.checkCertificateExpiry();

        verifyNoInteractions(discordNotifierClient);
    }

    @Test
    @DisplayName("이미 만료된 인증서는 매일 알린다")
    void notifiesWhenExpired() {
        given(tlsCertificateInspector.readNotAfter(anyString(), anyInt(), anyString(), any()))
            .willReturn(Instant.now().minus(2, ChronoUnit.DAYS));

        monitor.checkCertificateExpiry();

        verify(discordNotifierClient).sendNotification(anyString());
    }

    @Test
    @DisplayName("재시도 소진(recover) 시 Discord 알림을 발송한다")
    void recoverSendsDiscordNotification() {
        CertInspectionException exception = new CertInspectionException("연결 실패", new RuntimeException());

        monitor.recover(exception);

        verify(discordNotifierClient).sendNotification(anyString());
    }
}
