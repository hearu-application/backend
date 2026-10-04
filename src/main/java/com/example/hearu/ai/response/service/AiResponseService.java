package com.example.hearu.ai.response.service;

import java.time.LocalDateTime;
import java.util.List;

import com.example.hearu.ai.response.domain.AiResponse;
import com.example.hearu.ai.response.domain.AiResponseErrorCode;
import com.example.hearu.ai.response.domain.AiResponseStatusType;
import com.example.hearu.ai.response.domain.AttemptStartResult;
import com.example.hearu.ai.response.dto.response.AiResponseResponse;
import com.example.hearu.ai.response.infrastructure.repository.AiResponseRepository;
import com.example.hearu.diary.domain.Diary;
import com.example.hearu.diary.event.DiaryAiResponseRequestedEvent;
import com.example.hearu.user.domain.User;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.hearu.common.logging.LogMasker;
import com.example.hearu.common.util.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class AiResponseService {

    // 일기 1건당 실행 상한(최초 1번 + 회수 2번). 실행 1번은 @Retryable로 LLM을 최대 2번 호출한다.
    // 시작 시점에 세므로 실행이 어떻게 끝나든 이 상한을 넘겨 LLM을 부르지 않는다(수동 재요청 제외).
    static final int MAX_EXECUTIONS = 3;

    // 회수 스케줄러 1회 실행에서 다시 실행할 최대 건수
    private static final int SWEEP_BATCH_SIZE = 100;

    private final AiResponseRepository aiResponseRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    // 일기 생성 시 PENDING 상태의 AI 응답을 함께 만든다. (과거에는 Diary의 cascade로 생성했으나,
    // 역방향 @OneToOne 매핑을 제거하면서 생성 책임을 이쪽으로 옮겼다)
    public void createPending(Diary diary) {
        aiResponseRepository.save(AiResponse.create(diary));
        log.debug("AI 응답(PENDING) 생성. diaryId={}", diary.getDiaryId());
    }

    // 일기 soft delete에 맞춰 AI 응답도 soft delete한다. AI 응답이 없어도 삭제 자체는 성공해야 하므로
    // 존재하지 않으면 예외를 던지지 않고 건너뛴다.
    public void softDeleteByDiaryId(Long diaryId) {
        aiResponseRepository.findByDiary_DiaryIdAndDeletedAtIsNull(diaryId)
            .ifPresentOrElse(
                AiResponse::softDelete,
                () -> log.debug("삭제할 AI 응답이 없어 건너뜀. diaryId={}", diaryId)
            );
    }

    public int hardDeleteAllByUserId(Long userId) {
        return aiResponseRepository.deleteAllByUserId(userId);
    }

    @Transactional(readOnly = true)
    public AiResponseResponse getAiResponse(Long userId, Long diaryId) {
        AiResponse aiResponse = getAiResponseOrThrow(diaryId);
        aiResponse.getDiary().validateOwner(userId);

        // 응답이 아직 PENDING인지 FAILED인지가 클라이언트 폴링 디버깅의 핵심 정보다.
        log.debug("AI 응답 조회. diaryId={}, status={}, content={}",
            diaryId, aiResponse.getAiResponseStatusType(), LogMasker.textLength(aiResponse.getContent()));

        return new AiResponseResponse(aiResponse.getContent(), aiResponse.getAiResponseStatusType());
    }

    @Transactional(readOnly = true)
    public AiResponse getOwnedAiResponse(Long userId, Long diaryId) {
        AiResponse aiResponse = getAiResponseOrThrow(diaryId);
        aiResponse.getDiary().validateOwner(userId);
        return aiResponse;
    }

    // 호출부(AiResponseCaller)가 [AI][Complete] / 실패 사유를 이미 기록하므로 여기서는 로깅하지 않는다.
    public void markCompletedAndSaveResponse(Long diaryId, String content) {
        AiResponse aiResponse = getAiResponseOrThrow(diaryId);
        aiResponse.completeResponse(content);
    }

    // AI 응답 재요청(구버전 앱의 재시도 버튼). FAILED일 때만 PENDING으로 되돌리고 true를 반환한다.
    // COMPLETED는 기존 응답 보존, PENDING은 회수 스케줄러가 책임지므로 아무것도 하지 않는다(멱등).
    public boolean requestRetryIfFailed(Long diaryId) {
        AiResponse aiResponse = getAiResponseOrThrow(diaryId);

        if (!aiResponse.isFailed()) {
            log.debug("FAILED가 아니라 AI 응답 재요청을 무시. diaryId={}, status={}",
                diaryId, aiResponse.getAiResponseStatusType());
            return false;
        }

        log.debug("AI 응답 재요청으로 상태를 PENDING으로 초기화. diaryId={}, 이전 attemptCount={}",
            diaryId, aiResponse.getAttemptCount());
        aiResponse.retryResponse(MAX_EXECUTIONS);
        return true;
    }

    // 실행 직전에 시작 횟수를 센다(리스너가 호출). 상한을 다 썼으면 LLM 없이 FAILED로 확정된다.
    // 로그·알림은 호출부가 남긴다.
    public AttemptStart startAttempt(Long diaryId) {
        AiResponse aiResponse = getAiResponseOrThrow(diaryId);
        AttemptStartResult result = aiResponse.startAttempt(MAX_EXECUTIONS);
        return new AttemptStart(result, aiResponse.getAttemptCount());
    }

    // 실행 실패를 기록한다. 상한에 닿았으면 FAILED로 확정된다. 로그·알림은 호출부(AiResponseCaller)가 남긴다.
    public FailedAttempt recordFailure(Long diaryId) {
        AiResponse aiResponse = getAiResponseOrThrow(diaryId);
        boolean finalFailure = aiResponse.recordFailure(MAX_EXECUTIONS);
        return new FailedAttempt(aiResponse.getAttemptCount(), finalFailure);
    }

    @Transactional(readOnly = true)
    public List<Long> findStaleTargetIds(LocalDateTime cutoff) {
        return aiResponseRepository.findStaleIds(
            AiResponseStatusType.PENDING, cutoff, PageRequest.of(0, SWEEP_BATCH_SIZE));
    }

    // 고착된 PENDING을 선점하고 AI 응답 이벤트를 다시 발행한다. 선점에 실패하면(이미 완료·삭제·갱신됨) false.
    // 이벤트는 AFTER_COMMIT + @Async로 처리되므로 스케줄러 스레드는 LLM 호출을 기다리지 않는다.
    public boolean claimAndRepublish(Long aiResponseId, LocalDateTime cutoff) {
        // 1. 조건부 UPDATE로 선점 (updatedAt을 갱신해 다음 회수 대상에서 빠지게 한다)
        int claimed = aiResponseRepository.claimStale(
            aiResponseId, AiResponseStatusType.PENDING, cutoff, LocalDateTime.now());
        if (claimed == 0) {
            log.debug("회수 대상 선점 실패로 건너뜀. aiResponseId={}", aiResponseId);
            return false;
        }

        // 2. 이벤트에 실을 값을 엔티티에서 구성한다 (ai → diary/user 서비스 의존 없이 엔티티만 탐색)
        AiResponse aiResponse = aiResponseRepository.findById(aiResponseId)
            .orElseThrow(() -> {
                log.warn("선점한 AI 응답이 존재하지 않습니다. aiResponseId={}", aiResponseId);
                return new BusinessException(AiResponseErrorCode.AI_RESPONSE_NOT_FOUND);
            });
        Diary diary = aiResponse.getDiary();
        User user = diary.getUser();

        // 3. 이벤트 발행
        log.info("[AI][EventPublished] diaryId={}, userId={}, trigger=sweep, attemptCount={}",
            diary.getDiaryId(), user.getUserId(), aiResponse.getAttemptCount());
        applicationEventPublisher.publishEvent(DiaryAiResponseRequestedEvent.from(diary, user));
        return true;
    }

    private AiResponse getAiResponseOrThrow(Long diaryId) {
        return aiResponseRepository.findByDiary_DiaryIdAndDeletedAtIsNull(diaryId)
            .orElseThrow(() -> {
                log.warn("AI 응답 데이터가 존재하지 않습니다. diaryId={}", diaryId);
                return new BusinessException(AiResponseErrorCode.AI_RESPONSE_NOT_FOUND);
            });
    }

    // attemptCount: 이번 실행까지 포함한 시작 횟수
    public record AttemptStart(AttemptStartResult result, int attemptCount) {
    }

    // attemptCount: 실패한 실행까지 포함한 시작 횟수, finalFailure: 이번 실패로 FAILED가 확정됐는지
    public record FailedAttempt(int attemptCount, boolean finalFailure) {
    }
}
