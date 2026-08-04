---
paths:
  - "src/main/java/**/*.java"
---

# 코딩 컨벤션 (Java 프로덕션 코드)

- **Entities** — `BaseEntity`(`createdAt`/`updatedAt`/`deletedAt`) 상속. `@NoArgsConstructor(access = PROTECTED)`
  + 정적 팩토리(`User.create(...)`). 세터 금지, 상태 변경은 도메인 메서드로. Enum은 `@Enumerated(EnumType.STRING)`.
  **예외 — `RefreshToken`은 `BaseEntity`를 상속하지 않는다(의도된 것).** 토큰은 만료 시 하드 삭제
  대상이라 `deletedAt`이 무의미하고, 소프트 삭제 필터가 없는 상태에서 컬럼만 생기면 "지워졌는데 조회된다"는
  오해를 부른다. 새 엔티티를 추가할 때 이 예외를 선례로 삼지 말 것 — 도메인 데이터는 전부 상속한다.
- **DTOs** — 불변 record(`public record ProfileResponse(...) {}`). 도메인별 `dto/request/`·`dto/response/`
  분리. Entity→DTO 변환은 서비스 계층.
- **Services** — 클래스 레벨 `@Transactional`, 조회 메서드만 `@Transactional(readOnly = true)`.
  헬퍼 패턴 `getUserOrThrow(userId)`(한글 warn 로그 후 `BusinessException`). 복잡한 로직엔 단계별
  한글 주석(`// 1. User 조회`, `// 2. 검증`).
- **Error Handling** — 도메인별 `ErrorCode` enum(`UserErrorCode` 등). 항상 `BusinessException(ErrorCode)`로
  던지고 raw 예외 금지. 던지기 전 맥락 로그: `log.warn("메시지. userId={}", userId)`.
- **Controllers** — `@AuthenticationPrincipal Long userId`로 사용자 추출. Swagger용 `@Tag`·`@SecurityRequirement`.
  **예외 — 삭제(`DELETE`)는 `ResponseEntity<Void>` + 204 No Content로 본문 없이 반환한다**
  (`DiaryController.deleteDiary`, `UserController.delete`). 204는 HTTP 규약상 본문을 가질 수 없으므로
  `ApiResponse`로 감싸지 않는다. 성공 메시지를 굳이 실어야 하면 204가 아니라 200을 쓴다.
- **응답 래퍼** — 본문이 있는 응답은 `ApiResponse.success(...)`로 `ResponseEntity<ApiResponse<T>>` 반환
  (예외는 위 Controllers 항목).

## 기간 조회

**반열린 구간**(`start <= createdAt < end`)으로 쓴다. Spring Data 메서드명은 `Between`이 아니라
`CreatedAtGreaterThanEqualAndCreatedAtLessThan`. `Between`은 상한을 포함해 경계 시각의 행이 두 기간에
중복으로 잡힌다.

```java
// 하루
LocalDateTime start = today.atStartOfDay();
LocalDateTime end = today.plusDays(1).atStartOfDay();
// 한 달
LocalDateTime start = yearMonth.atDay(1).atStartOfDay();
LocalDateTime end = yearMonth.plusMonths(1).atDay(1).atStartOfDay();
```

`LocalTime.MAX`로 상한을 만들지 않는다. `23:59:59.999999999`(나노초 9자리)는 `datetime(6)` 비교 시
드라이버·서버 버전에 따라 절삭되거나 다음 날 `00:00:00`으로 반올림되고, 반올림되면 자정 생성 행이
전날 결과에 섞인다 — 컴파일·단위 테스트로 안 걸린다. `start`는 두고 상한만 "다음 구간의 시작"으로 잡는다.

## 외부 호출

모든 외부 클라이언트에 **타임아웃 필수**. 타임아웃 없는 클라이언트는 지연 시 호출 스레드를 무한
대기시켜 스레드 풀 고갈로 이어진다. 기존 설정은 `RestClientConfig`(Clova/Slack),
`JwksRestTemplateFactory`(OAuth JWKS)를 참고한다.
