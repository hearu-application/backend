-- =====================================================================
-- Flyway 도입 이전 스키마 (baseline)
--
-- 기존 dev/prod DB는 이미 이 스키마를 가지고 있으므로 이 파일을 실행하지 않고
-- baseline-version=1로 표시만 한다(application.yml의 baseline-on-migrate=true).
-- 이 파일은 스키마가 비어있는 신규 환경(예: 새 개발자 로컬)에서만 실행된다.
--
-- 엔티티 매핑 기준으로 재구성했다. provider_type은 현재 운영 DB의 실제 형태인
-- 네이티브 ENUM('GOOGLE','KAKAO')를 반영한다. APPLE 추가는 V2에서 이뤄진다.
-- 운영 스키마와 완전히 동일하게 맞추려면 `SHOW CREATE TABLE`로 대조 후 보정한다.
-- =====================================================================

CREATE TABLE user (
    user_id          BIGINT       NOT NULL AUTO_INCREMENT,
    nickname         VARCHAR(255),
    email            VARCHAR(255) NOT NULL,
    provider_type    ENUM('GOOGLE', 'KAKAO') NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    tone_type        VARCHAR(50)  NOT NULL,
    password         VARCHAR(255),
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    deleted_at       DATETIME(6),
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_user_provider_user_id (provider_user_id)
) ENGINE = InnoDB;

CREATE TABLE diary (
    diary_id     BIGINT      NOT NULL AUTO_INCREMENT,
    user_id      BIGINT      NOT NULL,
    content      TEXT        NOT NULL,
    emotion_type VARCHAR(50) NOT NULL,
    created_at   DATETIME(6) NOT NULL,
    updated_at   DATETIME(6) NOT NULL,
    deleted_at   DATETIME(6),
    PRIMARY KEY (diary_id),
    CONSTRAINT fk_diary_user FOREIGN KEY (user_id) REFERENCES user (user_id)
) ENGINE = InnoDB;

CREATE TABLE ai_response (
    ai_response_id          BIGINT       NOT NULL AUTO_INCREMENT,
    diary_id                BIGINT       NOT NULL,
    content                 TEXT,
    ai_response_status_type VARCHAR(255) NOT NULL,
    created_at              DATETIME(6)  NOT NULL,
    updated_at              DATETIME(6)  NOT NULL,
    deleted_at              DATETIME(6),
    PRIMARY KEY (ai_response_id),
    UNIQUE KEY uk_ai_response_diary_id (diary_id),
    CONSTRAINT fk_ai_response_diary FOREIGN KEY (diary_id) REFERENCES diary (diary_id)
) ENGINE = InnoDB;

CREATE TABLE ai_feedback (
    ai_feedback_id      BIGINT       NOT NULL AUTO_INCREMENT,
    ai_response_id      BIGINT       NOT NULL,
    feedback_type       VARCHAR(255) NOT NULL,
    dislike_reason_type VARCHAR(255),
    custom_text         VARCHAR(50),
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    deleted_at          DATETIME(6),
    PRIMARY KEY (ai_feedback_id),
    CONSTRAINT fk_ai_feedback_ai_response FOREIGN KEY (ai_response_id) REFERENCES ai_response (ai_response_id)
) ENGINE = InnoDB;

CREATE TABLE refresh_token (
    user_id    BIGINT       NOT NULL,
    token      VARCHAR(500) NOT NULL,
    expires_at DATETIME(6),
    PRIMARY KEY (user_id)
) ENGINE = InnoDB;
