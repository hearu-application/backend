# 일기 본문 저장 암호화 (설계 — 미구현)

## 문제 상황

일기 본문 `diary.content`가 DB에 **평문(cleartext)** 으로 저장된다.

```
-- V1__baseline_schema.sql
CREATE TABLE diary (
    ...
    content      TEXT        NOT NULL,   -- 평문
    ...
);
```

엔티티도 그대로 `String`을 매핑한다(`Diary`의 `content` 필드, `@Column(columnDefinition = "TEXT")`).
DB를 직접 열거나 덤프·백업이 유출되면 **모든 사용자의 일기 원문이 그대로 노출**된다.

일기는 성격상 건강·정신 상태, 종교·정치 신념, 성생활 등 개인정보보호법 **제23조 민감정보**가
담기기 쉬운 데이터다(일기라는 데이터 유형 전체가 법적으로 민감정보로 분류되는 것은 아니고,
그런 내용을 담을 개연성이 높다는 뜻이다 — 모든 일기가 민감정보인 것은 아니다). 민감정보가
포함된 경우 일반 개인정보보다 유출 시 피해가 크므로, 저장 단계 보호 수준이 서비스 신뢰의 전제가 된다.

## 무엇을 막고, 무엇을 못 막나 (위협 모델)

| 위협 | 평문 | 디스크 암호화(TDE) | 컬럼 암호화(앱 계층) |
|---|---|---|---|
| 디스크·백업 스냅샷 물리 도난 | ❌ 노출 | ✅ 방어 | ✅ 방어 |
| DB 덤프 유출 / DBA·운영자 직접 조회 | ❌ 노출 | ❌ 노출 | ✅ 방어 |
| 앱 서버 침해(키에 접근) | ❌ 노출 | ❌ 노출 | ❌ 노출 |

**TDE만으로는 이 서비스에서 가장 현실적인 유출 경로(덤프·내부자 조회)를 못 막는다.**
그래서 두 계층을 함께 둔다 — TDE는 인프라에서 기본으로, 본문은 앱 계층에서 컬럼 암호화.

**E2EE(Day One식)는 채택 불가다.** AI 응답 생성이 일기 원문을 필요로 하기 때문이다.
`DiaryService`가 `DiaryAiResponseRequestedEvent`로 원문을 넘기고 `AiResponseCaller`가
OpenAI에 전송하는 이상, 서버가 원문을 읽을 수 있어야 한다. 서버가 열 수 없는 암호화는
이 아키텍처와 양립하지 않는다. **저장(at rest) 보호가 목표이고, 전송(in transit)·처리 시점의
원문 노출은 별도 컴플라이언스 과제**(→ 아래 "이 문서가 다루지 않는 것")다.

---

## 문제 해결

### 방향 — 인프라 TDE + 애플리케이션 컬럼 암호화 2계층

**계층 1 (TDE)** — MySQL이 관리형(RDS/Aurora)이면 스토리지 암호화를 켠다. 코드 변경 없음,
성능 부담 무시할 수준. 백업·스냅샷·디스크 도난만 막는다.

**계층 2 (컬럼 암호화)** — `content`를 저장 직전 암호화, 조회 직후 복호화한다.
서비스·이벤트·프롬프트 코드는 항상 평문을 다루므로 **AI 응답 흐름은 그대로다**
(`getContent()`가 평문을 반환 → 이벤트 → OpenAI). 이 문서의 나머지는 계층 2를 다룬다.

### 적용 지점 — JPA `AttributeConverter`

`Diary` 엔티티의 `content`에 컨버터를 붙인다. 도메인 로직·팩토리·조회는 손대지 않는다.

```java
@Convert(converter = DiaryContentCryptoConverter.class)
@Column(columnDefinition = "TEXT", nullable = false)
private String content;
```

컨버터는 두 방향만 갖는다.

- `convertToDatabaseColumn(String plain)` → 저장 시 암호화, Base64 문자열 반환
- `convertToEntityAttribute(String stored)` → 조회 시 복호화, 평문 반환

**알고리즘은 AES-256-GCM.** GCM은 기밀성과 무결성(인증 태그)을 함께 준다 — 저장된 값이
조작되면 복호화가 실패해 탐지된다. IV(nonce)는 **암호화마다 새로 난수 생성**하고 암호문 앞에
붙여 저장한다. 저장 포맷 예: `Base64( iv(12B) || ciphertext || tag(16B) )`.

GCM은 매번 IV가 달라 **같은 평문도 매번 다른 암호문**이 된다(결정적이지 않다). 따라서
`content`에 대한 동등 비교·부분 검색·인덱스는 불가능하다 — 아래 "검색 영향" 참고.

### 키 관리 — KMS + envelope 암호화

**키를 코드·환경변수에 평문으로 두면 컬럼 암호화의 의미가 절반으로 준다**(앱 서버가 털리면
키도 함께 털린다는 한계는 남지만, 최소한 DB 유출 하나로는 안 뚫리게 하는 게 목적).

- **데이터 키(DEK)** 로 본문을 암호화하고, DEK는 **KMS 마스터 키(KEK)** 로 감싸(envelope)
  보관한다. 앱은 기동 시 KMS로 DEK를 복호화해 메모리에 두고 쓴다.
- 관리형 클라우드면 클라우드 KMS(AWS KMS 등), 아니면 최소한 KMS 역할을 하는 별도 시크릿
  저장소를 둔다. **DB와 같은 신뢰 경계에 키를 두지 않는다** — 그러면 DB 유출 = 키 유출이다.
- **키 회전(rotation)** 을 처음부터 설계에 넣는다. 저장 포맷에 키 버전 식별자를 포함해
  (`Base64( keyVersion || iv || ciphertext || tag )`) 여러 키 버전이 공존하는 동안에도
  복호화 대상 키를 고를 수 있게 한다. 회전 없이 시작하면 나중에 포맷을 못 바꾼다.

기존 외부 클라이언트 타임아웃 원칙과 동일하게 **KMS 호출에도 타임아웃**을 건다
(기동 시 1회라도 무한 대기는 금지).

### 기존 데이터 백필(backfill)

**이미 저장된 평문 행이 있다.** 컨버터만 붙이면 기동 시 그 평문을 "암호문"으로 알고
복호화를 시도해 깨진다. 반드시 마이그레이션이 선행된다.

**순수 Flyway SQL로는 못 한다** — 암호화에 앱의 키가 필요하기 때문이다. 절차:

1. **일회성 백필 작업**(앱 컨텍스트에서 실행되는 마이그레이션 코드, 예: `ApplicationRunner`
   1회 실행 또는 별도 커맨드)으로 기존 평문 행을 읽어 암호화 후 다시 저장한다.
   행마다 "암호화됨" 여부를 구분할 수 있어야 재실행이 안전하다(키 버전 프리픽스가 그 역할을 겸할 수 있다).
2. 백필 완료 확인 후에야 컨버터가 모든 읽기 경로에서 정상 동작한다.

**컬럼 폭은 그대로 `TEXT`를 쓴다** — 현재 검증 제약(`diary.content` 최대 1,000자,
`ai_response.content`는 `LLM_MAX_COMPLETION_TOKENS=1500` 토큰 한도) 기준으로 암호화(Base64 +
keyVersion/iv/tag 오버헤드) 후에도 `TEXT`(64KB) 대비 8~12배 여유가 있어 확장이 불필요하다고
판단했다(2026-09-04 검토). 이 제약이 실제로 완화되는 시점에 그 변경과 함께 컬럼 폭을 다시 검토한다.

이 절차는 롤백 시나리오(중간 실패 시 평문·암호문 혼재)를 반드시 함께 설계한다.
키 버전/포맷 프리픽스로 각 행의 상태를 판별할 수 있게 하는 이유가 이것이다.

### 검색 영향

현재 `DiaryRepository`는 `content`로 검색·정렬하지 않는다(조회는 `diaryId`·`userId`·기간 기준).
**따라서 지금은 GCM의 비결정성으로 인한 기능 손실이 없다.** 다만 향후 "일기 본문 검색" 기능이
생기면 암호화된 컬럼으로는 `LIKE`·인덱스가 불가능하므로, 그 시점에 별도 설계(예: 검색 전용
비식별 인덱스, 또는 검색 기능 자체의 재고)가 필요하다는 점을 남겨 둔다.

### AI 응답 본문(`ai_response.content`)도 암호화 대상에 포함한다 (확정)

**`ai_response.content`도 같은 범위에 넣는다.**

1. **응답은 일기에서 파생된 텍스트다.** 일기 원문을 입력으로 생성되어 그 감정·상황을 반영하므로,
   원문이 민감정보를 담는 이상 응답도 같은 민감도를 물려받는다. 표현이 바뀌어도 이 파생 관계는 그대로다.
2. **일기와 1:1로 묶여 한쪽만 암호화하면 새어나간다.** `ai_response`는 `diary_id`에 UNIQUE 제약
   (`uk_ai_response_diary_id`)으로 일기와 1:1이다. 일기만 암호화하고 응답을 평문으로 두면,
   DB 덤프에서 같은 행의 `ai_response.content`로 민감한 요지를 재구성할 수 있어 **보호 경계가 뚫린다.**

같은 컨버터를 `AiResponse.content`에 재사용하므로 확장 비용은 작다. **단 한 가지 차이**:
`ai_response.content`는 `TEXT` **nullable**이고 `PENDING`·`FAILED` 상태는 값이 null이다
(`completeResponse`만 값을 채운다). **컨버터는 null → null을 그대로 통과**시켜야 한다
(`diary.content`는 NOT NULL이라 이 분기가 없다). 백필도 `ai_response`에 동일하게 적용한다.

---

## TASK 분리

아래는 위 설계를 구현 순서대로 쪼갠 것이다. **Phase 간 순서는 의존 관계상 고정**이다 —
키 관리(P1)가 컨버터보다 먼저 서고, 백필(P3)이 컨버터를 읽기 경로에 붙이는 일(P4)보다
반드시 선행한다(평문 행에 컨버터가 붙으면 복호화 실패로 기동이 깨진다). 컬럼 폭(TEXT)은
현재 제약 기준 확장이 불필요하다고 판단해 별도 Phase를 두지 않는다(위 "기존 데이터
백필(backfill)" 절 참고). 검증(P5)·문서(P6)의 상세는 아래 "검증"·"구현 시 함께 갱신할 문서"
절이 소유한다 — 여기서는 어느 Phase에 묶이는지만 가리킨다.

### Phase 0 — 인프라 TDE (코드 무관, 별도 트랙)

- [x] MySQL이 관리형이면 스토리지 암호화(TDE)를 켠다. 코드 변경 없음.
      → 계층 1. 백업·스냅샷·디스크 도난만 막는다. P1~P6과 독립적으로 진행 가능.

**관리형 제공사 현황 (2026-09 조사)** — 계층 1은 인프라 선택에 좌우된다.

- **Aiven** — 볼륨 전체 암호화(LUKS2 AES-256)가 **항상 켜져 있고 끌 수 없다**. 백업도 암호화.
  즉 **Aiven을 쓰면 계층 1은 추가 조치 불필요**. 필요 시 BYOK(AWS/GCP/Azure KMS)로 키 통제 강화 가능.
- 어느 제공사든 **엔진 레벨 TDE(테이블/컬럼)는 노출하지 않으며, 계층 1은 계층 2(컬럼 암호화)를 대체하지 못한다.**

### Phase 1 — 암호화 코어 + 키 관리 (완료)

- [x] `ContentCryptoConverter` 구현(`common/encrypt` 패키지, 가칭 `DiaryContentCryptoConverter`에서
      개명 — `Diary`·`AiResponse`가 공유하는 컨버터임을 이름에 반영) — AES-256-GCM,
      `convertToDatabaseColumn`/`convertToEntityAttribute` 두 방향. **아직 엔티티에 `@Convert`로
      붙이지 않았다**(P4에서 붙임).
- [x] 저장 포맷 확정·구현 — `Base64( keyVersion(1B) || iv(12B) || ciphertext || tag(16B) )`.
      IV는 암호화마다 새 난수. 키 버전 프리픽스는 회전·백필 상태 판별을 겸한다.
- [x] **null 통과 처리** — `ai_response.content`가 nullable이므로 `null → null` 분기 구현
      (`diary.content`는 NOT NULL이지만 컨버터는 공유되므로 넣음).
- [x] **키 관리 방식 변경(결정)** — 관리형 클라우드가 아닌 단일 VM docker-compose 배포이고 별도
      KMS/Vault가 없어, KMS+envelope(DEK/KEK) 대신 **환경변수 마스터 키**(`DIARY_ENCRYPTION_KEY`)를
      그대로 AES 키로 쓰는 방식으로 단순화(`DiaryEncryptionProperties`). KMS 호출이 없으므로
      "KMS 타임아웃" 항목은 해당 없음. **한계**: 앱 서버가 침해되면 키도 함께 노출됨
      (JWT_SECRET_KEY와 동일한 신뢰 모델 — "이 문서가 다루지 않는 것" 절의 한계와 같은 종류).
- [x] 키 회전(rotation) 설계 반영 — `DiaryEncryptionProperties.keys`(keyVersion→키 맵)로 여러 버전
      공존 가능. 회전 절차: 새 keyVersion 키 추가 → `active-version` 변경 → 재백필 → 구버전 키 제거.

### Phase 2 — 기존 데이터 백필 (P1 이후, 완료)

- [x] 일회성 백필 작업 구현(`ContentEncryptionBackfillRunner`, `common/encrypt` 패키지) — `ApplicationRunner`로
      기동 시 1회 실행. JPA를 거치지 않고 `JdbcTemplate`로 `diary.content`·`ai_response.content` 원시
      컬럼을 직접 읽어 암호화 후 재저장. 실행 스위치(`diary.encryption.backfill.enabled=true`)는 어떤
      `application*.yml`에도 선언하지 않고 실행 시점에만 환경변수로 준다(1회성 운영 스위치를 설정에
      영구히 남기지 않기 위함).
- [x] **멱등성 보장** — 별도 마커 컬럼 없이, 행마다 `ContentCryptoConverter.convertToEntityAttribute`로
      먼저 복호화를 시도한다. GCM 인증 태그 덕분에 이미 암호화된 값만 성공하므로(위조 확률 무시 가능)
      실패를 "아직 평문"의 신호로 삼는다.
- [x] **롤백/부분 실패 시나리오 설계** — 트랜잭션으로 묶지 않고 행 단위 즉시 커밋. 중간 실패해도
      이미 처리한 행은 남고, 재실행 시 멱등성 검사로 남은 평문 행만 다시 처리한다.
- [x] 실행 전 사전 점검 — 실데이터를 건드리기 전 프로브 문자열로 암복호화 왕복 테스트를 해
      키 설정 오류를 조기에 걸러낸다(전체 행 실패를 막기 위함, 계획에 없던 보강).
- [x] **실제 백필 실행 및 완료 검증(dev)** — 2026-09-04 Railway dev에서
      `DIARY_ENCRYPTION_BACKFILL_ENABLED=true`로 실행. 로그 확인 결과
      `diary`: encrypted=58, alreadyEncrypted=0, skippedNull=0, failed=0 /
      `ai_response`: encrypted=39, alreadyEncrypted=0, skippedNull=8, failed=0.
      실행 후 스위치 제거, 재기동 로그에 `[EncryptBackfill]` 미출력 확인(정상 비활성화).
      **prod 백필은 별도** — prod는 `main` 브랜치 푸시로만 배포되므로(`.github/workflows/prod.yml`),
      prod에 실데이터가 있다면 Phase 3 코드를 `main`에 반영하기 전 동일 절차를 prod에서도 거쳐야 한다.

### Phase 3 — 컨버터 엔티티 적용 (P2 완료 후, 완료)

- [x] `Diary.content`에 `@Convert(converter = ContentCryptoConverter.class)` 부착.
      도메인 로직·팩토리·조회는 손대지 않음(`getContent()`는 여전히 평문 반환).
- [x] `AiResponse.content`에 동일 컨버터 재사용 부착(nullable — `completeResponse`만 값 채움).
- [x] AI 응답 흐름 무변경 확인 — 이벤트·프롬프트·OpenAI 전송은 평문 그대로 흐른다(코드 변경 없음,
      컨버터는 JPA 저장/조회 경계에서만 개입).
      **주의**: 이제 두 엔티티 모두 저장/조회 시 `DIARY_ENCRYPTION_KEY`가 필요하다 — 미설정이면
      기동은 되지만(기본값 빈 문자열) 첫 저장/조회에서 `ContentCryptoException`이 던져진다.
      기존 테스트는 전부 Mockito 단위 테스트(`@SpringBootTest`/`@DataJpaTest` 없음)라 영향 없음
      (`./gradlew test` 전체 통과 확인, 2026-09-04).

### Phase 4 — 검증 (→ 아래 "검증" 절이 소유)

- [ ] 단위: 컨버터 왕복, IV 비결정성(같은 평문 → 다른 암호문), 1비트 변조 시 복호화 실패.
- [ ] 통합: DB 원시 값에 평문 미포함, JPA 조회 복원, AI 이벤트 경로 평문 전달.
- [ ] 백필: 혼재 상태 재실행 멱등성, 중간 실패 복구.
- [ ] 로깅 회귀: `LogMasker.textLength()` 원칙 유지, 본문 로그 미노출.

### Phase 5 — 문서 갱신 (→ 아래 "구현 시 함께 갱신할 문서" 절이 소유)

- [ ] `architecture.md`(KMS 외부 연동·암호화 경계), `CLAUDE.md` 환경변수 표(KMS 키 식별자),
      `db-migration.md`(백필형 마이그레이션 선례 검토).

### 이번 범위 밖 (보류 항목 — 착수 금지, 판단만 기록)

- 일기 본문 검색 기능: 현재 `content`로 검색·정렬하지 않아 GCM 비결정성 손실 없음. 향후 검색
  기능이 생기면 별도 설계 필요(암호화 컬럼은 `LIKE`·인덱스 불가) — 그 시점에 다룬다.

---

## 이 문서가 다루지 않는 것 (별도 과제)

저장 암호화는 **at rest** 만 보호한다. 다음은 이 설계로 해결되지 않으며 별도로 다뤄야 한다.

- **OpenAI 국외 이전 컴플라이언스** — AI 응답 생성 시 원문이 평문으로 OpenAI(국외)로
  전송된다. 개인정보보호법상 국외 이전(위탁) 고지·동의, 민감정보 별도 동의,
  개인정보처리방침 공개가 필요하다. 저장 암호화와 무관하게 반드시 갖춰야 한다.
- **앱 서버 침해** — 앱이 키에 접근할 수 있으므로 서버가 완전히 장악되면 본문도 노출된다.
  이는 컬럼 암호화의 구조적 한계이며, 침해 탐지·최소권한·시크릿 격리로 대응한다.

## 검증

- **단위** — 컨버터 왕복 테스트: `plain → encrypt → decrypt == plain`, 매 암호화의
  IV가 달라 **같은 평문의 두 암호문이 서로 다른지**, 저장값 1비트 변조 시 복호화가
  **실패(무결성 위반 탐지)** 하는지.
- **통합** — `Diary` 저장 후 **DB 원시 값이 평문을 포함하지 않는지** 직접 조회로 확인,
  JPA 조회 시 평문으로 복원되는지, AI 이벤트 경로에 평문이 정상 전달되는지.
- **백필** — 평문·암호문 혼재 상태에서 재실행이 안전한지(멱등성), 중간 실패 후 복구 경로.
- **로깅 회귀** — 본문이 로그에 새지 않는지 재확인. 현재 `DiaryService`·`AiResponseService`가
  `LogMasker.textLength()`로 길이만 남기는 원칙을 컨버터 도입 후에도 유지한다.

## 구현 시 함께 갱신할 문서

- [`architecture.md`](../architecture.md) — "외부 연동 추가·제거"에 KMS가 추가되고, 일기
  저장/조회 경로에 암호화 경계가 생긴다. 흐름이 바뀌는 범위만 갱신한다.
- `CLAUDE.md` 환경변수 표 — KMS 키 식별자 등 기동 필수 값이 늘면 추가한다.
- `.claude/rules/db-migration.md` 선례로 남길 것이 있으면(백필형 마이그레이션 패턴) 반영 검토.
- 구현이 끝나면 이 문서는 삭제한다 (`docs/plan/`은 미결 과제만 갖는다).
