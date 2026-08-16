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

import com.example.hearu.ai.response.infrastructure.client.dto.ClovaChatResponse;
import com.example.hearu.ai.response.infrastructure.client.dto.Message;

public class NaverClovaClientTest {

    private static final String COMPLETION_URL = "https://clova.test/v3/chat-completions";

    private final List<Message> messages = List.of(new Message("user", "안녕"));

    private MockRestServiceServer server;
    private NaverClovaClient naverClovaClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();

        naverClovaClient = new NaverClovaClient(builder.build());
        ReflectionTestUtils.setField(naverClovaClient, "completionUrl", COMPLETION_URL);
        ReflectionTestUtils.setField(naverClovaClient, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(naverClovaClient, "temperature", 0.5);
        ReflectionTestUtils.setField(naverClovaClient, "topK", 0);
        ReflectionTestUtils.setField(naverClovaClient, "topP", 0.8);
        ReflectionTestUtils.setField(naverClovaClient, "repeatPenalty", 1.1);
        ReflectionTestUtils.setField(naverClovaClient, "maxTokens", 1000);
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
                    .body("{\"status\":{\"code\":\"42901\",\"message\":\"Too many requests\"}}"));

            assertThatThrownBy(() -> naverClovaClient.getAiResponse(messages))
                .isInstanceOf(HttpClientErrorException.TooManyRequests.class);
        }

        @Test
        @DisplayName("400은 TooManyRequests가 아니어서 재시도 대상에서 빠진다")
        void bad_request() {
            server.expect(requestTo(COMPLETION_URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"status\":{\"code\":\"40000\",\"message\":\"Bad Request\"}}"));

            assertThatThrownBy(() -> naverClovaClient.getAiResponse(messages))
                .isInstanceOf(HttpClientErrorException.class)
                .isNotInstanceOf(HttpClientErrorException.TooManyRequests.class);
        }

        @Test
        @DisplayName("401도 TooManyRequests가 아니다")
        void unauthorized() {
            server.expect(requestTo(COMPLETION_URL))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

            assertThatThrownBy(() -> naverClovaClient.getAiResponse(messages))
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

            assertThatThrownBy(() -> naverClovaClient.getAiResponse(messages))
                .isInstanceOf(HttpServerErrorException.class);
        }
    }

    @Nested
    @DisplayName("요청 본문")
    class RequestBody {

        // 파라미터 이름이 틀리거나 빠지면 API는 400을 주지 않고 조용히 기본값을 쓴다
        // (maxTokens 기본값 100). 컴파일에도 응답 파싱에도 걸리지 않으므로 나가는 본문을 직접 잠근다.
        @Test
        @DisplayName("생성 파라미터가 API 문서상 이름 그대로 실린다")
        void sends_generation_parameters() {
            server.expect(requestTo(COMPLETION_URL))
                .andExpect(jsonPath("$.maxTokens").value(1000))
                .andExpect(jsonPath("$.temperature").value(0.5))
                .andExpect(jsonPath("$.topK").value(0))
                .andExpect(jsonPath("$.topP").value(0.8))
                .andExpect(jsonPath("$.repeatPenalty").value(1.1))
                .andExpect(jsonPath("$.messages[0].role").value("user"))
                .andExpect(jsonPath("$.messages[0].content").value("안녕"))
                .andRespond(withSuccess("""
                    {
                      "status": {"code": "20000", "message": "OK"},
                      "result": {
                        "message": {"role": "assistant", "content": "{}"},
                        "inputLength": 10,
                        "outputLength": 20,
                        "stopReason": "stop_before",
                        "seed": 1
                      }
                    }
                    """, MediaType.APPLICATION_JSON));

            naverClovaClient.getAiResponse(messages);

            server.verify();
        }
    }

    @Nested
    @DisplayName("정상 응답")
    class Success {

        @Test
        @DisplayName("본문을 ClovaChatResponse로 역직렬화한다")
        void success() {
            server.expect(requestTo(COMPLETION_URL))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-api-key"))
                .andRespond(withSuccess("""
                    {
                      "status": {"code": "20000", "message": "OK"},
                      "result": {
                        "message": {"role": "assistant", "content": "{\\"response\\":\\"안녕하다아~\\"}"},
                        "inputLength": 10,
                        "outputLength": 20,
                        "stopReason": "stop_before",
                        "seed": 1
                      }
                    }
                    """, MediaType.APPLICATION_JSON));

            ClovaChatResponse response = naverClovaClient.getAiResponse(messages);

            assertThat(response.result().message().content()).isEqualTo("{\"response\":\"안녕하다아~\"}");
            assertThat(response.result().stopReason()).isEqualTo("stop_before");
        }
    }
}
