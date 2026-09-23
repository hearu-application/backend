-- 과거 날짜 일기 작성 기능: 대상 날짜(diary_date)를 제출 시각(created_at)과 분리한다.
-- 캘린더 조회는 diary_date를, 일일 작성 제한은 created_at을 기준으로 동작한다.
ALTER TABLE diary ADD COLUMN diary_date DATE NULL AFTER emotion_type;

-- 기존 행은 대상 날짜 개념이 없었으므로 제출 날짜(created_at)로 백필한다.
UPDATE diary SET diary_date = DATE(created_at) WHERE diary_date IS NULL;

ALTER TABLE diary MODIFY COLUMN diary_date DATE NOT NULL;

-- 캘린더 조회(userId + diary_date 범위) 인덱스
CREATE INDEX idx_diary_user_diary_date ON diary (user_id, diary_date);
