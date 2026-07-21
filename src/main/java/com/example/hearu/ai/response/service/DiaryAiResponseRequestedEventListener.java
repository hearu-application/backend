package com.example.hearu.ai.response.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.example.hearu.diary.event.DiaryAiResponseRequestedEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class DiaryAiResponseRequestedEventListener {

    private final AiResponseCaller aiResponseCaller;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(DiaryAiResponseRequestedEvent event) {
        // MdcTaskDecorator가 requestId/userId를 이 비동기 스레드로 전파하므로,
        // 일기 작성 요청부터 AI 응답 완료까지 requestId 하나로 추적할 수 있다.
        log.info("[AI][Start] diaryId={}, userId={}, thread={}",
            event.diaryId(), event.userId(), Thread.currentThread().getName());

        aiResponseCaller.call(event);
    }
}
