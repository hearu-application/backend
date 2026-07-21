# Apple OAuth (Sign in with Apple) 구현

## 배경

기존에 Google, Kakao OAuth가 구현되어 있었다. 두 provider는 클라이언트가 넘긴 `idToken`을
서버에서 검증한 뒤 자체 JWT(access/refresh)를 발급하는 구조이며, `OauthProvider` 인터페이스로
추상화되어 있다. 여기에 동일한 구조로 **Apple 로그인**을 추가했다.

기존 `KakaoTokenVerifier`가 이미 `NimbusJwtDecoder` + JWKS 방식을 사용하고 있고, Apple도
JWKS 기반 검증이므로 **Kakao 패턴을 그대로 재사용**했다. 별도 의존성 추가 없음
(`spring-boot-starter-oauth2-resource-server`가 이미 존재).

---

## 핵심 의사결정

### 1. 복수 audience(aud) 허용

Apple은 안드로이드용 네이티브 SDK가 없어서 **플랫폼별로 구현 방식과 idToken의 `aud` 값이 다르다.**

| 플랫폼 | 구현 방식 | idToken의 aud 값 |
|---|---|---|
| iOS | 네이티브 SDK (`AuthenticationServices`) | **Bundle ID (App ID)** |
| Android | 웹 플로우 (Sign in with Apple JS) | **Service ID** |

hearu는 iOS/Android를 **둘 다 지원**하므로, 서버 검증은 등록된 client-id(Bundle ID + Service ID)
중 **하나라도 일치하면 통과**하도록 복수 audience를 허용한다.

### 2. email 필수 처리 (Kakao와 동일, 옵션 A)

- Apple은 인증 요청에 **email scope**를 포함하면 동의 화면에서 사용자가 email 공유를 완전히
  거부할 수 없다. (실제 이메일 또는 `@privaterelay.appleid.com` 릴레이 주소 중 택일)
- 따라서 **클라이언트가 항상 email scope를 요청**하는 것을 전제로, id_token에는 매 로그인마다
  email claim이 포함된다.
- "최초 1회만"이라는 제약은 form_post로 오는 `user` JSON 객체(이름 등)에 해당하며,
  **id_token 안의 email claim은 별개로 매번 포함**된다.
- 기존 Google/Kakao 로직과의 일관성, `User.email (nullable=false)` 제약을 고려해
  email이 없으면 예외 처리한다.

> ⚠️ 주의: 사용자가 **최초 로그인을 email scope 없이** 했다면 이후 scope를 넣어도 email이
> 오지 않는다(최초 동의 scope에 종속). 신규 연동이면 해당 없음. 클라이언트(iOS/Android)는
> 반드시 email scope를 요청해야 한다.

---

## Apple 공식 검증 스펙

| 항목 | 값 |
|---|---|
| JWKS 공개키 엔드포인트 | `https://appleid.apple.com/auth/keys` |
| Issuer (iss) | `https://appleid.apple.com` |
| Audience (aud) | 등록된 Bundle ID 또는 Service ID 중 하나와 일치 |
| 주요 claim | `sub`(고유 사용자 ID), `email` |
| 서명/만료 | JWKS 공개키로 서명 검증 + `exp` 만료 검증 |

검증 절차:
1. JWKS 엔드포인트에서 공개키 조회 (NimbusJwtDecoder가 `kid` 기준 자동 처리)
2. 공개키로 서명 검증
3. claim 검증: `iss` 일치, `aud` 포함 여부, `exp` 만료 여부
4. `sub`, `email` 추출

---

## 변경/추가 파일

### 1. `ProviderType` 에 APPLE 추가
`src/main/java/com/example/hearu/auth/domain/ProviderType.java`

```java
public enum ProviderType {
    GOOGLE,
    KAKAO,
    APPLE
}
```

### 2. `AppleTokenVerifier` (신규)
`src/main/java/com/example/hearu/auth/infrastructure/provider/AppleTokenVerifier.java`

- `NimbusJwtDecoder.withJwkSetUri("https://appleid.apple.com/auth/keys")` 로 디코더 구성
- `@PostConstruct`에서 `DelegatingOAuth2TokenValidator` 설정
  - `JwtTimestampValidator` (exp 검증)
  - `JwtIssuerValidator("https://appleid.apple.com")`
  - 복수 aud 검증: 설정된 client-id 목록 중 하나라도 token의 aud에 포함되면 통과
    ```java
    new JwtClaimValidator<List<String>>("aud",
        aud -> aud != null && aud.stream().anyMatch(appleClientIds::contains))
    ```
- `verifyToken(idToken)`: 디코딩 실패 시 `INVALID_ID_TOKEN`, `sub`/`email` 누락 시
  `MISSING_REQUIRED_CLAIMS` 예외 → `record Payload(String sub, String email)` 반환
- 설정값 주입: `@Value("${oauth.apple.client-ids}") List<String> appleClientIds`
  (콤마 구분 문자열을 Spring이 `List<String>`로 바인딩)

### 3. `AppleProvider` (신규)
`src/main/java/com/example/hearu/auth/infrastructure/provider/AppleProvider.java`

- `OauthProvider` 구현, `getProviderType()` → `ProviderType.APPLE`
- `getUserInfoFromOauthServer()` → verifier 호출 후 `new OauthUserInfo(sub, email)` 반환

### 4. 설정 추가
`src/main/resources/application.yml`

```yaml
oauth:
  kakao:
    client-id: ${KAKAO_REST_API_KEY}
  google:
    client-id: ${GOOGLE_CLIENT_ID}
  apple:
    client-ids: ${APPLE_CLIENT_IDS}   # 콤마 구분: "<iOS_Bundle_ID>,<Android_Service_ID>"
```

> `application-dev.yml` / `application-prod.yml`은 `oauth` 블록을 따로 정의하지 않아
> base `application.yml`의 설정이 모든 프로파일에 적용된다.

### 5. 문서 갱신
`CLAUDE.md` "Environment Variables" 섹션에 `APPLE_CLIENT_IDS` 추가.

---

## 자동 동작 (수정 불필요)

인터페이스 추상화 덕분에 다음은 변경 없이 그대로 동작한다.

- `OauthProviderFactory` — `AppleProvider` 빈을 자동 등록
- `AuthController` — `@PathVariable ProviderType provider`로 `APPLE` 자동 매핑
- `AuthService.registerOrLogin` — provider 종류와 무관하게 동작
- DTO(`OauthRequest`, `OauthUserInfo`), 에러 코드(`AuthErrorCode`) 재사용

---

## API 사용법

```
POST /api/v1/auth/oauth/APPLE
Content-Type: application/json

{
  "idToken": "<Apple이 발급한 id_token (JWT)>"
}
```

응답 (성공):
```json
{
  "message": "인증에 성공하였습니다.",
  "data": {
    "accessToken": "...",
    "refreshToken": "...",
    "nickname": "..."
  }
}
```

---

## 검증 / 테스트

- **컴파일**: `./gradlew compileJava` 통과
- **단위 테스트**: 기존에 `GoogleProviderTest`/`KakaoProviderTest`가 없으므로(provider/verifier는
  외부 JWKS 의존) 동일 컨벤션상 신규 verifier 단위 테스트는 생략.
- **엔드투엔드 수동 검증**
  1. 환경변수 `APPLE_CLIENT_IDS`에 실제 Bundle ID / Service ID 설정
     (예: `com.example.hearu,com.example.hearu.signin`)
  2. iOS/Android 클라이언트에서 Apple 로그인으로 받은 `idToken`을 위 엔드포인트로 전송
  3. 정상: `200 OK` + access/refresh token 발급, User 신규 생성 또는 기존 조회
  4. 변조/만료 토큰: `401` `INVALID_ID_TOKEN`
  5. aud 불일치(등록 안 된 client-id): `401` (검증 실패)

---

## 참고 자료

- [Authenticating users with Sign in with Apple — Apple Developer](https://developer.apple.com/documentation/signinwithapple/authenticating-users-with-sign-in-with-apple)
- [Verifying a user — Sign in with Apple REST API](https://developer.apple.com/documentation/sign_in_with_apple/sign_in_with_apple_rest_api/verifying_a_user)
- JWKS 엔드포인트: `https://appleid.apple.com/auth/keys`
