package com.example.hearu.common.cert;

import java.time.Duration;
import java.time.Instant;

/**
 * TLS 소켓 의존 없이 경계값을 단위 테스트하기 위해 CertExpiryMonitor에서 분리한 순수 로직.
 */
public final class CertExpiryJudge {

    // 임계치 미만인 동안 매일 Discord로 알리면 피로가 커 실제 경고가 묻힌다. 여유가 있을 땐
    // ALERT_INTERVAL_DAYS 간격으로만, 마지막 DAILY_ALERT_WINDOW_DAYS(및 만료 후)엔 매일 알린다.
    // 스케줄러가 하루 1회 도는 것을 전제로 한 무상태 판정이라 상태 저장이 없어 재시작에도 안전하다.
    private static final int DAILY_ALERT_WINDOW_DAYS = 7;
    private static final int ALERT_INTERVAL_DAYS = 5;

    private CertExpiryJudge() {
    }

    public static long daysLeft(Instant notAfter, Instant now) {
        return Duration.between(now, notAfter).toDays();
    }

    public static boolean shouldWarn(Instant notAfter, Instant now, int warnThresholdDays) {
        return daysLeft(notAfter, now) < warnThresholdDays;
    }

    /**
     * 임계치 미만일 때 이번 실행에서 Discord 알림을 보낼 날인지. 만료(음수)는 항상 마지막 주 창에
     * 들어가 매일 알린다. 임계치 자체와 무관하게 남은 일수만으로 판정한다.
     */
    public static boolean isNotifyMilestone(long daysLeft) {
        return daysLeft <= DAILY_ALERT_WINDOW_DAYS || daysLeft % ALERT_INTERVAL_DAYS == 0;
    }
}
