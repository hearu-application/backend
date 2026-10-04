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
import com.example.hearu.ai.response.infrastructure.client.OpenAiClient;
import com.example.hearu.ai.response.infrastructure.client.dto.OpenAiChatResponse;
import com.example.hearu.ai.response.infrastructure.client.dto.Message;
import com.example.hearu.ai.response.service.AiResponseService.FailedAttempt;

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
    OpenAiClient openAiClient;

    @Mock
    AiResponseFinalFailureNotifier finalFailureNotifier;

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
            openAiClient,
            new PromptBuilder(),
            new ObjectMapper(),
            finalFailureNotifier
        );
        // 실패 경로의 기본값: 첫 실패라 PENDING 유지. 최종 실패 케이스는 개별 테스트에서 덮어쓴다.
        lenient().when(aiResponseService.recordFailure(anyLong()))
            .thenReturn(new FailedAttempt(1, false));
    }

    private OpenAiChatResponse openAiResponse(String content) {
        return openAiResponse(content, "stop", 20);
    }

    private OpenAiChatResponse openAiResponse(String content, String finishReason, int completionTokens) {
        return new OpenAiChatResponse(
            List.of(new OpenAiChatResponse.Choice(
                new OpenAiChatResponse.ResponseMessage("assistant", content, null),
                finishReason
            )),
            new OpenAiChatResponse.Usage(10, completionTokens, null)
        );
    }

    private OpenAiChatResponse refusalResponse(String refusal) {
        return new OpenAiChatResponse(
            List.of(new OpenAiChatResponse.Choice(
                new OpenAiChatResponse.ResponseMessage("assistant", null, refusal),
                "stop"
            )),
            new OpenAiChatResponse.Usage(10, 5, null)
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

            assertThat(systemPrompt).contains("친근한 존댓말(해요체)로 통일한다");
            assertThat(systemPrompt).doesNotContain("구어체 모음 연장을 적용한다");
        }

        @Test
        @DisplayName("이벤트가 반말이면 시스템 프롬프트에 반말 규칙이 실린다")
        void informal_event_builds_informal_system_prompt() {
            String systemPrompt = systemPromptSentFor(ToneType.INFORMAL);

            assertThat(systemPrompt).contains("구어체 모음 연장을 적용한다");
            assertThat(systemPrompt).doesNotContain("친근한 존댓말(해요체)로 통일한다");
        }

        @Test
        @DisplayName("이벤트의 닉네임이 시스템 프롬프트에 주입된다")
        void nickname_is_injected_into_system_prompt() {
            assertThat(systemPromptSentFor(ToneType.HONORIFIC)).contains("사용자 이름: 용준");
        }

        @Test
        @DisplayName("일기 원문이 평문 그대로 user 프롬프트에 전달된다")
        void diary_content_reaches_user_prompt_as_plaintext() {
            given(openAiClient.getAiResponse(anyList()))
                .willReturn(openAiResponse("{\"response\":\"응답\"}"));

            aiResponseCaller.call(event);

            verify(openAiClient).getAiResponse(messagesCaptor.capture());
            String userPrompt = messagesCaptor.getValue().stream()
                .filter(message -> "user".equals(message.role()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("user 메시지가 전달되지 않았습니다"))
                .content();

            assertThat(userPrompt).contains(event.content());
        }

        // LLM에 실제로 전달된 system 메시지를 꺼낸다.
        private String systemPromptSentFor(ToneType toneType) {
            given(openAiClient.getAiResponse(anyList()))
                .willReturn(openAiResponse("{\"response\":\"응답\"}"));

            aiResponseCaller.call(eventWith(toneType));

            verify(openAiClient).getAiResponse(messagesCaptor.capture());
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
        @DisplayName("429는 다시 던져 @Retryable에 맡기고 실패를 기록하지 않는다")
        void too_many_requests_is_rethrown() {
            given(openAiClient.getAiResponse(anyList()))
                .willThrow(clientError(HttpStatus.TOO_MANY_REQUESTS));

            assertThatThrownBy(() -> aiResponseCaller.call(event))
                .isInstanceOf(HttpClientErrorException.TooManyRequests.class);

            verify(aiResponseService, never()).recordFailure(anyLong());
        }

        @Test
        @DisplayName("5xx는 다시 던진다")
        void server_error_is_rethrown() {
            given(openAiClient.getAiResponse(anyList()))
                .willThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

            assertThatThrownBy(() -> aiResponseCaller.call(event))
                .isInstanceOf(HttpServerErrorException.class);

            verify(aiResponseService, never()).recordFailure(anyLong());
        }

        @Test
        @DisplayName("네트워크 오류는 다시 던진다")
        void network_error_is_rethrown() {
            given(openAiClient.getAiResponse(anyList()))
                .willThrow(new ResourceAccessException("timeout"));

            assertThatThrownBy(() -> aiResponseCaller.call(event))
                .isInstanceOf(ResourceAccessException.class);

            verify(aiResponseService, never()).recordFailure(anyLong());
        }
    }

    @Nested
    @DisplayName("재시도 대상이 아닌 예외")
    class NonRetryable {

        @Test
        @DisplayName("400은 재시도 없이 실패를 기록한다")
        void bad_request_marks_failed() {
            given(openAiClient.getAiResponse(anyList()))
                .willThrow(clientError(HttpStatus.BAD_REQUEST));

            aiResponseCaller.call(event);

            verify(aiResponseService).recordFailure(1L);
        }

        @Test
        @DisplayName("401은 재시도 없이 실패를 기록한다")
        void unauthorized_marks_failed() {
            given(openAiClient.getAiResponse(anyList()))
                .willThrow(clientError(HttpStatus.UNAUTHORIZED));

            aiResponseCaller.call(event);

            verify(aiResponseService).recordFailure(1L);
        }

        @Test
        @DisplayName("응답에 response 필드가 없으면 실패를 기록한다")
        void missing_response_field_marks_failed() {
            given(openAiClient.getAiResponse(anyList()))
                .willReturn(openAiResponse("{\"message\":\"필드 없음\"}"));

            aiResponseCaller.call(event);

            verify(aiResponseService).recordFailure(1L);
            verify(aiResponseService, never()).markCompletedAndSaveResponse(anyLong(), anyString());
        }

        // 출력이 max_completion_tokens에 걸려 JSON이 닫히기 전에 끝나는 케이스(운영에서 실제로 발생).
        @Test
        @DisplayName("응답 JSON이 잘려 있으면 실패를 기록한다")
        void truncated_json_marks_failed() {
            given(openAiClient.getAiResponse(anyList()))
                .willReturn(openAiResponse("{\"response\":\"킁킁, 몽글몽글 전해진다아", "length", 100));

            aiResponseCaller.call(event);

            verify(aiResponseService).recordFailure(1L);
            verify(aiResponseService, never()).markCompletedAndSaveResponse(anyLong(), anyString());
        }

        // Clova에는 없던 실패 모드. reasoning 모델이 안전 정책 등으로 본문 대신 refusal을 채우는 경우.
        @Test
        @DisplayName("refusal 응답(content=null)은 실패를 기록한다")
        void refusal_marks_failed() {
            given(openAiClient.getAiResponse(anyList()))
                .willReturn(refusalResponse("정책 위반"));

            aiResponseCaller.call(event);

            verify(aiResponseService).recordFailure(1L);
            verify(aiResponseService, never()).markCompletedAndSaveResponse(anyLong(), anyString());
        }
    }

    @Nested
    @DisplayName("정상 응답")
    class Success {

        @Test
        @DisplayName("response 필드를 파싱해 저장한다")
        void success() {
            given(openAiClient.getAiResponse(anyList()))
                .willReturn(openAiResponse("{\"response\":\"좋은 하루였구나아~\"}"));

            aiResponseCaller.call(event);

            verify(aiResponseService).markCompletedAndSaveResponse(1L, "좋은 하루였구나아~");
            verify(aiResponseService, never()).recordFailure(anyLong());
        }
    }

    @Nested
    @DisplayName("재시도 소진 시 복구")
    class Recover {

        @Test
        @DisplayName("429 소진 시 실패를 기록한다")
        void too_many_requests() {
            aiResponseCaller.recover(
                (HttpClientErrorException.TooManyRequests) clientError(HttpStatus.TOO_MANY_REQUESTS), event);

            verify(aiResponseService).recordFailure(1L);
        }

        @Test
        @DisplayName("5xx 소진 시 실패를 기록한다")
        void server_error() {
            aiResponseCaller.recover(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR), event);

            verify(aiResponseService).recordFailure(1L);
        }

        @Test
        @DisplayName("네트워크 오류 소진 시 실패를 기록한다")
        void network_error() {
            aiResponseCaller.recover(new ResourceAccessException("timeout"), event);

            verify(aiResponseService).recordFailure(1L);
        }
    }

    @Nested
    @DisplayName("실패 기록 후 알림")
    class FailureNotification {

        @Test
        @DisplayName("실행 상한 전의 실패는 PENDING을 유지하고 최종 실패 알림을 보내지 않는다")
        void non_final_failure_does_not_notify() {
            given(openAiClient.getAiResponse(anyList()))
                .willThrow(clientError(HttpStatus.BAD_REQUEST));

            aiResponseCaller.call(event);

            verifyNoInteractions(finalFailureNotifier);
        }

        @Test
        @DisplayName("실행 상한에 닿아 FAILED가 확정되면 예외 타입을 원인으로 알린다")
        void final_failure_notifies() {
            given(openAiClient.getAiResponse(anyList()))
                .willThrow(clientError(HttpStatus.BAD_REQUEST));
            given(aiResponseService.recordFailure(1L)).willReturn(new FailedAttempt(3, true));

            aiResponseCaller.call(event);

            verify(finalFailureNotifier).notify(eq(1L), eq(1L), eq(3), contains("BadRequest"));
        }

        @Test
        @DisplayName("재시도 소진(@Recover)으로 FAILED가 확정돼도 알린다")
        void final_failure_on_recover_notifies() {
            given(aiResponseService.recordFailure(1L)).willReturn(new FailedAttempt(3, true));

            aiResponseCaller.recover(new ResourceAccessException("timeout"), event);

            verify(finalFailureNotifier).notify(1L, 1L, 3, "ResourceAccessException");
        }
    }
}
