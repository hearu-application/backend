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
        log.info("[AI][Start] diaryId={}, userId={}", event.diaryId(), event.userId());
        aiResponseCaller.call(event);
    }
}
