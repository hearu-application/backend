# 재배포 시 비동기 AI 응답 유실 (graceful shutdown)

## 문제 상황

**재배포할 때마다, 그 순간 처리 중이던 AI 응답이 유실된다.**

일기 저장은 `ai_response` 행을 `PENDING`으로 먼저 만든 뒤,
`DiaryAiResponseRequestedEvent`를 `AFTER_COMMIT` + `@Async`로 처리해 LLM(OpenAI)을 호출한다.
이 비동기 작업이 사라지면 그 행은 `COMPLETED`도 `FAILED`도 되지 못한 채 영구히 `PENDING`으로 남는다.

사용자에게는 **무한 로딩**이다. 실패 표시가 없으니 재시도 버튼조차 뜨지 않는다.

배포는 컨테이너를 교체하므로(`.github/workflows/prod.yml`) 매 배포가 이 상황을 만든다.

```
docker compose pull app
docker compose up -d --remove-orphans
```

특히 나쁜 점은 **관측되지 않는다**는 것이다. 예외가 터지지 않아 로그에도 남지 않고,
사용자는 신고 대신 이탈한다. 그래서 "문제가 생기면 그때 고치자"가 작동하지 않는다.

---

## 원인 분석

컨테이너 교체 시 SIGTERM이 JVM(PID 1)에 전달되고 Spring 종료 절차가 시작되는데,
그 과정에서 `@Async` 큐가 버려진다. 원인은 두 가지다.

### 1. `ThreadPoolTaskExecutor`가 종료 시 큐를 폐기한다

`ExecutorConfigurationSupport`의 기본값은 `waitForTasksToCompleteOnShutdown = false`이고,
이 경우 `shutdown()`은 아래처럼 동작한다.

```java
if (this.waitForTasksToCompleteOnShutdown) {
    this.executor.shutdown();
} else {
    for (Runnable remainingTask : this.executor.shutdownNow()) {
        cancelRemainingTask(remainingTask);   // 큐에 있던 작업을 취소
    }
}
```

즉 **대기 중인 작업은 폐기되고, 실행 중인 스레드는 인터럽트된다.**

### 2. lifecycle stop 단계가 큐를 구제하지 못한다

Spring 6.1+에는 위 종료 전에 "coordinated lifecycle stop" 단계가 있지만,
`ExecutorLifecycleDelegate.stop()`이 보는 것은 **실행 중** 작업 수뿐이다.

```java
if (this.executingTaskCount == 0) {
    this.stopCallback = null;
    callback.run();          // 큐 대기분과 무관하게 즉시 통과
}
```

`executingTaskCount`는 `beforeExecute`/`afterExecute`로 세는 값이라 큐 대기 건수를 포함하지 않는다.
따라서 이 단계는 큐를 비워 주지 못하고, 곧바로 1번의 `shutdownNow()`가 큐를 버린다.

---

## 문제 해결

### 1. 종료 시 큐를 끝까지 처리한다 — `AsyncConfig`

```java
// 종료 시 큐에 남은 작업까지 처리한다. 기본값(false)이면 shutdownNow()로 폐기되어 재배포마다 유실된다.
executor.setWaitForTasksToCompleteOnShutdown(true);

// 위 대기의 상한. compose의 stop_grace_period가 이 값보다 커야 SIGKILL이 먼저 오지 않는다.
executor.setAwaitTerminationSeconds(50);
```

`setTaskDecorator`와 마찬가지로 **`initialize()` 이전에** 설정해야 적용된다.

50초 근거: LLM 호출은 타임아웃(connect 3s + read 20s)과 `@Retryable` 1회 재시도(backoff 1s)를 포함해
**건당 최대 약 47초**(2 × 23s + 1s)다.

> 처음에는 30초였다. Clova 시절 read 타임아웃 6초 기준으로 "건당 약 19초"를 잡은 값인데, OpenAI로 바꾸면서
> read 타임아웃이 20초로 늘어 근거가 깨졌다. AI 응답 회수 작업([`ai-response-stuck-sweep.md`](ai-response-stuck-sweep.md))에서
> 50초로 올렸다.

> `spring.task.execution.shutdown.*` 프로퍼티로도 같은 설정이 가능하지만 이 프로젝트에는
> **적용되지 않는다.** `TaskExecutionAutoConfiguration`이 `@ConditionalOnMissingBean(Executor.class)`인데
> `AsyncConfig`가 직접 `Executor` 빈을 정의해 자동 설정이 물러나기 때문이다. 코드로 설정해야 한다.

### 2. Docker가 그때까지 기다리게 한다 — 전제조건

Compose의 `stop_grace_period` 기본값은 **10초**다. 위에서 50초를 벌어 놔도 10초에 SIGKILL이
날아오면 무의미하므로, 1번이 동작하기 위한 전제조건으로 함께 올린다.

```yaml
# 기본값 10s는 AI 큐 종료 대기 상한(AsyncConfig 50s)보다 짧아 SIGKILL이 먼저 온다.
stop_grace_period: 60s
```

`docker-compose.prod.yml`, `docker-compose.dev.yml` 양쪽에 적용한다.

이 값은 **기존 문제도 함께 해결한다.** `server.shutdown`이 이미 `graceful`이고 그 단계 타임아웃이
30초인데 Docker가 10초에 죽이고 있었으므로, 처리 중인 HTTP 요청도 보호받지 못하고 있었다.

### 3. `server.shutdown` — 변경하지 않는다

Spring Boot 3.5.7에서는 **이미 기본값이 `graceful`**이다(`ServerProperties`의
`private Shutdown shutdown = Shutdown.GRACEFUL;`).
"기본값은 `immediate`"라고 설명하는 자료가 많은데 구버전 기준이므로 주의.

### 검증한 기본값 (Spring Boot 3.5.7 아티팩트 기준)

`spring-boot-autoconfigure-3.5.7.jar`의 `META-INF/spring-configuration-metadata.json`에서 확인했다.

| 항목 | 기본값 | 조치 |
|---|---|---|
| `waitForTasksToCompleteOnShutdown` | `false` | **`true`로 변경** |
| `awaitTerminationMillis` | `0` | **50초로 변경** (처음 30초) |
| `stop_grace_period` (Compose) | `10s` | **60s로 변경** (처음 40s) |
| `server.shutdown` | `graceful` | 이미 활성 — 변경 없음 |
| `spring.lifecycle.timeout-per-shutdown-phase` | `30s` | 변경 없음 |

### 적용 시점

`stop_grace_period`는 **컨테이너 재생성 시점부터** 반영된다. 다음 배포의 종료 단계는 아직
옛 설정(10초)으로 동작하므로 **효과는 그다음 배포부터**다.
앞당기려면 `docker compose up -d --force-recreate`를 한 번 실행한다.

---

## 이 변경으로 해결되지 않는 것

위 조치는 **재배포로 인한 유실**만 막는다. `PENDING` 고착을 만드는 다른 경로는 남는다.

- **큐 포화** — capacity 30을 넘으면 기본 `AbortPolicy`가 `RejectedExecutionException`을 던져
  이벤트가 버려진다. 리스너가 아예 실행되지 않는다.
- **큐가 가득 찬 상태의 종료** — 30건을 코어 5스레드로 비우려면 최악 약 282초(30 ÷ 5 × 47초)라
  50초 대기로는 부족하다.
- **OOM·강제 종료** — SIGTERM 자체가 전달되지 않는다.
- **실패 기록(당시 `markFailed`)의 실패** — DB 장애 시 `AiResponseCaller`의 예외 처리 경로가 또 실패해
  종단 상태를 기록하지 못한다.

공통점은 **`FAILED`를 기록할 주체가 사라졌다**는 것이다. 이 경로는 고착된 `PENDING`을 주기적으로
다시 실행하는 회수 스케줄러로 덮었다([`ai-response-stuck-sweep.md`](ai-response-stuck-sweep.md)).

---

## 참고

- [Graceful Shutdown :: Spring Boot 3.5](https://docs.spring.io/spring-boot/3.5/reference/web/graceful-shutdown.html)
- [ServerProperties.java (3.5.x)](https://github.com/spring-projects/spring-boot/blob/3.5.x/spring-boot-project/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/web/ServerProperties.java)
- [ExecutorConfigurationSupport.java (6.2.x)](https://github.com/spring-projects/spring-framework/blob/6.2.x/spring-context/src/main/java/org/springframework/scheduling/concurrent/ExecutorConfigurationSupport.java)
- [ExecutorLifecycleDelegate.java (6.2.x)](https://github.com/spring-projects/spring-framework/blob/6.2.x/spring-context/src/main/java/org/springframework/scheduling/concurrent/ExecutorLifecycleDelegate.java)
- [Compose file reference — `stop_grace_period`](https://docs.docker.com/reference/compose-file/services/)
