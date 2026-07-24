-- =====================================================================
-- provider_type ENUM에 APPLE 추가
--
-- 배경: Apple OAuth 도입 시 코드의 ProviderType enum에는 APPLE을 추가했으나,
--       DB의 user.provider_type 컬럼은 enum('GOOGLE','KAKAO')로 남아 있었다.
--       ddl-auto=update는 기존 컬럼 타입을 변경하지 않으므로 스키마 드리프트가 발생했고,
--       Apple 로그인 시 신규 사용자 insert가 1265(Data truncated for column 'provider_type')로 실패했다.
--
--       이 마이그레이션으로 dev/prod 모든 환경에 APPLE 값을 일관되게 반영한다.
-- =====================================================================

ALTER TABLE user
    MODIFY COLUMN provider_type ENUM('GOOGLE', 'KAKAO', 'APPLE') NOT NULL;
