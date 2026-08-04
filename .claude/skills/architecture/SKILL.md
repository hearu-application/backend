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
      PromptBuilder → NaverClovaClient → JSON의 "response" 필드 파싱
      → markCompletedAndSaveResponse
클라이언트는 GET /api/v1/diaries/{id}/ai-response 를 폴링한다.
```

## 설계 결정

- **AI 응답은 커밋 이후에 시작된다.** 일기 저장이 롤백되면 LLM 호출도 없다.
- **API는 AI 응답을 기다리지 않는다.** 클라이언트 폴링이 전제이므로, 동기 응답으로 바꾸면
  클라이언트 계약이 깨진다.
- **`ai` → `diary` 서비스 의존은 없다.** 이벤트가 일기 본문·닉네임까지 실어 나르는 이유가 이것이다.
- **soft delete 전파는 `DiaryService.deleteDiary` 한 곳이 관장한다.**
  `AiFeedbackService` → `AiResponseService` 의존이 이미 있어, AI 응답이 피드백을 연쇄 삭제하면 순환이 된다.
- **`AiResponseCaller`는 리스너와 별도 빈이다.** 자기 호출이면 `@Retryable` 프록시가 걸리지 않는다.
- **`MdcLoggingFilter`가 `JwtFilter`보다 먼저 실행된다.** 그래서 요청 시작 로그에는 `userId`가 없다.
- **`COMPLETED`는 종착점이다.** 재요청은 거부된다 — 기존 응답 보존과 LLM 과금 방지.
  반면 `PENDING` 상태의 재요청은 허용한다. 큐 포화·재배포로 유실된 작업을 사용자가 되살리는
  유일한 경로이기 때문이다.
