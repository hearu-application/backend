-- =====================================================================
-- ai_feedback.ai_response_id UNIQUE 추가
--
-- "AI 응답 하나당 피드백 하나"는 처음부터 의도였으나 제약이 빠져 있었고,
-- AiFeedbackService.createFeedback의 upsert만으로 규칙을 지켜왔다.
-- 코드로만 지키는 규칙이라 동시 요청이 겹치면 같은 ai_response_id로 두 행이 들어갈 수 있다.
--
-- soft delete와의 관계 — deleted_at을 포함하지 않는 단순 UNIQUE로 충분하다.
-- 피드백 soft delete는 DiaryService.deleteDiary 경로에서만 일어나고, 그 시점에 AI 응답도
-- 함께 soft delete된다. 이후 같은 응답에 피드백을 새로 만들려 해도
-- createFeedback → getOwnedAiResponse가 AI_RESPONSE_NOT_FOUND로 막으므로,
-- "soft delete된 행 + 새 행"이 공존하는 상황 자체가 생기지 않는다.
--
-- 주의 — 이미 중복 행이 있으면 이 마이그레이션은 실패한다. 적용 전 확인할 것:
--   SELECT ai_response_id, COUNT(*)
--     FROM ai_feedback
--    GROUP BY ai_response_id
--   HAVING COUNT(*) > 1;
-- =====================================================================

ALTER TABLE ai_feedback
    ADD CONSTRAINT uk_ai_feedback_ai_response_id UNIQUE (ai_response_id);
