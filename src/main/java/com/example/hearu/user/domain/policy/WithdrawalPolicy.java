package com.example.hearu.user.domain.policy;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 회원 탈퇴 유예 규칙. 탈퇴 시각(deletedAt)이 cutoff 이상이면 유예 중(복구 가능),
 * cutoff 미만이면 하드 삭제 대상이다. 두 판정이 같은 cutoff를 반열린 구간으로 나눠 겹치지 않는다.
 */
@Component
public class WithdrawalPolicy {

    private final Duration gracePeriod;

    public WithdrawalPolicy(@Value("${user.withdrawal.grace-period}") Duration gracePeriod) {
        this.gracePeriod = gracePeriod;
    }

    public LocalDateTime graceCutoff(LocalDateTime now) {
        return now.minus(gracePeriod);
    }

    public LocalDateTime purgeAt(LocalDateTime deletedAt) {
        return deletedAt.plus(gracePeriod);
    }
}
