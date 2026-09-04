---
paths:
  - "src/main/resources/db/migration/**"
  - "src/main/java/**/domain/**"
  - "src/main/java/**/entity/**"
  - "src/main/resources/application*.yml"
---

# 데이터베이스 마이그레이션 (Flyway)

> 이 규칙은 마이그레이션 폴더뿐 아니라 **엔티티를 편집할 때도** 로드된다.
> 스키마 변경은 엔티티 쪽에서 시작되기 때문이다.

**모든 스키마/DDL 변경은 Flyway 마이그레이션으로만 한다.** Flyway가 스키마의 단일 소스, Hibernate는
검증만. `ddl-auto`는 전 프로필 `validate`이고 **`update`/`create`로 되돌리지 않는다** — `update`는 기존
컬럼 타입을 못 바꾸고(추가만) 환경마다 스키마를 제각각으로 만들어 드리프트를 조용히 만든다. 실제로 이 때문에
`provider_type`이 `enum('GOOGLE','KAKAO')`로 남아 Apple 로그인이 1265(Data truncated)로 실패한 적 있다.

**변경 절차** — 엔티티/스키마를 바꾸면 **같은 커밋/PR에** 마이그레이션 파일을 함께 넣는다.
1. `src/main/resources/db/migration/`에 `V{다음번호}__{설명}.sql` 추가. 파일명은 `V`+정수+`__`(언더스코어
   2개)+스네이크 설명(예: `V3__add_user_birthday.sql`).
2. DDL을 직접 SQL로 작성한다(**Flyway는 엔티티 변경을 감지해 SQL을 자동 생성하지 않는다**).
3. 엔티티 매핑도 맞춰 변경. 기동 시 Flyway 적용 후 Hibernate가 `validate`하므로 마이그레이션을 빠뜨리면
   **기동 단계에서 실패한다** — 이 실패가 안전장치다.

**불변식:**
- **적용된 마이그레이션 파일은 절대 수정하지 않는다**(Flyway 체크섬 검증, 고치면 기동 실패). 잘못된 변경은
  파일을 고치지 말고 **되돌리는 새 버전**(예: `V5__revert_...sql`)을 추가한다.
- 버전 번호는 **순차 증가**한다(병합 시 중복 확인).
- `V1__baseline_schema.sql`은 Flyway 도입 이전 전체 스키마다. 기존 dev/prod는 `baseline-on-migrate`로
  V1을 건너뛰고 baseline(version 1)으로 표시, **빈 DB(신규 로컬)만** V1부터 전부 실행한다. baseline 대상
  DB는 V1이 안 돌아 V1을 고쳐도 기존 환경엔 반영되지 않는다.
- **앱의 비밀(암호화 키 등)이 있어야 변환 가능한 기존 데이터**는 순수 SQL인 Flyway로 못 한다. 이때는
  `ApplicationRunner` 기반 1회성 러너로 앱 컨텍스트에서 처리한다(선례: `ContentEncryptionBackfillRunner`).
  실행 스위치는 어떤 `application*.yml`에도 남기지 않고 실행 시점에만 환경변수로 준다. 행 단위 즉시
  커밋 + 멱등성 체크(재실행 시 이미 처리된 행을 안전하게 건너뜀)로 부분 실패에 대비한다.
