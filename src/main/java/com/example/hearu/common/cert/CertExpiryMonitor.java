package com.example.hearu.common.cert;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.springframework.context.annotation.Profile;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.hearu.common.client.discord.DiscordNotifierClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// nginx TLS 종단은 prod 토폴로지에만 있다. dev(Railway)엔 nginx가 없어 매 cron마다 실패하고 공용
// Discord 웹훅으로 오탐이 쌓이므로 prod에서만 돌린다.
@Component
@Profile("prod")
@RequiredArgsConstructor
@Slf4j
public class CertExpiryMonitor {

    // 알림·로그의 시각은 사람이 보는 값이라 운영 타임존(KST)으로 표기한다. Instant를 그대로 찍으면
    // UTC(...Z)로 나와 cron(09:00 KST)·운영자 기준과 어긋나 오해를 부른다.
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter KST_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(KST);

    private final TlsCertificateInspector tlsCertificateInspector;
    private final DiscordNotifierClient discordNotifierClient;
    private final CertProperties certProperties;

    // 임계치 미만은 예외가 아니라 정상 판독 결과이므로 재시도 대상에 넣지 않고 바로 알린다.
    @Scheduled(cron = "${cert.check.cron}")
    @Retryable(
        retryFor = { CertInspectionException.class },
        maxAttempts = 2, // 최초 1회 + 재시도 1회
        backoff = @Backoff(delay = 1000)
    )
    public void checkCertificateExpiry() {
        Instant notAfter = tlsCertificateInspector.readNotAfter(
            certProperties.getTargetHost(),
            certProperties.getTargetPort(),
            certProperties.getSniHost(),
            certProperties.getTimeout()
        );
        Instant now = Instant.now();
        long daysLeft = CertExpiryJudge.daysLeft(notAfter, now);

        if (!CertExpiryJudge.shouldWarn(notAfter, now, certProperties.getWarnThresholdDays())) {
            // prod의 유일한 앱 로그 레벨이 INFO라 정상 경로도 하트비트로 남긴다. 없으면 "정상"과
            // "스케줄러가 죽어 아예 안 돎"을 구분할 수 없어, 감시 잡 자체가 조용히 실패한다.
            log.info("[Cert][ExpiryCheck] 정상. daysLeft={}, notAfter={}", daysLeft, KST_FORMAT.format(notAfter));
            return;
        }

        // 만료(음수 daysLeft)면 "0일"로 절삭돼 오해를 부르므로 문구를 구분한다.
        boolean expired = daysLeft < 0;
        log.warn("[Cert][ExpiryCheck] {}. daysLeft={}, notAfter={}",
            expired ? "이미 만료됨" : "임계치 미만", daysLeft, KST_FORMAT.format(notAfter));

        // 로그(감사 추적)는 매일 남기되, Discord 핑은 피로 방지를 위해 알림 주기에만 보낸다.
        if (!CertExpiryJudge.isNotifyMilestone(daysLeft)) {
            log.debug("[Cert][ExpiryCheck] 알림 주기 아님 — Discord 생략. daysLeft={}", daysLeft);
            return;
        }

        discordNotifierClient.sendNotification("""
        %s
        • 대상: %s (SNI: %s)
        • 남은 일수: %s
        • 만료 시각: %s
        • 확인 시각: %s
        """.formatted(
                expired ? "[인증서 만료됨]" : "[인증서 만료 임박]",
                certProperties.getTargetHost(),
                certProperties.getSniHost(),
                expired ? "이미 만료됨" : "%d일".formatted(daysLeft),
                KST_FORMAT.format(notAfter),
                KST_FORMAT.format(now)
            )
        );
    }

    @Recover
    public void recover(CertInspectionException e) {
        String causeDetail = formatCause(e.getCause());

        log.error(
            "[Cert][ExpiryCheck] 재시도 1회 후 최종 실패 - reason={}, cause={}",
            e.getMessage(),
            causeDetail,
            e
        );

        discordNotifierClient.sendNotification("""
        [인증서 만료 확인 실패]
        • 작업: CertExpiryCheck
        • 재시도: 1회 후 실패
        • 원인: %s
        • 상세: %s
        • 시각: %s
        """.formatted(
                e.getMessage(),
                causeDetail,
                KST_FORMAT.format(Instant.now())
            )
        );
    }

    private static String formatCause(Throwable cause) {
        if (cause == null) {
            return "원인 불명";
        }
        return "%s: %s".formatted(cause.getClass().getSimpleName(), cause.getMessage());
    }
}
