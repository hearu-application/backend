package com.example.hearu.ai.response.service;

import java.util.List;

import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import com.example.hearu.ai.response.domain.PromptBuilder;
import com.example.hearu.ai.response.infrastructure.client.NaverClovaClient;
import com.example.hearu.ai.response.infrastructure.client.dto.ClovaChatResponse;
import com.example.hearu.ai.response.infrastructure.client.dto.Message;
import com.example.hearu.diary.event.DiaryAiResponseRequestedEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiResponseCaller {

    private final AiResponseService aiResponseService;
    private final NaverClovaClient naverClovaClient;
    private final PromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;

    @Retryable(
        retryFor = {
            ResourceAccessException.class,
            HttpServerErrorException.class
        },
        maxAttempts = 2,
        backoff = @Backoff(delay = 1000)
    )
    public void call(DiaryAiResponseRequestedEvent event) {
        try {
            // 1. 프롬프트 생성
            String systemPrompt = promptBuilder.systemPromptBuild();
            String userPrompt = promptBuilder.userPromptBuild(event.content(), event.emotionType());

            // 2. LLM 전달 메시지 구성
            List<Message> messages = List.of(
                new Message("system", systemPrompt),
                new Message("user", userPrompt)
            );

            // 3. LLM 외부 API 호출
            ClovaChatResponse clovaChatResponse = naverClovaClient.getAiResponse(messages);
            log.warn("[AI][STOP_REASON] diaryId={}, userId={}, stopReason={}",
                event.diaryId(), event.userId(), clovaChatResponse.result().stopReason());
            String content = clovaChatResponse.result().message().content();
            log.info("[AI][CALL_SUCCESS] diaryId={}, userId={}", event.diaryId(), event.userId());

            // 4. JSON 파싱 및 response 필드 검증
            JsonNode jsonNode = objectMapper.readTree(content);
            JsonNode responseNode = jsonNode.get("response");
            if (responseNode == null || responseNode.isNull()) {
                throw new IllegalStateException(
                    "AI 응답에 'response' 필드가 없습니다. diaryId=" + event.diaryId() + ", content=" + content
                );
            }
            String response = responseNode.asText();

            // 5. AI 응답 완료 및 저장
            aiResponseService.markCompletedAndSaveResponse(event.diaryId(), response);
            log.info("[AI][Complete] diaryId={}, userId={}", event.diaryId(), event.userId());

        } catch (ResourceAccessException | HttpServerErrorException e) {
            // @Retryable / @Recover 가 처리
            throw e;

        } catch (Exception e) {
            log.error(
                "[AI][Unhandled] diaryId={}, userId={}, reason={}, message={}",
                event.diaryId(), event.userId(), e.getClass().getSimpleName(), e.getMessage()
            );
            aiResponseService.markFailed(event.diaryId());
        }
    }

    @Recover
    public void recover(ResourceAccessException e, DiaryAiResponseRequestedEvent event) {
        log.warn("[AI][RetryFail][Network] diaryId={}, userId={}, reason={}, message={}",
            event.diaryId(), event.userId(), e.getClass().getSimpleName(), e.getMessage());
        aiResponseService.markFailed(event.diaryId());
    }

    @Recover
    public void recover(HttpServerErrorException e, DiaryAiResponseRequestedEvent event) {
        log.error("[AI][RetryFail][5xx] diaryId={}, userId={}, reason={}, message={}",
            event.diaryId(), event.userId(), e.getClass().getSimpleName(), e.getMessage());
        aiResponseService.markFailed(event.diaryId());
    }
}
