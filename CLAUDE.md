# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
./gradlew clean build       # Full build
./gradlew test              # Run all tests
./gradlew bootRun --args='--spring.profiles.active=local'   # Run locally
```

프로필은 `local`, `dev`, `prod`. 미지정 시 `application.yml`만 적용되어 로그 레벨·`ddl-auto`가
빠지므로, 로컬 실행은 `local`을 명시한다.

## Architecture Overview

Spring Boot 3.5.7 (Java 21) 일기 앱 + AI 캐릭터. 도메인 기반 계층형 구조.

**도메인 모듈:** `user`, `diary`, `auth`, `ai/response`, `ai/feedback`.
각 도메인은 `controller → service → infrastructure(repository) → domain(entity)` 순.

**핵심 패턴:**
- **이벤트 비동기** — `DiaryService`가 일기 생성 후 `DiaryAiResponseRequestedEvent` 발행 →
  `DiaryAiResponseRequestedEventListener`가 OpenAI GPT-5.6 Luna LLM을 비동기 호출
- **소프트 삭제** — 도메인 엔티티가 `BaseEntity`(`deletedAt`) 상속, 조회는 `deletedAtIsNull()`로 필터
- **정책 객체** — `RefreshTokenPolicy`(토큰 규칙), `OauthProviderFactory`(Google/Kakao/Apple 선택)
- **응답 래퍼** — 본문이 있는 응답은 `ApiResponse.success(...)`로 감싼다

**런타임 흐름은 [`docs/architecture.md`](docs/architecture.md)가 단일 소스다.** 위 요약으로 부족한
요청 파이프라인·시퀀스·상태 전이·실패 경로는 그 문서를 먼저 읽는다. **다음을 바꾸면 같은 커밋에 문서도
갱신한다:**
- 도메인 간 서비스 호출 방향(예: `ai` 패키지가 `diary` 서비스를 참조하게 되는 등)
- 이벤트 발행/구독, `@Async`·`@TransactionalEventListener`의 트랜잭션 경계
- 엔티티 상태 전이(`AiResponseStatusType` 등)와 그 전이를 막는 정책
- 필터 순서, `SecurityConfig`의 인가 규칙, soft delete 전파 범위
- 외부 연동 추가·제거

메서드 시그니처·DTO 필드·로그 문구 변경만으로는 갱신하지 않는다. **흐름이 그대로면 문서도 그대로다** —
사소한 변경마다 문서를 건드리면 diff가 지저분해져 결국 아무도 읽지 않게 된다.

## 상세 규칙의 위치

세부 컨벤션은 `.claude/rules/`에 있고, **해당 경로의 파일을 다룰 때 자동으로 로드된다.**
아래는 무엇이 언제 로드되는지만 적는다 — **어느 파일이 무엇을 담는지는
[`documentation.md`](.claude/rules/documentation.md)의 소유자 표가 갖는다.**

| 파일 | 적용 경로 |
|---|---|
| `.claude/rules/coding-style.md` | `src/main/java/**` |
| `.claude/rules/logging.md` | Java · `logback-*.xml` · `application*.yml` |
| `.claude/rules/db-migration.md` | `db/migration/**` · 엔티티 · `application*.yml` |
| `.claude/rules/testing.md` | `src/test/**` |
| `.claude/rules/documentation.md` | 모든 `*.md` |

문서를 고치기 전에는 소유자 표를 따른다 — 어느 파일에 쓸지 매번 다시 판단하지 않기 위한 것이다.
코드와 문서가 어긋났는지 점검하려면 `/docs-audit`을 돌린다.

## 놓치면 조용히 깨지는 것들

아래는 **컴파일·단위 테스트로 걸리지 않는** 항목이라 여기 남긴다. 각각의 상세는 위 규칙 파일에 있다.

- **스키마는 Flyway가 단일 소스.** `ddl-auto`는 전 프로필 `validate`이며 `update`/`create`로 되돌리지
  않는다. **이미 적용된 마이그레이션 파일은 절대 수정하지 않는다**(체크섬 불일치로 기동 실패).
- **`RefreshToken`은 `BaseEntity`를 상속하지 않는다(의도된 예외).** 만료 시 하드 삭제 대상이라
  `deletedAt`이 무의미하다. 새 엔티티의 선례로 삼지 말 것 — 도메인 데이터는 전부 상속한다.
- **기간 조회는 반열린 구간**(`start <= createdAt < end`). `LocalTime.MAX`로 상한을 만들지 않는다 —
  반올림되면 자정 생성 행이 전날 결과에 섞인다.
- **`logback-spring.xml`에서 `%clr`·`%wEx`를 쓰려면 Spring Boot `defaults.xml`을 include해야 한다.**
  빠지면 **앱이 기동되지 않는다.** 이 파일을 고쳤으면 실제로 띄워서 확인한다.
- **모든 외부 클라이언트에 타임아웃 필수.** 없으면 지연 시 호출 스레드가 무한 대기해 풀이 고갈된다.
- **민감정보(일기 본문·이메일·토큰·OAuth `sub`)는 로그·커밋 메시지 어디에도 넣지 않는다.**
  로그는 `LogMasker`를 쓴다.
- **삭제(`DELETE`)는 `ResponseEntity<Void>` + 204**로 반환하고 `ApiResponse`로 감싸지 않는다
  (204는 HTTP 규약상 본문을 가질 수 없다).

## 사실 주장에는 근거를 붙인다

코드·설정·동작에 대한 **사실 주장**을 할 때는 확인한 파일·명령·출력을 함께 밝힌다.
직접 확인하지 못한 것은 **"미확인"으로 표시**하고 추측으로 채우지 않는다. 기억에 의존한 단정은
재질문 한 번에 뒤집히고, 그때 무엇이 맞는지 판단할 근거가 남지 않는다.

기계로 확인 가능한 것(테스트·컴파일·`git`·라이브러리 바이트코드)은 토론하지 말고 실행해서 확인한다.
독립 검증이 필요하면 `/verify`를 쓴다 — 깨끗한 컨텍스트에서 다시 판단한다.

## 커밋 컨벤션

Conventional Commits를 따른다. 형식은 `type(scope): 설명`이며, 설명은 **한글 개조식**으로
"무엇을 했는지" 결과 중심으로 쓴다.

```
fix(auth): Kakao JWKS 타임아웃 적용 및 토큰 검증 실패 시 401 반환
feat(diary): 일기 캘린더 월별 조회 추가
refactor(ai-response): AOP 프록시 분리 및 코드 품질 개선
```

**type** — 소문자 한 단어만. `Hotfix:`처럼 대문자를 쓰거나 타입 없이 자유 문구(`Auth 관련 리팩토링`)로
시작하지 않는다. 긴급 수정도 타입은 `fix`이고, 긴급 배포 맥락은 본문에 적는다.

| type | 용도 |
|---|---|
| `feat` | 새 기능 |
| `fix` | 버그 수정 (긴급 수정 포함 — `Hotfix`를 쓰지 않는다) |
| `refactor` | 동작 변화 없는 구조 개선. 코드·기능 삭제도 여기에 (`remove`를 쓰지 않는다) |
| `perf` | 성능 개선 |
| `test` | 테스트 추가·수정 |
| `chore` | 빌드·설정·인프라·의존성 등 프로덕션 코드 외 잡무 |
| `docs` | 문서만 변경 |

**scope** — 변경이 속한 **도메인 모듈**을 우선(`user`, `diary`, `auth`, `ai-response`, `ai-feedback`).
도메인에 속하지 않으면 관심사(`common`, `logging`, `db`, `cors`, `error`, `docker`, `nginx`, `discord`,
`dev-cicd`, `prod-cicd`). 범위가 하나로 좁혀지지 않으면 생략(`chore: ...`). 새 scope를 즉흥적으로 만들지 않는다.

**본문** — 제목 한 줄로 이유가 안 되면 본문에 배경("왜")을 적는다(제목은 결과, 본문은 이유).

## Key Infrastructure

| Concern | Implementation |
|---|---|
| Auth | Spring Security + JWT (jjwt 0.11.5) + Google/Kakao/Apple OAuth2 |
| Database | MySQL + Spring Data JPA (MySQLDialect), 스키마는 Flyway |
| AI | OpenAI GPT-5.6 Luna (`OpenAiClient`) |
| Notifications | Discord webhook (`DiscordNotifierClient`) |
| Scheduling | `@Scheduled` cron — refresh token 정리(`RefreshTokenCleanupScheduler`), TLS 인증서 만료 감시(`CertExpiryMonitor`) |
| Docs | SpringDoc OpenAPI (Swagger UI) |
| Observability | 프로필별 로그 레벨, `MdcLoggingFilter`, `MdcTaskDecorator`, `LogMasker`, Actuator (`/actuator/health`) |

## Environment Variables

기동에 필요한 값 (모두 `application.yml`에서 참조하며, 하나라도 없으면 기동에 실패한다):

| 변수 | 용도 |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | MySQL 접속 |
| `JWT_SECRET_KEY` | JWT 서명 키 (HS256이므로 최소 32바이트) |
| `ACCESS_TOKEN_EXPIRE_TIME` | Access Token 만료. `Duration`으로 바인딩되어 숫자만 주면 밀리초 |
| `REFRESH_TOKEN_EXPIRE_TIME` | Refresh Token 만료. 단위는 위와 동일 |
| `GOOGLE_CLIENT_ID` | Google OAuth. iOS/Android 모두 웹 클라이언트 ID를 사용 |
| `KAKAO_REST_API_KEY` | Kakao OAuth |
| `APPLE_CLIENT_IDS` | Apple OAuth. 콤마 구분 (iOS Bundle ID + Android Service ID) |
| `LLM_COMPLETION_URL`, `LLM_API_KEY` | OpenAI (GPT-5.6 Luna) |
| `LLM_MODEL`, `LLM_REASONING_EFFORT`, `LLM_MAX_COMPLETION_TOKENS` | OpenAI 요청 파라미터. 기본값 있어 미지정 시에도 기동됨(`gpt-5.6-luna` / `medium` / `1500`) |
| `DISCORD_WEBHOOK_URL` | 장애 알림 |
| `DIARY_ENCRYPTION_KEY`, `DIARY_ENCRYPTION_KEY_VERSION` | 일기·AI 응답 본문 컬럼 암호화(AES-256-GCM) 키. `KEY`는 Base64 인코딩된 32바이트 키, `VERSION`은 기본값 `1`이라 미지정 시에도 기동됨. `KEY`는 기본값이 빈 문자열이라 미지정이어도 기동은 되지만, 첫 일기·AI 응답 저장/조회에서 예외가 난다 |
