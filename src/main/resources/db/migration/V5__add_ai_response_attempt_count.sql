-- AI 응답 실패 시 바로 FAILED로 확정하지 않고 PENDING을 유지한 채 실행 횟수를 센다.
-- 회수 스케줄러가 이 값으로 최종 실패(FAILED) 여부를 판단한다.
ALTER TABLE ai_response ADD COLUMN attempt_count INT NOT NULL DEFAULT 0;

-- 회수 스케줄러 대상 조회(status = PENDING and updated_at < cutoff) 인덱스
CREATE INDEX idx_ai_response_status_updated_at ON ai_response (ai_response_status_type, updated_at);
