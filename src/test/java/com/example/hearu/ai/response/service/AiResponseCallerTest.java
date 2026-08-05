package com.example.hearu.ai.response.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

@ExtendWith(MockitoExtension.class)
public class AiResponseCallerTest {

    @Mock
    AiResponseService aiResponseService;

    @Mock
    NaverClovaClient naverClovaClient;

    AiResponseCaller aiResponseCaller;

    private final DiaryAiResponseRequestedEvent event =
        new DiaryAiResponseRequestedEvent(1L, "오늘은 좋은 하루였다", EmotionType.JOY, 1L, "용준");

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
