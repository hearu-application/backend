package com.example.hearu.ai.response.service;

import java.util.List;

import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import com.example.hearu.ai.response.domain.PromptBuilder;
import com.example.hearu.common.logging.LogMasker;
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
            HttpServerErrorException.class,
            HttpClientErrorException.TooManyRequests.class
        },
        maxAttempts = 2,
        backoff = @Backoff(delay = 1000)
    )
    public void call(DiaryAiResponseRequestedEvent event) {
        long startedAt = System.currentTimeMillis();
        try {
            // 1. 프롬프트 생성
            String systemPrompt = promptBuilder.systemPromptBuild(event.nickname(), event.toneType());
            String userPrompt = promptBuilder.userPromptBuild(event.content(), event.emotionType());
            log.debug("[AI][PromptBuilt] diaryId={}, systemPrompt={}, userPrompt={}, emotionType={}, toneType={}",
                event.diaryId(),
                LogMasker.textLength(systemPrompt),
                LogMasker.textLength(userPrompt),
                event.emotionType(),
                event.toneType());

            // 2. LLM 전달 메시지 구성
            List<Message> messages = List.of(
                new Message("system", systemPrompt),
                new Message("user", userPrompt)
            );

            // 3. LLM 외부 API 호출
            ClovaChatResponse.Result result = naverClovaClient.getAiResponse(messages).result();

            // 외부 호출 자체의 소요 시간은 NaverClovaClient가, 전체 소요 시간은
            // 아래 [AI][Complete]가 기록하므로 여기서는 중복 측정하지 않는다.
            String content = result.message().content();

            // stopReason·토큰 수는 아래 파싱이 실패했을 때 원인이 "출력 잘림"인지 가르는 유일한 근거다.
            // 그 판정을 prod에서 해야 하므로 DEBUG가 아니라 이 INFO 줄에 싣는다.
            log.info("[AI][CALL_SUCCESS] diaryId={}, userId={}, stopReason={}, inputLength={}, outputLength={}",
                event.diaryId(), event.userId(),
                result.stopReason(), result.inputLength(), result.outputLength());

            // 4. JSON 파싱 및 response 필드 검증
            JsonNode jsonNode = objectMapper.readTree(content);
            JsonNode responseNode = jsonNode.get("response");
            if (responseNode == null || responseNode.isNull()) {
                // 이 예외 메시지는 아래 catch에서 ERROR로 기록되어 운영 로그에도 남는다.
                // content는 사용자의 일기를 바탕으로 생성된 텍스트이므로 원문을 넣지 않는다.
                // 실제 응답 본문은 dev/local의 DEBUG 로그에서 확인한다.
                log.debug("[AI][ParseFail] 원본 응답. diaryId={}, content={}", event.diaryId(), content);
                throw new IllegalStateException(
                    "AI 응답에 'response' 필드가 없습니다. diaryId=" + event.diaryId()
                        + ", content=" + LogMasker.textLength(content)
                );
            }
            String response = responseNode.asText();
            log.debug("[AI][Parsed] diaryId={}, response={}", event.diaryId(), LogMasker.textLength(response));

            // 5. AI 응답 완료 및 저장
            aiResponseService.markCompletedAndSaveResponse(event.diaryId(), response);
            log.info("[AI][Complete] diaryId={}, userId={}, elapsed={}ms",
                event.diaryId(), event.userId(), System.currentTimeMillis() - startedAt);

        } catch (ResourceAccessException | HttpServerErrorException
                 | HttpClientErrorException.TooManyRequests e) {
            // @Retryable / @Recover 가 처리. 재시도로 복구되면 이 줄이 실패의 유일한 흔적이라 DEBUG면 안 된다.
            log.warn("[AI][Retryable] 재시도 대상 예외 발생. diaryId={}, reason={}",
                event.diaryId(), e.getClass().getSimpleName());
            throw e;

        } catch (Exception e) {
            // 스택트레이스를 함께 남겨야 파싱/저장 실패 원인을 추적할 수 있다.
            log.error(
                "[AI][Unhandled] diaryId={}, userId={}, reason={}, message={}",
                event.diaryId(), event.userId(), e.getClass().getSimpleName(), e.getMessage(), e
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

    @Recover
    public void recover(HttpClientErrorException.TooManyRequests e, DiaryAiResponseRequestedEvent event) {
        log.error("[AI][RetryFail][429] diaryId={}, userId={}, reason={}, message={}",
            event.diaryId(), event.userId(), e.getClass().getSimpleName(), e.getMessage());
        aiResponseService.markFailed(event.diaryId());
    }
}
