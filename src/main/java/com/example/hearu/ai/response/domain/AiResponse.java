package com.example.hearu.ai.response.domain;

import com.example.hearu.common.encrypt.ContentCryptoConverter;
import com.example.hearu.common.entity.BaseEntity;
import com.example.hearu.diary.domain.Diary;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "ai_response")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiResponse extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ai_response_id")
    private Long aiResponseId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "diary_id", nullable = false)
    private Diary diary;

    @Convert(converter = ContentCryptoConverter.class)
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_response_status_type", nullable = false)
    private AiResponseStatusType aiResponseStatusType;

    // 실행 상한 판정용 카운터. 실패가 아니라 시작 시점에 센다 — 실행이 실패 기록까지 가지 못하고
    // 끝나도(프로세스 종료 등) 횟수가 남아야 LLM 호출 상한이 지켜진다.
    // 누적 실행 횟수가 아니다: 수동 재요청(retryResponse)이 "남은 실행 1회" 상태로 되돌리므로,
    // 재요청이 있었던 일기는 실제 누적 실행 수보다 작게 보인다.
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    private AiResponse(Diary diary) {
        this.diary = diary;
        this.aiResponseStatusType = AiResponseStatusType.PENDING;
    }

    public static AiResponse create(Diary diary) {
        return new AiResponse(diary);
    }

    public void completeResponse(String content) {
        this.aiResponseStatusType = AiResponseStatusType.COMPLETED;
        this.content = content;
    }

    // 실행을 시작하기 직전에 호출한다. 상한 안이면 횟수를 올리고 STARTED,
    // 상한을 다 썼으면 LLM을 부르지 않고 FAILED로 확정한다.
    public AttemptStartResult startAttempt(int maxExecutions) {
        if (this.aiResponseStatusType != AiResponseStatusType.PENDING) {
            return AttemptStartResult.SKIPPED;
        }
        if (this.attemptCount >= maxExecutions) {
            this.aiResponseStatusType = AiResponseStatusType.FAILED;
            return AttemptStartResult.EXHAUSTED;
        }
        this.attemptCount++;
        return AttemptStartResult.STARTED;
    }

    // 실행이 실패했을 때 호출한다. 횟수는 시작 시점에 이미 셌으므로 여기서는 상한 도달 여부만 판정한다.
    // 상한이면 FAILED로 확정하고 true. 늦게 도착한 실패가 완료된 응답을 덮지 않도록 COMPLETED면 무시한다.
    public boolean recordFailure(int maxExecutions) {
        if (isCompleted()) {
            return false;
        }
        if (this.attemptCount >= maxExecutions) {
            this.aiResponseStatusType = AiResponseStatusType.FAILED;
            return true;
        }
        return false;
    }

    // 재요청 시 직전 시도의 FAILED 상태를 지운다. 이걸 하지 않으면 클라이언트 폴링이
    // 재시도 직후에도 계속 FAILED를 읽어 실패로 확정한다.
    // 수동 재요청은 실행 1회만 허용한다 — 횟수를 상한 바로 아래로 맞춰, 실패하면 곧바로 FAILED가 되게 한다.
    public void retryResponse(int maxExecutions) {
        this.aiResponseStatusType = AiResponseStatusType.PENDING;
        this.attemptCount = maxExecutions - 1;
    }

    public boolean isCompleted() {
        return this.aiResponseStatusType == AiResponseStatusType.COMPLETED;
    }

    public boolean isFailed() {
        return this.aiResponseStatusType == AiResponseStatusType.FAILED;
    }
}
