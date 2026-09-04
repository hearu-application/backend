package com.example.hearu.common.encrypt;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
@DisplayName("본문 암호화 백필 러너")
public class ContentEncryptionBackfillRunnerTest {

    private static final String DIARY_QUERY = "SELECT diary_id, content FROM diary";
    private static final String AI_RESPONSE_QUERY = "SELECT ai_response_id, content FROM ai_response";
    private static final String DIARY_UPDATE = "UPDATE diary SET content = ? WHERE diary_id = ?";
    private static final String AI_RESPONSE_UPDATE = "UPDATE ai_response SET content = ? WHERE ai_response_id = ?";

    @Mock
    JdbcTemplate jdbcTemplate;

    @Mock
    ContentCryptoConverter converter;

    @InjectMocks
    ContentEncryptionBackfillRunner runner;

    @BeforeEach
    void setUp() {
        // "ENC:" 접두가 붙은 값만 이미 암호화된 것으로 간주하는 가짜 암복호화 동작.
        // 사전 점검 프로브도 이 규칙을 그대로 통과한다. lenient()는 사전 점검 실패 테스트가
        // 이 기본 동작을 doReturn()으로 덮어써 미사용 스텁으로 잡히는 것을 막기 위함이다.
        lenient().when(converter.convertToDatabaseColumn(anyString()))
            .thenAnswer(invocation -> "ENC:" + invocation.getArgument(0, String.class));
        lenient().when(converter.convertToEntityAttribute(anyString()))
            .thenAnswer(invocation -> {
                String stored = invocation.getArgument(0, String.class);
                if (stored.startsWith("ENC:")) {
                    return stored.substring(4);
                }
                throw new ContentCryptoException("아직 암호화되지 않은 값입니다.");
            });
    }

    @Nested
    @DisplayName("사전 점검")
    class Preflight {

        @Test
        @DisplayName("암복호화 왕복이 실패하면 데이터를 건드리지 않고 예외를 던진다")
        void preflight_failure_stops_before_touching_data() {
            // given()은 재스텁 전 기존 willAnswer(예외 던지는 분기)를 실제로 실행시켜버리므로
            // 여기서는 doReturn()으로 실행 없이 덮어쓴다.
            doReturn("전혀 다른 값").when(converter).convertToEntityAttribute(anyString());

            assertThatThrownBy(() -> runner.run(null))
                .isInstanceOf(ContentCryptoException.class);

            verify(jdbcTemplate, never()).queryForList(anyString());
        }
    }

    @Nested
    @DisplayName("정상 백필")
    class NormalBackfill {

        @Test
        @DisplayName("평문·암호문·null이 섞여 있어도 평문 행만 암호화해 갱신한다")
        void mixed_rows_only_plaintext_is_updated() {
            given(jdbcTemplate.queryForList(DIARY_QUERY)).willReturn(List.of(
                Map.of("diary_id", 1L, "content", "평문 일기"),
                rowWith("diary_id", 2L, null)
            ));
            given(jdbcTemplate.queryForList(AI_RESPONSE_QUERY)).willReturn(List.of(
                Map.of("ai_response_id", 10L, "content", "ENC:이미 암호화된 응답")
            ));

            runner.run(null);

            verify(jdbcTemplate).update(DIARY_UPDATE, "ENC:평문 일기", 1L);
            verify(jdbcTemplate, never()).update(eq(DIARY_UPDATE), any(), eq(2L));
            verify(jdbcTemplate, never()).update(eq(AI_RESPONSE_UPDATE), any(), eq(10L));
        }
    }

    @Nested
    @DisplayName("부분 실패")
    class PartialFailure {

        @Test
        @DisplayName("한 행의 암호화가 실패해도 나머지 행은 계속 처리된다")
        void one_row_failure_does_not_stop_others() {
            given(converter.convertToDatabaseColumn("실패할 평문"))
                .willThrow(new ContentCryptoException("암호화 실패"));

            given(jdbcTemplate.queryForList(DIARY_QUERY)).willReturn(List.of(
                Map.of("diary_id", 1L, "content", "실패할 평문"),
                Map.of("diary_id", 2L, "content", "정상 평문")
            ));
            given(jdbcTemplate.queryForList(AI_RESPONSE_QUERY)).willReturn(List.of());

            runner.run(null);

            verify(jdbcTemplate, never()).update(eq(DIARY_UPDATE), any(), eq(1L));
            verify(jdbcTemplate).update(DIARY_UPDATE, "ENC:정상 평문", 2L);
        }
    }

    @Nested
    @DisplayName("멱등성")
    class Idempotency {

        @Test
        @DisplayName("이미 암호화된 행을 다시 처리해도 갱신하지 않는다")
        void already_encrypted_row_is_skipped_on_rerun() {
            given(jdbcTemplate.queryForList(DIARY_QUERY)).willReturn(List.of(
                Map.of("diary_id", 1L, "content", "ENC:이미 암호화된 일기")
            ));
            given(jdbcTemplate.queryForList(AI_RESPONSE_QUERY)).willReturn(List.of());

            runner.run(null);

            verify(jdbcTemplate, never()).update(anyString(), any(), any());
        }
    }

    private static Map<String, Object> rowWith(String idColumn, long id, String content) {
        Map<String, Object> row = new HashMap<>();
        row.put(idColumn, id);
        row.put("content", content);
        return row;
    }
}
