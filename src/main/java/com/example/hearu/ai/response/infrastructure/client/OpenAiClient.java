package com.example.hearu.ai.response.infrastructure.client;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.hearu.ai.response.infrastructure.client.dto.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import com.example.hearu.ai.response.infrastructure.client.dto.OpenAiChatResponse;
import com.example.hearu.common.util.SafeBody;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiClient {

    private final RestClient aiRestClient;

    @Value("${llm.completion-url}")
    private String completionUrl;

    @Value("${llm.api-key}")
    private String apiKey;

    @Value("${llm.model}")
    private String model;

    // low는 위기 상황 응답에서 안전 문구가 부족했다 — 실측 후 medium으로 상향.
    @Value("${llm.reasoning-effort:medium}")
    private String reasoningEffort;

    @Value("${llm.max-completion-tokens:1500}")
    private int maxCompletionTokens;

    public OpenAiChatResponse getAiResponse(List<Message> messages) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("reasoning_effort", reasoningEffort);
        body.put("max_completion_tokens", maxCompletionTokens);
        body.put("response_format", responseFormat());

        // 프롬프트 본문에는 일기 내용이 포함되므로 메시지 수만 남긴다.
        log.debug("[AI][HttpCall] 요청 시작. url={}, model={}, messageCount={}, reasoningEffort={}, maxCompletionTokens={}",
            completionUrl, model, messages.size(), reasoningEffort, maxCompletionTokens);

        long startedAt = System.currentTimeMillis();

        OpenAiChatResponse response = aiRestClient.post()
            .uri(completionUrl)
            .header("Authorization", "Bearer " + apiKey)
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .body(body)
            .retrieve()
            .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                String errorBody = SafeBody.read(res);
                log.warn(
                    "[AI][CALL_FAIL][4xx] status={}, body={}",
                    res.getStatusCode(),
                    errorBody
                );
                throw HttpClientErrorException.create(
                    res.getStatusCode(),
                    res.getStatusText(),
                    res.getHeaders(),
                    errorBody.getBytes(StandardCharsets.UTF_8),
                    StandardCharsets.UTF_8
                );
            })
            .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                log.error(
                    "[AI][CALL_FAIL][5xx] status={}, body={}",
                    res.getStatusCode(),
                    SafeBody.read(res)
                );
                throw new HttpServerErrorException(res.getStatusCode());
            })
            .body(OpenAiChatResponse.class);

        log.debug("[AI][HttpCall] 요청 완료. elapsed={}ms", System.currentTimeMillis() - startedAt);

        return response;
    }

    // PromptBuilder의 JSON 형식 지시만으로는 모델이 ```json으로 감쌀 수 있어 API 레벨에서 강제한다.
    private Map<String, Object> responseFormat() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of("response", Map.of("type", "string")));
        schema.put("required", List.of("response"));
        schema.put("additionalProperties", false);

        Map<String, Object> jsonSchema = new LinkedHashMap<>();
        jsonSchema.put("name", "diary_ai_response");
        jsonSchema.put("schema", schema);
        jsonSchema.put("strict", true);

        Map<String, Object> responseFormat = new LinkedHashMap<>();
        responseFormat.put("type", "json_schema");
        responseFormat.put("json_schema", jsonSchema);
        return responseFormat;
    }
}
