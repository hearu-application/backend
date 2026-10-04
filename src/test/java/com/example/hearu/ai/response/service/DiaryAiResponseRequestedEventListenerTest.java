package com.example.hearu.ai.response.service;

import static org.mockito.BDDMockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.hearu.ai.response.domain.AttemptStartResult;
import com.example.hearu.ai.response.service.AiResponseService.AttemptStart;
import com.example.hearu.diary.domain.EmotionType;
import com.example.hearu.diary.event.DiaryAiResponseRequestedEvent;
import com.example.hearu.user.domain.ToneType;

@ExtendWith(MockitoExtension.class)
class DiaryAiResponseRequestedEventListenerTest {

    @Mock
    AiResponseService aiResponseService;

    @Mock
    AiResponseCaller aiResponseCaller;

    @Mock
    AiResponseFinalFailureNotifier finalFailureNotifier;

    @InjectMocks
    DiaryAiResponseRequestedEventListener listener;

    private final DiaryAiResponseRequestedEvent event = new DiaryAiResponseRequestedEvent(
        1L, "오늘은 좋은 하루였다", EmotionType.JOY, 2L, "용준", ToneType.HONORIFIC);

    @Test
    @DisplayName("실행 횟수를 먼저 센 뒤 LLM 호출을 시작한다")
    void starts_attempt_then_calls() {
        given(aiResponseService.startAttempt(1L)).willReturn(new AttemptStart(AttemptStartResult.STARTED, 1));

        listener.handle(event);

        InOrder inOrder = Mockito.inOrder(aiResponseService, aiResponseCaller);
        inOrder.verify(aiResponseService).startAttempt(1L);
        inOrder.verify(aiResponseCaller).call(event);
        verifyNoInteractions(finalFailureNotifier);
    }

    @Test
    @DisplayName("이미 종단 상태면 LLM을 호출하지 않는다")
    void skipped_does_not_call() {
        given(aiResponseService.startAttempt(1L)).willReturn(new AttemptStart(AttemptStartResult.SKIPPED, 1));

        listener.handle(event);

        verifyNoInteractions(aiResponseCaller, finalFailureNotifier);
    }

    // 실패 기록 없이 끝난 실행(프로세스 종료 등)이 반복돼도 상한을 넘겨 LLM을 부르지 않는다.
    @Test
    @DisplayName("실행 상한을 다 썼으면 LLM을 호출하지 않고 최종 실패를 알린다")
    void exhausted_notifies_without_calling() {
        given(aiResponseService.startAttempt(1L)).willReturn(new AttemptStart(AttemptStartResult.EXHAUSTED, 3));

        listener.handle(event);

        verifyNoInteractions(aiResponseCaller);
        verify(finalFailureNotifier).notify(eq(1L), eq(2L), eq(3), anyString());
    }
}
