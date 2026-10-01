# 회원 탈퇴 유예 + 하드 삭제

## 배경

기존 탈퇴(`DELETE /api/v1/users`)는 refresh token 삭제와 `User` soft delete만 했다. 일기·AI 응답·피드백은
영구히 남았고, 같은 소셜 계정으로 다시 로그인하면 새 `user_id`로 가입돼 남은 데이터는 아무도 접근할 수 없는
채 쌓였다.

요구사항:

- 탈퇴 후 **24시간 유예**. 유예 중에는 `User`만 soft delete, 나머지 데이터는 유지
- 유예가 지나면 유저·일기·AI 응답·피드백 **하드 삭제** (배포 전 탈퇴자 포함)
- 배포·롤백 중에도, 구버전 앱에서도 깨지지 않을 것

런타임 흐름(시퀀스·삭제 순서·스케줄러·설정값)은 [`architecture.md`](architecture.md) §4.4·§4.6·§4.7이 소유한다.
이 문서는 **왜 그렇게 정했는지**만 남긴다.

---

## 근거 조사

| 항목 | 내용 | 출처 |
|---|---|---|
| 파기 시한 | 탈퇴는 동의 철회라 지체 없이 파기. ISMS-P 3.4.1은 "정당한 사유가 없는 한 5일 이내" | [itwiki ISMS-P 3.4.1](https://itwiki.kr/w/ISMS-P_인증_기준_3.4.1.개인정보의_파기) |
| Apple 5.1.1(v) | 계정 삭제를 앱 안에서 제공해야 하고, 일시 비활성화만으로는 부족. Sign in with Apple 토큰 revoke 필요 | [Apple Developer](https://developer.apple.com/support/offering-account-deletion-in-your-app) |
| 유예 중 복구 UX | X는 30일 유예 중 로그인하면 "재활성화할지" 확인 안내 후 복구 | [X Help](https://help.x.com/managing-your-account/how-to-deactivate-twitter-account) |
| 복구 API 분리 | Playnanoo는 탈퇴 조회와 복구 API를 따로 두고 클라이언트가 명시적으로 호출 | [Playnanoo](https://document.playnanoo.com/api/unity/account/withdraw/restore/) |

24시간 유예 + 매시 정리는 5일 이내 파기 기준 안에 든다.

---

## 핵심 의사결정

### 1. 복구는 명시적 요청으로만, 구버전 앱은 기존 동작

유예 중 로그인을 자동 복구로 처리하면 **구버전 앱 사용자는 안내 없이 예전 일기를 다시 보게 된다.**
구버전 앱에는 복구 안내 화면이 없고, 조사한 사례(X)도 확인을 거쳐 복구한다.

- 로그인 요청에 `withdrawalRestoreSupported=true`를 보내는 앱만 `pendingWithdrawal` 응답을 받는다.
- 앱이 사용자에게 묻고, "복구"면 `/restore`, "새로 시작"이면 플래그 `false`로 로그인을 다시 호출한다.
- 플래그가 없는 구버전 앱은 기존처럼 신규 가입. 예전 계정은 유예 후 삭제된다.
- `pendingWithdrawal`은 null이면 JSON 키 자체가 빠진다(`@JsonInclude(NON_NULL)`, 필드 단위).
  다른 필드에 걸지 않은 이유: 신규 가입 응답의 `"nickname": null` 키가 사라지면 구버전 앱 응답이 바뀐다.

### 2. 탈퇴 시 `providerUserId` 변경(`sub:deleted:<uuid>`)을 유지

복구를 위해 원래 sub를 그대로 두는 안도 있었지만, 그러면 **구 서버(롤백)에서 깨진다.** 구 서버 로그인은
`deletedAt IS NULL`인 행만 찾으므로, 원래 sub를 가진 탈퇴 행이 남아 있으면 같은 sub로 INSERT하다
`uk_user_provider_user_id` 위반(500)이 난다. 변경을 유지하면 구 서버는 기존처럼 새 계정을 만들 뿐이다.

복구 대상은 `sub:deleted:` 접두사 LIKE로 찾는다(이스케이프 문자는 `!` — MySQL 문자열에서 `\`가 다시
이스케이프로 해석되는 문제를 피하려고).

부수 효과:

- **스키마 변경 없음.** 탈퇴 시각은 기존 `deleted_at`. 마이그레이션이 없으니 구 버전의 `validate`에도 영향 없다.
- **배포 전 탈퇴자가 자동으로 포함된다.** 같은 형식이라 같은 정리 쿼리에 걸린다.

### 3. 하드 삭제를 `UserService`가 아닌 `WithdrawalPurgeService`에 둠

`DiaryService` → `UserService` 의존이 이미 있어, `UserService`가 `DiaryService`를 부르면 순환이 된다.
하위 데이터 삭제 순서(피드백 → AI 응답 → 일기)는 soft delete 전파와 같은 이유로 `DiaryService`가 갖는다.

### 4. 복구와 삭제의 경합은 행 락으로 직렬화

복구(`findLatestRestorableForUpdate`)와 삭제(`findByIdForUpdate`) 모두 같은 `User` 행에 `PESSIMISTIC_WRITE`를
잡는다. 삭제는 락을 잡은 뒤 "여전히 만료된 탈퇴 상태인지"를 다시 확인해, 대상 조회와 삭제 사이에 복구된
유저를 지우지 않는다.

### 5. 유예 중 기존 access token은 막지 않음

탈퇴 후 access token이 만료까지 유효한 기존 정책을 유지했다(요청마다 DB 조회 비용, 문서화된 결정).
유예 중 피드백 작성 등으로 하드 삭제가 FK 위반으로 실패하면 그 유저만 다음 실행에서 다시 시도된다.

---

## 버전 호환

| 상황 | 결과 |
|---|---|
| 배포 전 탈퇴자 | 이미 `sub:deleted:<uuid>` + 유예 초과 → 첫 정리에서 하드 삭제. 이후 재가입한 계정(활성)은 무관 |
| 구버전 앱 | 요청·응답 JSON 동일. 유예 중 로그인 → 신규 가입 |
| 신 서버에서 탈퇴 → 구 서버로 로그인(롤백) | 구 서버는 신규 가입. 500 없음. `/restore`는 구 서버에 없어 404 |
| 구 서버에서 탈퇴 → 신 서버 | 행 형식이 같아 신버전 앱이면 복구 가능 |
| 유예 중 이미 재가입(활성 계정 존재) | 활성 계정 우선, 복구 안내 없음. 유예 계정은 최신 1건만 복구 대상 |

prod 배포는 고정 `container_name`으로 컨테이너를 재생성하므로 평소에는 구·신 서버가 동시에 뜨지 않는다.
겹치는 경우는 롤백뿐이다.

---

## 앱 팀 공유 사항

- 로그인 요청에 `withdrawalRestoreSupported: true`를 보내면, 유예 중인 계정이 있을 때
  `{ accessToken: null, refreshToken: null, nickname: null, pendingWithdrawal: { purgeAt } }`가 온다.
- 복구: `POST /api/v1/auth/oauth/{provider}/restore` `{ idToken }`. 로그인 때와 **같은 idToken**을 써도 된다.
  만료돼 401이 나면 소셜 로그인을 다시 해 새 idToken을 받는다.
- 새로 시작: 같은 로그인 API를 `withdrawalRestoreSupported: false`로 다시 호출.
- `/restore`가 404면(유예 만료 또는 구 서버) "새로 시작"으로 처리.
- **서버를 먼저 배포**한 뒤 앱을 배포한다.

미결 과제(Apple revoke 등)는 [`plan/withdrawal-followups.md`](plan/withdrawal-followups.md).
