---
paths:
  - "src/main/java/**/*.java"
  - "src/main/resources/logback-*.xml"
  - "src/main/resources/application*.yml"
---

# 로깅

**레벨은 `application-{profile}.yml`의 `logging.level`에서만** 관리하고, `logback-spring.xml`은
프로필별 출력 형태(appender/pattern)만 담당한다.
- **local/dev** — 프레임워크 INFO, `com.example.hearu` DEBUG. root를 DEBUG로 두면 앱 로그가
  톰캣·스프링 내부 로그에 묻히므로 안 한다.
- **prod** — root WARN(프레임워크 소음 차단), `com.example.hearu` INFO(가입·로그아웃·AI 응답 완료·
  스케줄러 건수 등 비즈니스 이벤트 유지). 인증 실패는 봇 트래픽으로 대량 발생하므로 `common.security`만 WARN.

**레벨 선택 기준:**
- **ERROR** — 서버 결함/사람 개입 필요(예상 못 한 예외, 재시도 후 최종 실패, 500 경로). 스택트레이스 포함.
- **WARN** — 복구 가능한 실패·클라이언트 오류(잘못된 토큰, 정책 위반, 없는 리소스). 정상 케이스이므로
  스택트레이스 없이 메시지만.
- **INFO** — 운영 추적용 비즈니스 이벤트. prod에 남는 유일한 앱 레벨이므로 요청마다 찍히는 내용 금지.
- **DEBUG** — 분기 근거, 조회 건수, 외부 호출 파라미터 등 디버깅 맥락.
- **TRACE** — 앱 코드에선 안 씀. Hibernate 바인딩 파라미터 전용.

정상 흐름에 WARN 이상 금지 — 운영 경고는 실제로 확인이 필요한 것만 남아야 한다.

**계층별 위치** — 컨트롤러는 로깅하지 않는다(진입/응답은 `MdcLoggingFilter`가 균일하게, 본문 필드는
서비스가 남겨 중복). 서비스는 "왜 이렇게 동작했는지"(분기·검증 실패·조회 건수), 인프라 클라이언트는
외부 호출 직전 파라미터·완료 후 소요 시간을 남긴다. 호출부가 결과를 남기면 피호출부는 남기지 않는다.

**MDC** — `MdcLoggingFilter`가 요청마다 8자리 `requestId`, `JwtFilter`가 인증 성공 시 `userId`를 넣어
모든 로그에 자동 부착(헬스체크는 필터 제외). `MdcTaskDecorator`가 `@Async` 워커로 MDC를 전파해 일기
작성~AI 응답을 하나의 `requestId`로 추적한다. 워커는 재사용되므로 작업 종료 시 원래 MDC 상태를 복구해야
이전 `requestId`가 다음 작업에 섞이지 않는다.

**민감정보** — 일기 본문·이메일·토큰·OAuth `sub`는 `LogMasker`로 마스킹/길이만 남긴다. 환경 분기 없이
항상 마스킹한다(프로필 분기하면 문장마다 조건이 붙거나 마스커가 환경을 알아야 해 취약). 비밀번호는 로그에
넣지 않는다. 예외 메시지도 마스킹 대상(상위에서 ERROR로 운영 로그까지 도달).

**주의:**
- `logback-spring.xml`에서 `%clr`·`%wEx`를 쓰려면 Spring Boot `defaults.xml`을 include해야 한다.
  빠지면 logback 초기화 실패로 **앱이 기동되지 않는다** — 컴파일·테스트로 안 걸리니 이 파일 수정 시 실제로 띄워 확인.
- SQL 로그는 `spring.jpa.show-sql`이 아니라 `logging.level.org.hibernate.SQL`로 제어(`show-sql`은
  System.out 직접 출력이라 logback·MDC·레벨을 우회).
- 바인딩 파라미터 로거는 Hibernate 6의 `org.hibernate.orm.jdbc.bind`(Hibernate 5의
  `org.hibernate.type.descriptor.sql`은 현재 버전에서 무효).
- 바인딩 TRACE는 일기 본문·이메일을 평문 출력해 `LogMasker`를 우회한다. prod는 꺼져 있고 dev는 테스트
  데이터 전제로 켜 둠 — **dev에 실제 사용자 데이터가 들어오면 `org.hibernate.orm.jdbc.bind`를 꺼야 한다.**

**로그 태그** — AI 응답 흐름은 `[AI][*]` 태그로 통일되어 있다. 정상 경로는

`EventPublished` → `Start` → `PromptBuilt` → `HttpCall`(요청 시작/완료) → `StopReason` →
`CALL_SUCCESS` → `Parsed` → `Complete`

이고, 실패 계열은 다음과 같다.

| 태그 | 의미 |
|---|---|
| `CALL_FAIL][4xx` / `CALL_FAIL][5xx` | 외부 호출이 오류 상태로 응답 (클라이언트가 남긴다) |
| `Retryable` | 재시도 대상 예외를 다시 던지기 직전 |
| `RetryFail][Network` / `][5xx` / `][429` | 재시도 소진 후 `@Recover` 진입 |
| `ParseFail` | 응답 JSON에 `response` 필드가 없음 |
| `Unhandled` | 재시도 대상이 아닌 예외 |

새 로그를 추가할 때 이 태그 체계를 깨지 않는다. 태그를 늘리거나 이름을 바꿨으면 이 목록도 같이 고친다 —
이 파일이 태그의 소유자이고, 다른 문서는 여기를 링크만 한다.
