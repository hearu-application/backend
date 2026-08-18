# JWT 인증 하드닝 (설계 — 미구현)

JWT 인증은 `JwtProvider`(발급·파싱), `JwtKeyManager`(서명키), `JwtFilter`(요청 검증),
`RefreshTokenPolicy`·`RefreshTokenService`(회전)로 자체 구현되어 있다. 런타임 흐름 자체는
[`architecture.md`](../architecture.md) §4.4~4.5가 소유한다. 이 문서는 그 구현에서 발견한
결함과 대응만 다룬다.

| # | 결함 | 성격 | 확인 여부 |
|---|---|---|---|
| 1 | 만료된 access token을 헤더에 달면 permitAll인 재발급 API까지 401 | **잠복** — 클라이언트가 헤더를 붙이면 전면 장애 | 필터 동작은 **테스트로 확인**, 발동 여부는 미확인 |
| 2 | refresh token 재사용을 감지해도 세션을 폐기하지 않는다 | RFC 9700 미충족 | 코드로 확인 |
| 3 | refresh token을 평문으로 저장한다 | DB 유출 시 피해 확대 | 스키마로 확인 |
| 4 | 세션 테이블이 사용자당 1행이다 (PK = `userId`) | 다기기 불가 + 2번의 오탐 원인 | 코드로 확인 |
| 5 | 동시 refresh에 경쟁 조건이 있다 | 토큰 유실 → 재로그인 | 코드로 확인 |
| 6 | 토큰에 `jti`·`typ`·`iss`·`aud`가 없다 | 즉시 무효화 경로가 원천 봉쇄 | **테스트로 확인** |
| 7 | 서명키 검사가 길이만 본다 | 약한 키가 통과 | **테스트로 확인** |
| 8 | 키 로테이션 경로가 없다 | 키 교체 = 전체 강제 로그아웃 | 코드로 확인 |
| 9 | clock skew가 0이다 | 서버 시계 오차에 정상 토큰이 거부 | **테스트로 확인** |

로그아웃·탈퇴 후에도 access token이 만료까지 유효한 건 별개 문제이며
[`account-withdrawal-improvement.md`](account-withdrawal-improvement.md)가 소유한다.

---

## 이미 방어되고 있는 것

조사 과정에서 확인한, **고칠 필요가 없는** 항목이다. 나중에 "혹시 이것도?"로 다시 열지 않기 위해 남긴다.

| 공격 | 결과 |
|---|---|
| `alg=none` 무서명 토큰 | `UnsupportedJwtException` — jjwt가 `parseClaimsJws`에서 거부 |
| 알고리즘 혼동 (RS256 서명 토큰을 HMAC 키 파서에) | `UnsupportedJwtException` — 키 타입과 `alg` 불일치를 jjwt가 검사 |
| 다른 키로 서명한 토큰 | `SignatureException` |

커스텀 `type` 클레임(`ACCESS`/`REFRESH`)과 `JwtProvider.isAccessToken` 검사는
**RFC 8725 §3.12(서로 다른 종류의 JWT에 배타적 검증 규칙을 적용하라)를 충족한다.**
refresh token으로 API를 호출해도 인증되지 않는다. 이 설계는 유지한다.

---

## 확인 방법

임시 probe 테스트를 작성해 실행한 뒤 삭제했다. 결과는 아래와 같고, 재확인이 필요하면 같은 방식으로
다시 만든다(리포지토리에 남기지 않은 이유는 프로덕션 동작이 아니라 라이브러리 동작을 캐물은
일회성 조사이기 때문이다).

```
alg=none                → UnsupportedJwtException "Unsigned Claims JWTs are not supported"
RS256 토큰 + HMAC 키    → UnsupportedJwtException (알고리즘 혼동 차단)
다른 HMAC 키            → SignatureException
발급 토큰 header        → {"alg":"HS256"}                       ← typ 없음
발급 토큰 payload       → {sub, type, exp, iat}                 ← iss/aud/jti 모두 null
'a' 44자를 서명키로     → 33바이트로 디코딩되어 길이 검사 통과
1초 지난 만료 토큰      → ExpiredJwtException (skew 허용 없음)

POST /api/v1/auth/token/refresh + 만료된 access token 헤더
   → JwtFilter가 BadCredentialsException(cause=ExpiredJwtException)
   → chain.doFilter 미호출 (컨트롤러 도달 못함)
```

---

## 원인 분석

### 1. `JwtFilter`의 동작이 아니라 적용 범위가 문제다

`JwtFilter`에는 `shouldNotFilter` 오버라이드가 없다. 그래서 URI와 무관하게 토큰을 검증하고,
실패하면 `chain.doFilter` **이전에** `BadCredentialsException`을 던진다.
`permitAll` 판정은 그 뒤 `AuthorizationFilter`에서 일어나므로 요청은 컨트롤러에 닿지 못한다.

**던지는 것 자체는 옳다.** Spring Security의 `BearerTokenAuthenticationFilter`(6.5.6) 바이트코드를
확인한 결과 동작이 같다.

| 상황 | `BearerTokenAuthenticationFilter` | 현재 `JwtFilter` |
|---|---|---|
| 토큰 없음 | `chain.doFilter` — 통과 | 통과 (동일) |
| 토큰 있고 무효 | `clearContext()` → 즉시 401, **체인 중단** | throw → EntryPoint → 401, 체인 중단 (동일) |

즉 **무효 토큰을 즉시 거부하는 fail-fast는 프레임워크와 동일한 표준 동작이며 바꿀 이유가 없다.**

문제는 **그 필터가 토큰 발급 엔드포인트에도 걸려 있다는 것**이다. 표준 OAuth2 구성에서는
토큰 발급이 Authorization Server, 보호 자원이 Resource Server로 나뉘어 있어 bearer 필터가
발급 엔드포인트에 애초에 붙지 않는다. 이 프로젝트는 둘을 한 앱에 합쳤는데
**`SecurityFilterChain`은 하나뿐이라 분리가 사라졌다.** 결함 1은 그 결과다.

`SecurityConfig`가 `JwtFilter`를 `ExceptionTranslationFilter` **뒤에** 놓은 것도 의도대로
동작한다(던진 예외를 `ExceptionTranslationFilter`가 받아 `CustomAuthenticationEntryPoint`로 넘긴다).
필터 순서도, 필터 동작도 아니고, **체인 구성이 원인이다.**

#### 지금 당장 터지고 있지는 않다

발동 조건은 **클라이언트가 재발급 요청에 Authorization 헤더를 붙이는가** 하나다.
`docs/hearu-api-postman-collection.json`의 재발급 요청은 헤더가 `Content-Type` 하나뿐이므로,
**이 저장소가 정의한 API 계약상으로는 발동하지 않는다.** 실제 iOS·Android 앱이 인터셉터로 모든
요청에 토큰을 붙이는지는 백엔드 저장소에서 확인할 수 없다 — **미확인**이다.

그럼에도 고치는 이유는 **가용성이 "클라이언트가 헤더를 안 붙인다"는 약속에 의존**하고 있기
때문이다. 그 약속이 깨지는 순간 증상은 access token이 만료된 전 사용자의 강제 로그아웃이고,
무증상과 전면 장애 사이에 중간 단계가 없다. 서버가 스스로 방어할 수 있는 것을 클라이언트 규약에
맡겨 둘 이유가 없다.

### 2. 회전은 있으나 재사용 탐지가 없다

`RefreshTokenPolicy.validateStoredTokenMatch`는 입력 토큰이 저장값과 다르면
`INVALID_REFRESH_TOKEN`을 던지고 끝난다. 저장된 토큰은 그대로 살아 있다.

RFC 9700 §4.14는 회전을 쓰는 경우 재사용이 탐지되면
"MUST revoke all tokens issued previously based on that refresh token"을 요구한다.
지금은 탈취가 드러나도 **공격자도 정상 사용자도 차단되지 않고**, 운영자에게 남는 신호는
`log.warn` 한 줄뿐이다.

### 3. 세션 테이블이 사용자당 1행이다

`RefreshToken`의 PK가 `userId`라서 새 로그인이 이전 기기의 세션을 조용히 덮어쓴다.
폰과 태블릿을 함께 쓰면 한쪽이 로그아웃된다.

이건 단순한 기능 제약이 아니라 **2번의 선행 조건**이다. 세션이 1행뿐인 상태에서 재사용 탐지를
넣으면, 정상적인 두 번째 기기 로그인이 재사용처럼 보여 오탐이 쏟아진다. **2·3·4는 한 번에 고쳐야 한다.**

### 4. `jti`가 없으면 무효화 수단 자체가 없다

OWASP JWT 치트시트가 제시하는 즉시 무효화는 `(jti, iss)`를 키로 한 denylist다.
현재 토큰에는 `jti`가 없으므로 이 방식을 **선택할 수조차 없다.** 나중에 "강제 로그아웃"
요구가 생기면 토큰 포맷부터 바꿔야 하고, 그 시점엔 이미 발급된 토큰과의 호환을 고민해야 한다.
지금 넣어 두는 비용이 압도적으로 싸다.

`iss`·`aud`는 발급자도 소비자도 하나뿐인 현재 구조에서 실질 위험이 낮다. 다만 RFC 8725 §3.8·3.9가
권고하고 추가 비용이 없으므로 함께 넣는다.

### 5. 서명키 검사는 길이만 본다

`JwtKeyManager`는 Base64 디코딩 후 32바이트 이상인지만 확인한다. `a` 44자는 33바이트로
디코딩되어 통과한다. RFC 8725 §3.5는 **사람이 기억할 수 있는 패스워드를 HS256 키로 직접 쓰지 말라**고
명시한다. 현재 검사는 사람이 지은 패스프레이즈를 걸러내지 못한다.

부수적으로, URL-safe Base64(`-`·`_`)나 한글이 섞인 값은 `Base64.getDecoder()`가 예외를 던져
**기동에 실패한다.** fail-fast라 동작은 옳지만 예외 메시지가 원인을 알려주지 않는다.

---

## 문제 해결

### 1. `SecurityFilterChain`을 인증용과 자원용으로 분리한다

**`JwtFilter`도 `CustomAuthenticationEntryPoint`도 고치지 않는다.** 원인이 체인 구성이므로
체인만 나눈다.

| 체인 | `@Order` | `securityMatcher` | `JwtFilter` |
|---|---|---|---|
| 인증용 | 1 | `/api/v1/auth/**` | **없음** |
| 자원용 | 2 (기본) | 나머지 | 있음 (현행 유지) |

인증용 체인에는 `JwtFilter`가 아예 없으므로 Authorization 헤더에 무엇이 실려 오든 무시되고
컨트롤러에 도달한다. 자원용 체인은 지금과 완전히 동일하게 동작한다 — **fail-fast도, 만료·서명·포맷을
구분하는 `SecurityServletErrorCode`도 손대지 않으므로 저절로 보존된다.**

이건 **한 앱이 토큰 발급과 보호 자원을 함께 호스팅할 때 Spring이 제시하는 정본 구성이다.**
Spring Authorization Server 레퍼런스가 `@Order(1)` + `securityMatcher`로 프로토콜 엔드포인트
체인을 먼저 두고 자원 체인을 뒤에 두는 것과 같은 형태다.

`csrf`·`cors`·`sessionManagement`·`httpBasic` 비활성화 등 공통 설정은 두 체인에 모두 필요하다.
private 메서드 하나로 묶어 양쪽에서 호출한다 — 복사하면 한쪽만 바뀌는 순간 어긋난다.

`/actuator/health`·`/swagger-ui/**`·`/v3/api-docs/**`도 `permitAll`이지만 인증용 체인으로 옮기지
않는다. 이 경로들은 Authorization 헤더를 동반하지 않으므로 결함 1의 대상이 아니고, 옮기면
인증용 체인의 의미가 "인증 엔드포인트"에서 "permitAll 잡동사니"로 흐려진다.

### 2. 세션 테이블 재설계 — 2·3·4를 한 번에

`refresh_token`을 사용자당 N행으로 바꾸고, 토큰은 해시로만 보관한다.

| 컬럼 | 용도 |
|---|---|
| `id` | PK (surrogate) |
| `user_id` | 소유자. 인덱스 |
| `token_hash` | SHA-256 hex. **원문은 저장하지 않는다.** UNIQUE |
| `family_id` | 회전 계보. 최초 로그인에서 생성, 회전해도 유지 |
| `expires_at` | 기존과 동일. 정리 스케줄러가 사용 |

해시로 바꿔도 검증은 성립한다. 서버는 "정확히 이 토큰인가"만 판정하면 되고, 불일치는 거부로
끝나므로 fail-closed다. (OWASP가 경고하는 "해시를 키로 쓰지 말라"는 **denylist**에 대한 것이고,
여기처럼 정확 일치를 요구하는 allowlist에는 해당하지 않는다.)

`RefreshToken`이 `BaseEntity`를 상속하지 않는 예외는 그대로 유지한다 — 여전히 만료 시 하드 삭제 대상이다.

### 3. 재사용 탐지 → family 폐기 + 알림

`RefreshTokenPolicy.validateStoredTokenMatch`가 불일치를 감지하면 거부만 하지 말고,
**해당 `family_id`의 모든 행을 삭제**한다. 공격자와 정상 사용자 모두 재로그인해야 하지만,
그게 RFC 9700이 요구하는 동작이고 탈취 상황에서 옳은 선택이다.

알림은 기존 `DiscordNotifierClient`를 재사용한다. 로그 레벨과 태그 규칙은
[`.claude/rules/logging.md`](../../.claude/rules/logging.md)를 따른다 — 정의상 비정상이고
확인이 필요하므로 WARN 이상이다. **userId 외에 토큰 원문·해시는 남기지 않는다.**

### 4. 동시 refresh — 낙관적 락

`RefreshTokenService.getNewRefreshTokenAndAccessToken`은 read-then-update이고 엔티티에 `@Version`이
없다. 네트워크 재시도나 앱의 동시 요청 2건이 같은 토큰으로 들어오면 둘 다 검증을 통과하고
나중 쓰기가 이긴다. 진 쪽의 응답을 받은 클라이언트는 이미 무효인 토큰을 쥔다.

`@Version`을 추가하고, 충돌 시에는 재시도하지 말고 그대로 실패시킨다(재시도하면 3번의 재사용
탐지와 구분이 안 된다). 클라이언트는 한 번 더 refresh하면 된다.

### 5. 클레임 보강

| 위치 | 항목 | 값 |
|---|---|---|
| header | `typ` | `JWT` (RFC 8725 §3.11 명시적 타이핑) |
| payload | `jti` | UUID. 무효화 수단을 위한 자리 확보 |
| payload | `iss` | 서비스 식별자. 설정값으로 주입 |
| payload | `aud` | 클라이언트 식별자 |

`iss`·`aud`는 **발급과 검증을 같은 커밋에 넣는다.** 발급만 하고 검증하지 않으면 아무 효과가 없다.

기존 토큰과의 호환: 검증을 필수로 만들면 이미 발급된 토큰이 전부 거부된다. access token은 수명이
짧아 무시할 수 있지만 refresh token은 그렇지 않다. **claim 검증은 "있으면 검사, 없으면 통과"로
시작해 refresh token 최대 수명이 지난 뒤 필수로 승격한다.**

### 6. 서명키와 clock skew

- `JwtKeyManager`에 **"base64로 인코딩한 32바이트 난수"**라는 기대 형식을 예외 메시지와 주석에
  명시한다. 현재 메시지("최소 256bit")는 사용자가 무엇을 넣어야 하는지 알려주지 않는다.
- `Base64.getDecoder()` 실패도 같은 자리에서 잡아 원인을 밝힌다.
- 엔트로피를 코드로 완벽히 검증할 수는 없다. 기동 시 경고 수준까지가 현실적이다.
- `JwtProvider.parseClaims`의 파서에 clock skew 허용치를 준다. Spring Security 기본값이 60초이므로
  그보다 크게 잡을 이유는 없다.

### 7. 키 로테이션 — `kid`

발급 시 header에 `kid`를 넣고, 검증은 `kid`로 키를 고른다. `JwtKeyManager`가 키 1개가 아니라
**현재 서명 키 + 검증만 허용하는 이전 키들**을 갖도록 바꾼다. 이게 없으면 키 유출 시 교체가
전체 사용자 강제 로그아웃과 동의어가 된다.

5번의 `jti`와 달리 이건 급하지 않다. 다만 **`kid` 헤더를 넣는 것만이라도 5번과 같은 커밋에
해 두면**, 나중에 실제 로테이션을 붙일 때 발급 포맷을 다시 건드리지 않아도 된다.

---

## 우선순위

| 순위 | 항목 | 이유 |
|---|---|---|
| 1 | 해결 1 (체인 분리) | 기존 필터·핸들러를 건드리지 않아 회귀 위험이 가장 낮다. 지금 터지고 있진 않지만 터지면 **전면 장애**이고, 트리거가 서버 밖(클라이언트 구현)에 있어 예고 없이 발동한다 |
| 2 | 해결 2·3·4 (세션 테이블 + 재사용 탐지 + 락) | 마이그레이션을 공유한다. 4 없이 3만 넣으면 오탐이 난다 |
| 3 | 해결 5·6 (클레임·키·skew) | 저비용. 나중에 할수록 호환 처리가 커진다 |
| 4 | 해결 7 (로테이션) | 사고가 나기 전엔 티가 안 나지만, 나면 대안이 없다 |

---

## 변경 파일

| 파일 | 변경 |
|---|---|
| `V?__redesign_refresh_token.sql` | 신규 — 아래 마이그레이션 참조 |
| `SecurityConfig` | `SecurityFilterChain` 2개로 분리(`@Order(1)` 인증용 + 자원용), 공통 설정 추출 |
| `JwtFilter` | **무수정** (결함 1 대응에 한해) |
| `CustomAuthenticationEntryPoint` | **무수정** |
| `JwtProvider` | `jti`·`iss`·`aud`·`typ`·`kid` 발급, 파서에 clock skew·검증 규칙 |
| `JwtKeyManager` | 키 맵(`kid` → key), 예외 메시지 개선 |
| `RefreshToken` | `id` PK, `tokenHash`, `familyId`, `@Version` |
| `AuthRepository` | `findByTokenHash`, `deleteByFamilyId`, 기존 `deleteByExpiresAtBefore` 유지 |
| `RefreshTokenPolicy` | 해시 비교, 불일치 시 family 폐기 위임 |
| `RefreshTokenService` | `issueInitialToken` 다중 세션화, 회전 시 family 유지 |
| `UserService.logout` | userId 전체 삭제 → 해당 세션만 삭제로 변경 검토 |
| `application.yml` | `jwt.issuer`, `jwt.audience`, `jwt.clock-skew` |

### 마이그레이션

**버전 번호는 구현 시점에 정한다.** `ai-response-stuck-sweep.md`도 다음 번호를 예약해 두었으므로
둘 중 먼저 머지되는 쪽이 앞 번호를 갖는다. 절차와 불변식은
[`.claude/rules/db-migration.md`](../../.claude/rules/db-migration.md)를 따른다.

기존 행은 **버리고 시작한다.** 평문 토큰에서 해시를 만들어 옮길 수는 있지만, `family_id`를
소급 생성할 근거가 없고 전 사용자 재로그인은 refresh token 수명 안에 자연히 수렴한다.
배포 시점을 트래픽이 적은 시간대로 잡는 것으로 충분하다.

---

## 검증 방법

`.claude/rules/testing.md`를 따르되, 이 작업에서 반드시 덮어야 하는 케이스는 다음과 같다.

| 대상 | 확인할 것 |
|---|---|
| 체인 분리 | 만료 토큰 + `/api/v1/auth/token/refresh` → **재발급에 성공한다** (현재는 401) |
| 체인 분리 | 위조·형식 오류 토큰을 달아도 재발급에 성공한다 (헤더가 무시되는지) |
| 회귀 | 만료 토큰 + `/api/v1/diaries` → 여전히 401 `EXPIRED_JWT_TOKEN` |
| 회귀 | 서명 불일치·포맷 오류가 각각 고유 `SecurityServletErrorCode`를 유지한다 |
| 회귀 | 토큰 없이 `/api/v1/diaries` → 기존과 동일한 401 |
| 회귀 | CORS 프리플라이트가 두 체인 모두에서 동작한다 (공통 설정 누락 검출) |
| 재사용 탐지 | 회전된 구 토큰으로 재요청 → 401 **이고 family의 모든 행이 사라진다** |
| 다기기 | 두 번째 로그인이 첫 번째 세션을 무효화하지 않는다 |
| 동시성 | 같은 토큰으로 동시 refresh 2건 → 하나만 성공 |
| 해시 저장 | DB에 토큰 원문이 없다 |
| 클레임 | `iss`·`aud` 불일치 토큰이 거부된다 |
| 로그 | 토큰 원문·해시가 어디에도 남지 않는다 |

---

## 채택하지 않은 대안

| 대안 | 이유 |
|---|---|
| **Spring Security OAuth2 Resource Server** (`NimbusJwtDecoder.withSecretKey`) | 커스텀 필터를 없애고 exp/nbf 검증·기본 skew·`OAuth2TokenValidator` 조합을 얻는다. **의존성은 이미 있다** — `spring-boot-starter-oauth2-resource-server`가 `build.gradle`에 있고, 현재는 외부 IdP의 id_token 검증(`GoogleTokenVerifier`·`KakaoTokenVerifier`·`AppleTokenVerifier`의 `NimbusJwtDecoder`)에만 쓰인다. `oauth2ResourceServer()` 설정은 어디에도 없다. 즉 추가 비용은 설정과 에러 응답 재작성뿐이다. 다만 응답이 RFC 6750 형식으로 고정되어 `SecurityServletErrorCode` 기반의 세분화된 코드 체계를 다시 만들어야 한다. **위 해결 1~6을 끝낸 뒤 별도 안건으로 재검토한다** — 지금 섞으면 무엇이 무엇을 고쳤는지 구분되지 않는다. 해결 1의 체인 분리는 이 전환의 선행 작업이기도 하다 |
| 1번 대응으로 `JwtFilter`가 예외를 삼키고 익명 통과 (+ request attribute stash) | 동작은 성립한다(`ExceptionTranslationFilter`가 익명의 `AccessDeniedException`을 401로 보내는 것을 바이트코드로 확인). 그러나 **fail-fast를 버리는 것이고, 이는 `BearerTokenAuthenticationFilter`의 표준 동작에 역행한다.** 게다가 에러 코드를 살리려면 attribute stash라는 관례 밖 장치가 필요하다. 결함 1의 원인은 필터 동작이 아니라 체인 구성이므로 필터를 바꾸는 건 잘못된 지점을 고치는 것이다 |
| 1번 대응으로 `shouldNotFilter` 경로 제외 | 제외 목록을 `SecurityConfig`의 `permitAll`과 이중 관리해야 하고, 새 permitAll 경로에서 같은 버그가 재발한다. 체인 분리는 경로 정의가 matcher 한 곳에만 존재한다 |
| 비대칭키(RS256/ES256) + JWKS | 검증 주체가 여럿일 때 의미가 있다. 단일 모놀리스에는 운영 비용만 늘어난다 |
| 불투명 토큰 + Redis 세션 | 즉시 무효화가 요구사항이 되면 가장 단순한 정답이다. 다만 Redis가 새 인프라 의존성이고, 지금은 즉시 무효화가 요구사항이 아니다 |
| `tokenVersion` 클레임 + 매 요청 대조 | denylist보다 저장 비용이 낮지만 **모든 요청이 DB를 친다.** 현재 `JwtFilter`가 DB를 전혀 조회하지 않는 이점을 버리게 된다 |
| DPoP / mTLS 송신자 제약 (RFC 9700 권고) | 모바일 앱 한 종류에는 과하다 |
| jjwt 0.12.x 업그레이드 | **하려면 이 작업과 분리한다.** CVE-2024-31033은 **철회·논쟁 중**이므로 보안 근거로 삼으면 안 된다. 실제 이유는 0.11.x의 API가 전면 deprecated라는 것뿐이고, 지금 올리면 위 변경의 diff에 무관한 API 치환이 섞인다 |

---

## 결정이 필요한 것

| 항목 | 선택지 | 막히는 작업 |
|---|---|---|
| 사용자당 최대 세션 수 | 무제한 / N개(초과 시 가장 오래된 것 폐기) | 세션 테이블 재설계 |
| 로그아웃 범위 | 해당 기기만 / 전체 기기 | `UserService.logout` |
| `iss`·`aud` 값 | 서비스·클라이언트 식별자 문자열 확정 | 클레임 보강 |
| 클레임 검증 승격 시점 | refresh token 최대 수명 경과 후 | 선택적 검증 → 필수 전환 |
| access token 수명 | 현재 `ACCESS_TOKEN_EXPIRE_TIME` 실값 미확인 | 무효화 지연 창의 크기 판단 |

---

## 참고

- [RFC 8725 — JSON Web Token Best Current Practices](https://www.rfc-editor.org/rfc/rfc8725.html) (§3.5 키 엔트로피, §3.8·3.9 iss·aud, §3.11 typ, §3.12 배타적 검증)
- [RFC 9700 — Best Current Practice for OAuth 2.0 Security](https://www.rfc-editor.org/rfc/rfc9700.html) (§4.14 refresh token 보호·회전·재사용 탐지)
- [OWASP JSON Web Token Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_Cheat_Sheet.html)
- [Spring Security — Architecture](https://docs.spring.io/spring-security/reference/servlet/architecture.html) (`ExceptionTranslationFilter`의 401/403 분기, 필터 순서)
- [Spring Authorization Server — Configuration Model](https://docs.spring.io/spring-authorization-server/reference/configuration-model.html) (`@Order(1)` + `securityMatcher`로 프로토콜 엔드포인트 체인 분리)
- [Spring Security — OAuth 2.0 Resource Server JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
- [GHSA-r65j-6h5f-4f92 (CVE-2024-31033) — 철회됨](https://github.com/advisories/GHSA-r65j-6h5f-4f92)
