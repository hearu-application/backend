---
paths:
  - "src/test/**/*.java"
---

# 테스트 컨벤션

JUnit 5 + Mockito + AssertJ. `@ExtendWith(MockitoExtension.class)`(단위 테스트에 Spring 컨텍스트 없음).
한글 `@DisplayName`, `@Nested` 그룹화, `@BeforeEach` 공유 셋업, Given-When-Then 구조. private ID는
`ReflectionTestUtils.setField(entity, "id", value)`로 주입. 이벤트 발행은 `ArgumentCaptor<ApplicationEvent>`로
검증. 성공 경로와 각 `BusinessException` 케이스를 모두 테스트. **참고:** `UserServiceTest`, `DiaryServiceTest`.

실행:

```bash
./gradlew test
./gradlew test --tests "com.example.hearu.user.service.UserServiceTest"   # 단일 클래스
```
