package com.example.hearu.ai.response.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import com.example.hearu.ai.response.domain.PromptBuilder;
import com.example.hearu.ai.response.infrastructure.client.NaverClovaClient;
import com.example.hearu.ai.response.infrastructure.client.dto.ClovaChatResponse;
import com.example.hearu.ai.response.infrastructure.client.dto.Message;
import com.example.hearu.diary.domain.EmotionType;
import com.example.hearu.diary.event.DiaryAiResponseRequestedEvent;
import com.example.hearu.user.domain.ToneType;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;

@ExtendWith(MockitoExtension.class)
public class AiResponseCallerTest {

    @Mock
    AiResponseService aiResponseService;

    @Mock
    NaverClovaClient naverClovaClient;

    @Captor
    ArgumentCaptor<List<Message>> messagesCaptor;

    AiResponseCaller aiResponseCaller;

    private final DiaryAiResponseRequestedEvent event = eventWith(ToneType.HONORIFIC);

    private static DiaryAiResponseRequestedEvent eventWith(ToneType toneType) {
        return new DiaryAiResponseRequestedEvent(
            1L, "오늘은 좋은 하루였다", EmotionType.JOY, 1L, "용준", toneType);
    }

    @BeforeEach
    void setUp() {
        aiResponseCaller = new AiResponseCaller(
            aiResponseService,
            naverClovaClient,
            new PromptBuilder(),
            new ObjectMapper()
        );
    }

    private ClovaChatResponse clovaResponse(String content) {
        return new ClovaChatResponse(
            new ClovaChatResponse.Status("20000", "OK"),
            new ClovaChatResponse.Result(new Message("assistant", content), 10, 20, "stop_before", 1L)
        );
    }

    private HttpClientErrorException clientError(HttpStatus status) {
        return HttpClientErrorException.create(
            status,
            status.getReasonPhrase(),
            HttpHeaders.EMPTY,
            new byte[0],
            StandardCharsets.UTF_8
        );
    }

    @Nested
    @DisplayName("프롬프트 전달")
    class PromptWiring {

        // 이 두 테스트가 없으면 systemPromptBuild에 말투를 하드코딩해도 전체 테스트가 통과한다.
        // "설정이 프롬프트까지 도달하지 않는다"가 실제로 났던 버그이므로 그 구간을 여기서 잠근다.
        @Test
        @DisplayName("이벤트가 존댓말이면 시스템 프롬프트에 존댓말 규칙이 실린다")
        void honorific_event_builds_honorific_system_prompt() {
            String systemPrompt = systemPromptSentFor(ToneType.HONORIFIC);

            assertThat(systemPrompt).contains("모든 문장을 존댓말로 끝낸다");
            assertThat(systemPrompt).doesNotContain("문장 끝 모음 늘이기");
        }

        @Test
        @DisplayName("이벤트가 반말이면 시스템 프롬프트에 반말 규칙이 실린다")
        void informal_event_builds_informal_system_prompt() {
            String systemPrompt = systemPromptSentFor(ToneType.INFORMAL);

            assertThat(systemPrompt).contains("문장 끝 모음 늘이기");
            assertThat(systemPrompt).doesNotContain("모든 문장을 존댓말로 끝낸다");
        }

        @Test
        @DisplayName("이벤트의 닉네임이 시스템 프롬프트에 주입된다")
        void nickname_is_injected_into_system_prompt() {
            assertThat(systemPromptSentFor(ToneType.HONORIFIC)).contains("사용자 이름: 용준");
        }

        // LLM에 실제로 전달된 system 메시지를 꺼낸다.
        private String systemPromptSentFor(ToneType toneType) {
            given(naverClovaClient.getAiResponse(anyList()))
                .willReturn(clovaResponse("{\"response\":\"응답\"}"));

            aiResponseCaller.call(eventWith(toneType));

            verify(naverClovaClient).getAiResponse(messagesCaptor.capture());
            return messagesCaptor.getValue().stream()
                .filter(message -> "system".equals(message.role()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("system 메시지가 전달되지 않았습니다"))
                .content();
        }
    }

    @Nested
    @DisplayName("재시도 대상 예외")
    class Retryable {

        @Test
        @DisplayName("429는 다시 던져 @Retryable에 맡기고 FAILED로 확정하지 않는다")
        void too_many_requests_is_rethrown() {
            given(naverClovaClient.getAiResponse(anyList()))
                .willThrow(clientError(HttpStatus.TOO_MANY_REQUESTS));

            assertThatThrownBy(() -> aiResponseCaller.call(event))
                .isInstanceOf(HttpClientErrorException.TooManyRequests.class);

            verify(aiResponseService, never()).markFailed(anyLong());
        }

        @Test
        @DisplayName("5xx는 다시 던진다")
        void server_error_is_rethrown() {
            given(naverClovaClient.getAiResponse(anyList()))
                .willThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

            assertThatThrownBy(() -> aiResponseCaller.call(event))
                .isInstanceOf(HttpServerErrorException.class);

            verify(aiResponseService, never()).markFailed(anyLong());
        }

        @Test
        @DisplayName("네트워크 오류는 다시 던진다")
        void network_error_is_rethrown() {
            given(naverClovaClient.getAiResponse(anyList()))
                .willThrow(new ResourceAccessException("timeout"));

            assertThatThrownBy(() -> aiResponseCaller.call(event))
                .isInstanceOf(ResourceAccessException.class);

            verify(aiResponseService, never()).markFailed(anyLong());
        }
    }

    @Nested
    @DisplayName("재시도 대상이 아닌 예외")
    class NonRetryable {

        @Test
        @DisplayName("400은 즉시 FAILED로 확정한다")
        void bad_request_marks_failed() {
            given(naverClovaClient.getAiResponse(anyList()))
                .willThrow(clientError(HttpStatus.BAD_REQUEST));

            aiResponseCaller.call(event);

            verify(aiResponseService).markFailed(1L);
        }

        @Test
        @DisplayName("401은 즉시 FAILED로 확정한다")
        void unauthorized_marks_failed() {
            given(naverClovaClient.getAiResponse(anyList()))
                .willThrow(clientError(HttpStatus.UNAUTHORIZED));

            aiResponseCaller.call(event);

            verify(aiResponseService).markFailed(1L);
        }

        @Test
        @DisplayName("응답에 response 필드가 없으면 FAILED로 확정한다")
        void missing_response_field_marks_failed() {
            given(naverClovaClient.getAiResponse(anyList()))
                .willReturn(clovaResponse("{\"message\":\"필드 없음\"}"));

            aiResponseCaller.call(event);

            verify(aiResponseService).markFailed(1L);
            verify(aiResponseService, never()).markCompletedAndSaveResponse(anyLong(), anyString());
        }
    }

    @Nested
    @DisplayName("정상 응답")
    class Success {

        @Test
        @DisplayName("response 필드를 파싱해 저장한다")
        void success() {
            given(naverClovaClient.getAiResponse(anyList()))
                .willReturn(clovaResponse("{\"response\":\"좋은 하루였구나아~\"}"));

            aiResponseCaller.call(event);

            verify(aiResponseService).markCompletedAndSaveResponse(1L, "좋은 하루였구나아~");
            verify(aiResponseService, never()).markFailed(anyLong());
        }
    }

    @Nested
    @DisplayName("재시도 소진 시 복구")
    class Recover {

        @Test
        @DisplayName("429 소진 시 FAILED로 확정한다")
        void too_many_requests() {
            aiResponseCaller.recover(
                (HttpClientErrorException.TooManyRequests) clientError(HttpStatus.TOO_MANY_REQUESTS), event);

            verify(aiResponseService).markFailed(1L);
        }

        @Test
        @DisplayName("5xx 소진 시 FAILED로 확정한다")
        void server_error() {
            aiResponseCaller.recover(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR), event);

            verify(aiResponseService).markFailed(1L);
        }

        @Test
        @DisplayName("네트워크 오류 소진 시 FAILED로 확정한다")
        void network_error() {
            aiResponseCaller.recover(new ResourceAccessException("timeout"), event);

            verify(aiResponseService).markFailed(1L);
        }
    }
}
