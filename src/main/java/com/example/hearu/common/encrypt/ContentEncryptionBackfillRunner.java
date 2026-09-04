package com.example.hearu.common.encrypt;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 기존 평문 `diary.content`·`ai_response.content`를 일회성으로 암호화해 재저장한다
 * (docs/plan/diary-content-encryption.md Phase 2). 아직 엔티티에 `@Convert`가 붙지 않은
 * 시점에 실행하는 전제이므로 JPA를 거치지 않고 {@link JdbcTemplate}로 원시 컬럼을 직접 갱신한다.
 *
 * <p>{@code diary.encryption.backfill.enabled=true}일 때만 빈이 생성되어 기동 시 1회 실행된다.
 * 이 프로퍼티는 어떤 {@code application*.yml}에도 선언하지 않는다 — {@code @ConditionalOnProperty}는
 * 프로퍼티가 아예 없으면 기본이 "조건 불충족"이라, 실행할 때만
 * {@code DIARY_ENCRYPTION_BACKFILL_ENABLED=true}를 그 실행 한 번의 환경변수로 주고 평상시엔
 * 아무 곳에도 남기지 않는다(1회성 운영 스위치를 코드/설정에 영구히 두지 않기 위함).
 *
 * <p><b>멱등성</b> — 행마다 {@link ContentCryptoConverter#convertToEntityAttribute}로 먼저 복호화를
 * 시도한다. GCM 인증 태그 덕분에 이미 암호화된 값만 성공하고(위조 확률 무시 가능), 평문은 Base64
 * 디코딩 또는 태그 검증에서 실패한다 — 이 실패를 "아직 평문"의 신호로 삼아 별도 마커 컬럼 없이도
 * 재실행이 안전하다.
 *
 * <p><b>부분 실패 대응</b> — 트랜잭션으로 묶지 않고 행 단위로 즉시 커밋한다. 중간에 실패해도
 * 이미 처리한 행은 그대로 남고, 재실행 시 위 멱등성 검사로 남은 평문 행만 다시 처리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "diary.encryption.backfill", name = "enabled", havingValue = "true")
public class ContentEncryptionBackfillRunner implements ApplicationRunner {

    private static final String PREFLIGHT_PROBE_PLAINTEXT = "__diary_encryption_backfill_probe__";

    private final JdbcTemplate jdbcTemplate;
    private final ContentCryptoConverter converter;

    @Override
    public void run(ApplicationArguments args) {
        verifyKeyConfigured();

        BackfillResult diaryResult = backfillDiary();
        log.info("[EncryptBackfill] diary 처리 완료. {}", diaryResult);

        BackfillResult aiResponseResult = backfillAiResponse();
        log.info("[EncryptBackfill] ai_response 처리 완료. {}", aiResponseResult);
    }

    // 실제 데이터를 건드리기 전에 왕복 테스트로 키 설정을 확인한다. 여기서 걸러야
    // 키 설정 오류로 전체 행이 실패하는 상황을 사전에 막을 수 있다.
    private void verifyKeyConfigured() {
        String encrypted = converter.convertToDatabaseColumn(PREFLIGHT_PROBE_PLAINTEXT);
        String decrypted = converter.convertToEntityAttribute(encrypted);

        if (!PREFLIGHT_PROBE_PLAINTEXT.equals(decrypted)) {
            throw new ContentCryptoException("백필 사전 점검 실패 — 암복호화 왕복 결과가 일치하지 않습니다.");
        }
        log.info("[EncryptBackfill] 키 설정 사전 점검 통과");
    }

    private BackfillResult backfillDiary() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT diary_id, content FROM diary");

        BackfillResult result = new BackfillResult();
        for (Map<String, Object> row : rows) {
            Long diaryId = ((Number) row.get("diary_id")).longValue();
            String content = (String) row.get("content");

            processRow(diaryId, content, result, encrypted ->
                jdbcTemplate.update("UPDATE diary SET content = ? WHERE diary_id = ?", encrypted, diaryId));
        }

        return result;
    }

    private BackfillResult backfillAiResponse() {
        List<Map<String, Object>> rows =
            jdbcTemplate.queryForList("SELECT ai_response_id, content FROM ai_response");

        BackfillResult result = new BackfillResult();
        for (Map<String, Object> row : rows) {
            Long aiResponseId = ((Number) row.get("ai_response_id")).longValue();
            String content = (String) row.get("content");

            processRow(aiResponseId, content, result, encrypted ->
                jdbcTemplate.update(
                    "UPDATE ai_response SET content = ? WHERE ai_response_id = ?", encrypted, aiResponseId));
        }

        return result;
    }

    private void processRow(Long id, String content, BackfillResult result, Consumer<String> updater) {
        if (content == null) {
            // ai_response.content는 PENDING/FAILED 상태에서 null이다 — 암호화 대상이 아니다.
            result.skippedNull++;
            return;
        }

        if (isAlreadyEncrypted(content)) {
            result.alreadyEncrypted++;
            return;
        }

        try {
            updater.accept(converter.convertToDatabaseColumn(content));
            result.encrypted++;
        } catch (ContentCryptoException e) {
            result.failed++;
            log.error("[EncryptBackfill] 행 암호화 실패. id={}", id, e);
        }
    }

    private boolean isAlreadyEncrypted(String content) {
        try {
            converter.convertToEntityAttribute(content);
            return true;
        } catch (ContentCryptoException e) {
            return false;
        }
    }

    private static class BackfillResult {
        private int encrypted;
        private int alreadyEncrypted;
        private int skippedNull;
        private int failed;

        @Override
        public String toString() {
            return "encrypted=" + encrypted + ", alreadyEncrypted=" + alreadyEncrypted
                + ", skippedNull=" + skippedNull + ", failed=" + failed;
        }
    }
}
