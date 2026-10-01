package com.example.hearu.auth.dto.request;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 구버전 앱은 withdrawalRestoreSupported를 보내지 않는다. 필드가 없을 때 false로 바인딩되어야
 * 기존처럼 신규 가입 경로를 탄다. 역직렬화 결과는 서비스 단위 테스트로는 드러나지 않는다.
 */
@DisplayName("OauthRequest JSON 역직렬화")
class OauthRequestDeserializationTest {

    // Spring Boot가 쓰는 기본 설정과 같은 빌더
    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();

    @Test
    @DisplayName("구버전 앱 요청(필드 없음)은 false로 바인딩된다")
    void missing_field_is_false() throws Exception {
        OauthRequest request = objectMapper.readValue("{\"idToken\":\"t\"}", OauthRequest.class);

        assertThat(request.withdrawalRestoreSupported()).isFalse();
    }

    @Test
    @DisplayName("true를 보내면 true로 바인딩된다")
    void explicit_true() throws Exception {
        OauthRequest request = objectMapper.readValue(
                "{\"idToken\":\"t\",\"withdrawalRestoreSupported\":true}", OauthRequest.class);

        assertThat(request.withdrawalRestoreSupported()).isTrue();
    }
}
