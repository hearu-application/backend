package com.example.hearu.ai.response.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.example.hearu.ai.response.domain.AttemptStartResult;
import com.example.hearu.ai.response.service.AiResponseService.AttemptStart;
import com.example.hearu.diary.event.DiaryAiResponseRequestedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class DiaryAiResponseRequestedEventListener {

    private final AiResponseService aiResponseService;
    private final AiResponseCaller aiResponseCaller;
    private final AiResponseFinalFailureNotifier finalFailureNotifier;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(DiaryAiResponseRequestedEvent event) {
        // 1. 실행 횟수를 시작 시점에 센다. AiResponseCaller.call()은 @Retryable로 다시 불리므로 그 안에서 세면
        //    재시도마다 중복으로 센다. 실행이 실패 기록까지 가지 못하고 끝나도 횟수는 이미 남아 상한이 지켜진다.
        AttemptStart start = aiResponseService.startAttempt(event.diaryId());

        if (start.result() == AttemptStartResult.SKIPPED) {
            // 회수와 원래 실행이 겹쳐 이미 끝난 경우 등. LLM을 다시 부르지 않는다.
            log.debug("이미 종단 상태라 AI 응답 실행을 건너뜀. diaryId={}", event.diaryId());
            return;
        }
        if (start.result() == AttemptStartResult.EXHAUSTED) {
            // 상한만큼 시작했지만 실패 기록 없이 끝난 실행이 있었다(프로세스 종료 등). LLM 없이 FAILED로 확정됐다.
            finalFailureNotifier.notify(
                event.diaryId(), event.userId(), start.attemptCount(), "실행 상한 소진(실패 기록 없이 종료된 실행 포함)");
            return;
        }

        // MdcTaskDecorator가 requestId/userId를 이 비동기 스레드로 전파하므로,
        // 일기 작성 요청부터 AI 응답 완료까지 requestId 하나로 추적할 수 있다.
        log.info("[AI][Start] diaryId={}, userId={}, attemptCount={}, thread={}",
            event.diaryId(), event.userId(), start.attemptCount(), Thread.currentThread().getName());

        // 2. LLM 호출 (실패 처리·재시도는 AiResponseCaller가 맡는다)
        aiResponseCaller.call(event);
    }
}
