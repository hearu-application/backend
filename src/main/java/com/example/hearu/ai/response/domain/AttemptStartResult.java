package com.example.hearu.ai.response.domain;

// AI 응답 실행을 시작하기 직전의 판정 결과
public enum AttemptStartResult {
    // 실행 횟수를 올렸고 LLM을 호출해도 된다
    STARTED,
    // 이미 종단 상태(COMPLETED·FAILED)라 실행하지 않는다
    SKIPPED,
    // 실행 상한을 다 써서 LLM을 부르지 않고 FAILED로 확정했다
    EXHAUSTED
}
