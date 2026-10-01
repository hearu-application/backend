package com.example.hearu.auth.dto.response;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 구버전 앱은 pendingWithdrawal 필드를 모른다. 이 필드가 없을 때 응답 JSON이 필드 추가 전과
 * 같아야(키 자체가 없어야) 하위 호환이 유지된다. 직렬화 결과는 단위 테스트의 mock으로는 드러나지 않는다.
 */
@DisplayName("AuthResponse JSON 직렬화")
class AuthResponseSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    @DisplayName("일반 로그인 응답에는 pendingWithdrawal 키가 없고, nickname은 null이어도 키가 남는다")
    void issued_omits_pending_withdrawal() throws Exception {
        JsonNode json = objectMapper.readTree(
                objectMapper.writeValueAsString(AuthResponse.issued("a", "r", null)));

        assertThat(json.has("pendingWithdrawal")).isFalse();
        assertThat(json.has("nickname")).isTrue();
        assertThat(json.has("accessToken")).isTrue();
    }

    @Test
    @DisplayName("유예 중 응답에는 pendingWithdrawal.purgeAt이 포함된다")
    void withdrawal_pending_includes_purge_at() throws Exception {
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(
                AuthResponse.withdrawalPending(LocalDateTime.of(2026, 10, 2, 12, 0))));

        assertThat(json.path("pendingWithdrawal").has("purgeAt")).isTrue();
        assertThat(json.get("accessToken").isNull()).isTrue();
    }
}
