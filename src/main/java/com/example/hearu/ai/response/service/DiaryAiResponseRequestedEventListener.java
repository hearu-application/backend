package com.example.hearu.ai.response.service;

import java.util.List;

import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import com.example.hearu.ai.response.infrastructure.client.NaverClovaClient;
import com.example.hearu.ai.response.domain.PromptBuilder;
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
public class DiaryAiResponseRequestedEventListener {

    private final AiResponseService aiResponseService;
    private final NaverClovaClient naverClovaClient;
    private final PromptBuilder promptBuilder;
    ObjectMapper objectMapper = new ObjectMapper();

    @Async
    @Retryable(
        retryFor = {
            ResourceAccessException.class, // timeout, network
            HttpServerErrorException.class // 5xx
        },
        maxAttempts = 2,
        backoff = @Backoff(delay = 1000)
    )
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(DiaryAiResponseRequestedEvent event) {

        try {
            // 1. prompt 생성
            String systemPrompt = promptBuilder.systemPromptBuild();
            String userPrompt = promptBuilder.userPromptBuild(event.content(), event.emotionType());

            // 2. 사용자 일기 내용을 LLM에 전달할 메시지로 변환
            List<Message> messages = List.of(
                    new Message(
                        "system",
                        systemPrompt
                    ),

                    new Message(
                        "user",
                        userPrompt
                    )
            );

            // 3. llm 외부 api 호출
            ClovaChatResponse clovaChatResponse = naverClovaClient.getAiResponse(messages);
            String content = clovaChatResponse.result().message().content();

            // 4. Json으로 파싱 및 검증 dev
            JsonNode jsonNode = objectMapper.readTree(content);
            String response = jsonNode.get("response").asText();

            // 5. ai 응답 완료 및 저장
            aiResponseService.markedCompletedAndSaveResponse(
                    event.userId(),
                    event.diaryId(),
                    response
            );

        } catch (ResourceAccessException | HttpServerErrorException e) {
            // retry / recover가 처리
            throw e;

        } catch (Exception e) {
            // 🔥 여기서 모든 구멍을 막는다
            log.error(
                "[AI][Unhandled] diaryId={}, userId={}, reason={}",
                event.diaryId(),
                event.userId(),
                e.getClass().getSimpleName()
            );
            aiResponseService.markFailed(
                event.userId(),
                event.diaryId()
            );
        }
    }

    @Recover
    public void recover(ResourceAccessException e, DiaryAiResponseRequestedEvent event) {
        log.warn("[AI][RetryFail][Network] diaryId={}, userId={}, reason={}",
            event.diaryId(),
            event.userId(),
            e.getClass().getSimpleName()
        );
        aiResponseService.markFailed(event.userId(), event.diaryId());
    }

    @Recover
    public void recover(HttpServerErrorException e, DiaryAiResponseRequestedEvent event) {
        log.error(
            "[AI][RetryFail][5xx] diaryId={}, userId={}, reason={}",
            event.diaryId(),
            event.userId(),
            e.getClass().getSimpleName()
        );
        aiResponseService.markFailed(event.userId(), event.diaryId());
    }
}
