package com.example.hearu.common.cert;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("CertExpiryJudge")
class CertExpiryJudgeTest {

    private final Instant now = Instant.parse("2026-09-16T00:00:00Z");

    @Nested
    @DisplayName("daysLeft")
    class DaysLeft {

        @Test
        @DisplayName("만료까지 남은 일수를 계산한다")
        void calculatesRemainingDays() {
            Instant notAfter = now.plus(30, ChronoUnit.DAYS);

            assertThat(CertExpiryJudge.daysLeft(notAfter, now)).isEqualTo(30);
        }
    }

    @Nested
    @DisplayName("shouldWarn")
    class ShouldWarn {

        @Test
        @DisplayName("남은 일수가 임계치 이상이면 경고하지 않는다")
        void doesNotWarnWhenAboveThreshold() {
            Instant notAfter = now.plus(21, ChronoUnit.DAYS);

            assertThat(CertExpiryJudge.shouldWarn(notAfter, now, 21)).isFalse();
        }

        @Test
        @DisplayName("남은 일수가 임계치 미만이면 경고한다")
        void warnsWhenBelowThreshold() {
            Instant notAfter = now.plus(20, ChronoUnit.DAYS);

            assertThat(CertExpiryJudge.shouldWarn(notAfter, now, 21)).isTrue();
        }

        @Test
        @DisplayName("이미 만료됐으면 경고한다")
        void warnsWhenAlreadyExpired() {
            Instant notAfter = now.minus(1, ChronoUnit.DAYS);

            assertThat(CertExpiryJudge.shouldWarn(notAfter, now, 21)).isTrue();
        }
    }

    @Nested
    @DisplayName("isNotifyMilestone")
    class IsNotifyMilestone {

        @Test
        @DisplayName("마지막 주(7일 이하)는 매일 알림 주기다")
        void notifiesDailyInFinalWeek() {
            assertThat(CertExpiryJudge.isNotifyMilestone(7)).isTrue();
            assertThat(CertExpiryJudge.isNotifyMilestone(1)).isTrue();
        }

        @Test
        @DisplayName("이미 만료된(음수) 경우도 알림 주기다")
        void notifiesWhenExpired() {
            assertThat(CertExpiryJudge.isNotifyMilestone(-3)).isTrue();
        }

        @Test
        @DisplayName("여유가 있을 땐 5일 간격에만 알림 주기다")
        void notifiesEveryFiveDaysWhenAmpleTime() {
            assertThat(CertExpiryJudge.isNotifyMilestone(10)).isTrue();  // 5의 배수
            assertThat(CertExpiryJudge.isNotifyMilestone(15)).isTrue();  // 5의 배수
            assertThat(CertExpiryJudge.isNotifyMilestone(9)).isFalse();  // 간격 아님
            assertThat(CertExpiryJudge.isNotifyMilestone(13)).isFalse(); // 간격 아님
        }
    }
}
