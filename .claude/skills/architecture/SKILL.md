---
name: architecture
description: hearu 백엔드의 런타임 흐름 — 일기 작성부터 AI 응답까지의 비동기 파이프라인, 트랜잭션 경계, AI 응답 상태 전이, 실패·재시도 경로, 요청 필터 순서와 인가 규칙, soft delete 전파 범위. 도메인 간 호출이나 이벤트 흐름을 바꾸거나, "왜 이렇게 동작하는지"를 알아야 할 때 쓴다.
when_to_use: 일기·AI 응답·인증 흐름을 수정할 때, PENDING/FAILED 상태나 AI 응답 유실을 디버깅할 때, 새 도메인·이벤트·외부 연동을 추가할 때, 필터·인가·삭제 전파를 건드릴 때
---

# hearu 런타임 흐름

다이어그램·시퀀스·상태 기계·API 표·설정값은 `docs/architecture.md`에 있다.

## 중심 흐름

```
POST /api/v1/diaries
  → DiaryService.createDiary  [트랜잭션]
      User 조회 → 정책 검증 → Diary 저장
      → AiResponseService.createPending  (같은 트랜잭션에서 PENDING 행 생성)
      → publishEvent(DiaryAiResponseRequestedEvent)
  → 커밋 → 즉시 응답 (AI를 기다리지 않는다)
  ⇢ AFTER_COMMIT + @Async → AiResponseCaller.call()
      PromptBuilder → OpenAiClient → JSON의 "response" 필드 파싱
      → markCompletedAndSaveResponse
클라이언트는 GET /api/v1/diaries/{id}/ai-response 를 폴링한다.
```

## 설계 결정

- **AI 응답은 커밋 이후에 시작된다.** 일기 저장이 롤백되면 LLM 호출도 없다.
- **API는 AI 응답을 기다리지 않는다.** 클라이언트 폴링이 전제이므로, 동기 응답으로 바꾸면
  클라이언트 계약이 깨진다.
- **`ai` → `diary` 서비스 의존은 없다.** 이벤트가 일기 본문·닉네임까지 실어 나르는 이유가 이것이다.
- **일기 하위 데이터 삭제(soft delete 전파·탈퇴 하드 삭제)는 `DiaryService` 한 곳이 관장한다.**
  `AiFeedbackService` → `AiResponseService` 의존이 이미 있어, AI 응답이 피드백을 연쇄 삭제하면 순환이 된다.
- **탈퇴는 유예 후 하드 삭제다. 복구는 앱이 명시적으로 요청할 때만 한다.** 구버전 앱은 복구 안내 화면이
  없으므로 기존처럼 신규 가입시킨다. 탈퇴 시 `providerUserId` 변경은 롤백된 구 서버와의 호환을 위해 유지한다.
- **`AiResponseCaller`는 리스너와 별도 빈이다.** 자기 호출이면 `@Retryable` 프록시가 걸리지 않는다.
- **`MdcLoggingFilter`가 `JwtFilter`보다 먼저 실행된다.** 그래서 요청 시작 로그에는 `userId`가 없다.
- **AI 응답 답장은 서버가 끝까지 책임진다.** 실패해도 `PENDING`을 유지하고, 회수 스케줄러가 고착된
  `PENDING`(실패한 실행·유실된 작업 모두)을 다시 실행한다. 실행 상한을 다 쓰면 `FAILED`로 확정하고 Discord로
  알린다. 새 상태 값은 만들지 않는다 — 구버전 앱이 모르는 값을 `PENDING`처럼 취급해 끝없이 기다리게 된다.
- **실행 횟수는 실패가 아니라 시작 시점에 센다(리스너, `@Retryable` 바깥).** 실패 기록에 기대면 실행이 도중에
  죽었을 때 횟수가 안 남아 LLM 호출 상한이 깨진다.
- **AI 응답 이벤트는 `DiaryAiResponseRequestedEvent.from` 한 곳에서만 만든다.** 발행 경로(작성·재요청·회수)마다
  조립하면 필드 추가 시 한 경로만 어긋나도 아무것도 걸리지 않는다.
- **`COMPLETED`는 종착점이고, 재요청 API는 멱등이다.** `FAILED`일 때만 다시 실행하고(구버전 앱용) 나머지는
  아무것도 하지 않는다. 수동 재요청은 실행 1회만 허용한다.
