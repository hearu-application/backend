package com.example.hearu.ai.response.infrastructure.client;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import com.example.hearu.ai.response.infrastructure.client.dto.Message;
import com.example.hearu.ai.response.infrastructure.client.dto.OpenAiChatResponse;

public class OpenAiClientTest {

    private static final String COMPLETION_URL = "https://openai.test/v1/chat/completions";

    private final List<Message> messages = List.of(new Message("user", "안녕"));

    private MockRestServiceServer server;
    private OpenAiClient openAiClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();

        openAiClient = new OpenAiClient(builder.build());
        ReflectionTestUtils.setField(openAiClient, "completionUrl", COMPLETION_URL);
        ReflectionTestUtils.setField(openAiClient, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(openAiClient, "model", "gpt-5.6-luna");
        ReflectionTestUtils.setField(openAiClient, "reasoningEffort", "low");
        ReflectionTestUtils.setField(openAiClient, "maxCompletionTokens", 2000);
    }

    @Nested
    @DisplayName("4xx 응답")
    class ClientError {

        @Test
        @DisplayName("429는 TooManyRequests 하위 타입으로 던져 재시도 대상으로 구분된다")
        void too_many_requests() {
            server.expect(requestTo(COMPLETION_URL))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\":{\"message\":\"Rate limit exceeded\"}}"));

            assertThatThrownBy(() -> openAiClient.getAiResponse(messages))
                .isInstanceOf(HttpClientErrorException.TooManyRequests.class);
        }

        @Test
        @DisplayName("400은 TooManyRequests가 아니어서 재시도 대상에서 빠진다")
        void bad_request() {
            server.expect(requestTo(COMPLETION_URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\":{\"message\":\"Bad Request\"}}"));

            assertThatThrownBy(() -> openAiClient.getAiResponse(messages))
                .isInstanceOf(HttpClientErrorException.class)
                .isNotInstanceOf(HttpClientErrorException.TooManyRequests.class);
        }

        @Test
        @DisplayName("401도 TooManyRequests가 아니다")
        void unauthorized() {
            server.expect(requestTo(COMPLETION_URL))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

            assertThatThrownBy(() -> openAiClient.getAiResponse(messages))
                .isInstanceOf(HttpClientErrorException.class)
                .isNotInstanceOf(HttpClientErrorException.TooManyRequests.class);
        }
    }

    @Nested
    @DisplayName("5xx 응답")
    class ServerError {

        @Test
        @DisplayName("HttpServerErrorException을 던진다")
        void server_error() {
            server.expect(requestTo(COMPLETION_URL))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

            assertThatThrownBy(() -> openAiClient.getAiResponse(messages))
                .isInstanceOf(HttpServerErrorException.class);
        }
    }

    @Nested
    @DisplayName("요청 본문")
    class RequestBody {

        // 파라미터 이름이 틀리면 API가 400을 준다(reasoning 모델은 temperature 등 미지원 파라미터도
        // 400으로 거부). 나가는 본문을 직접 잠가 필드명 오타를 컴파일이 아닌 여기서 잡는다.
        @Test
        @DisplayName("요청 파라미터가 API 문서상 이름 그대로 실린다")
        void sends_request_parameters() {
            server.expect(requestTo(COMPLETION_URL))
                .andExpect(jsonPath("$.model").value("gpt-5.6-luna"))
                .andExpect(jsonPath("$.reasoning_effort").value("low"))
                .andExpect(jsonPath("$.max_completion_tokens").value(2000))
                .andExpect(jsonPath("$.messages[0].role").value("user"))
                .andExpect(jsonPath("$.messages[0].content").value("안녕"))
                .andExpect(jsonPath("$.response_format.type").value("json_schema"))
                .andExpect(jsonPath("$.response_format.json_schema.strict").value(true))
                .andExpect(jsonPath("$.response_format.json_schema.schema.required[0]").value("response"))
                .andRespond(withSuccess("""
                    {
                      "choices": [
                        {
                          "message": {"role": "assistant", "content": "{}"},
                          "finish_reason": "stop"
                        }
                      ],
                      "usage": {"prompt_tokens": 10, "completion_tokens": 20}
                    }
                    """, MediaType.APPLICATION_JSON));

            openAiClient.getAiResponse(messages);

            server.verify();
        }
    }

    @Nested
    @DisplayName("정상 응답")
    class Success {

        @Test
        @DisplayName("본문을 OpenAiChatResponse로 역직렬화한다")
        void success() {
            server.expect(requestTo(COMPLETION_URL))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-api-key"))
                .andRespond(withSuccess("""
                    {
                      "choices": [
                        {
                          "message": {"role": "assistant", "content": "{\\"response\\":\\"안녕하다아~\\"}"},
                          "finish_reason": "stop"
                        }
                      ],
                      "usage": {
                        "prompt_tokens": 10,
                        "completion_tokens": 20,
                        "completion_tokens_details": {"reasoning_tokens": 5}
                      }
                    }
                    """, MediaType.APPLICATION_JSON));

            OpenAiChatResponse response = openAiClient.getAiResponse(messages);

            assertThat(response.choices().get(0).message().content())
                .isEqualTo("{\"response\":\"안녕하다아~\"}");
            assertThat(response.choices().get(0).finishReason()).isEqualTo("stop");
            assertThat(response.usage().completionTokensDetails().reasoningTokens()).isEqualTo(5);
        }

        @Test
        @DisplayName("refusal 응답은 content가 null로 역직렬화된다")
        void refusal() {
            server.expect(requestTo(COMPLETION_URL))
                .andRespond(withSuccess("""
                    {
                      "choices": [
                        {
                          "message": {"role": "assistant", "content": null, "refusal": "정책 위반"},
                          "finish_reason": "stop"
                        }
                      ],
                      "usage": {"prompt_tokens": 10, "completion_tokens": 5}
                    }
                    """, MediaType.APPLICATION_JSON));

            OpenAiChatResponse response = openAiClient.getAiResponse(messages);

            assertThat(response.choices().get(0).message().content()).isNull();
            assertThat(response.choices().get(0).message().refusal()).isEqualTo("정책 위반");
        }
    }
}
