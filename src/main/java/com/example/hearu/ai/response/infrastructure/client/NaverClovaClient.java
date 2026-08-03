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

import com.example.hearu.ai.response.infrastructure.client.dto.ClovaChatResponse;
import com.example.hearu.common.util.SafeBody;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class NaverClovaClient {

    private final RestClient aiRestClient;

    @Value("${llm.completion-url}")
    private String completionUrl;

    @Value("${llm.api-key}")
    private String apiKey;

    @Value("${llm.temperature:0.5}")
    private double temperature;

    @Value("${llm.top-k:0}")
    private int topK;

    @Value("${llm.top-p:0.8}")
    private double topP;

    @Value("${llm.repeat-penalty:1.1}")
    private double repeatPenalty;

    @Value("${llm.max-tokens:1000}")
    private int maxTokens;

    public ClovaChatResponse getAiResponse(List<Message> messages) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messages", messages);
        body.put("temperature", temperature);
        body.put("topK", topK);
        body.put("topP", topP);
        body.put("repeatPenalty", repeatPenalty);
        body.put("maxTokens", maxTokens);

        // 프롬프트 본문에는 일기 내용이 포함되므로 메시지 수/길이만 남긴다.
        log.debug("[AI][HttpCall] 요청 시작. url={}, messageCount={}, temperature={}, maxTokens={}",
            completionUrl, messages.size(), temperature, maxTokens);

        long startedAt = System.currentTimeMillis();

        ClovaChatResponse response = aiRestClient.post()
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
            .body(ClovaChatResponse.class);

        log.debug("[AI][HttpCall] 요청 완료. elapsed={}ms", System.currentTimeMillis() - startedAt);

        return response;
    }
}
