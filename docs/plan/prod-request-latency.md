# prod 요청 지연 조사

dev(Railway)에서는 재현되지 않고 prod에서만 나타난다. 코드는 동일하다.
조사 기간 2026-08-05 ~ 08-13.

**실측값과 공식 문서에서 확인한 것만 적는다.** 확인하지 못한 것은 "미확인"으로 남긴다.

| # | 증상 | 원인 | 상태 |
|---|---|---|---|
| **A** | 모든 요청이 **0.75~0.94초** | DB가 인도 리전 | **미조치** |
| **B** | 유휴 후 첫 요청이 **21.9초** | JVM 힙 페이지 스왑인 | **해결 → 2.08초** |

두 문제가 섞여 있었다. "20초"를 A로 설명하려는 시도가 반복해서 실패했고, 분리한 뒤에야 각각 풀렸다.

---

## A — DB가 인도 리전에 있다

| 측정 | 값 | 방법 |
|---|---|---|
| 앱 서버 위치 | NAVER Cloud Corp, `NBP-NET-KR`, KR | `223.130.151.14` RDAP |
| DB 리전 | 인도 | Aiven 콘솔 |
| **DB 왕복 1회** | **133~169ms** | `</dev/tcp/HOST/PORT` 상시 프로브 |
| `wait_timeout` | 28800 | `SHOW VARIABLES` |

Aiven은 리전을 SQL로 노출하지 않는다(`SELECT @@hostname` → `hearu-4`).

### DB 유무로 갈라 잰 값 (nginx `uht` = 앱 처리 시간)

| 요청 | DB | `uht` |
|---|---|---|
| `GET /diaries/calendar` **401** (필터에서 거절) | 없음 | **0.017** |
| `GET /actuator/health` (검증 쿼리, 트랜잭션 없음) | 1회 | **0.230~0.275** |
| `GET /users/profile` **200** | 있음 | **0.729~0.900** |
| `GET /diaries/calendar` **200** | 있음 | **0.776~0.935** |

`UserService.getProfile`과 `DiaryService.getCalendarDiaries`는 각각 SELECT 1회다
(`findCalendarDiaries`는 DTO 프로젝션).

앱 내부 로그의 단계별 간격:

| 작업 | 측정 |
|---|---|
| JWT 서명 2회 (로컬 연산) | **4ms** |
| 사용자 조회 SELECT 1회 | **139ms** |
| `refresh token` 저장 (SELECT + UPDATE) | **242ms** |

**앱 자체는 17ms면 끝난다.** 나머지는 DB 왕복이다.

### 조치가 막힌 지점

[Aiven 문서](https://aiven.io/docs/products/kafka/free-tier/create-free-tier-kafka-service)상 free tier는
**지역 그룹(Asia Pacific / Australia / Europe / North America)만** 선택할 수 있고 클라우드 제공자와
구체 리전은 Aiven이 배정한다. 리전 지정과 콘솔 내 이전은 유료 플랜에서만 가능하다.

**목표는 "NCP Cloud DB"가 아니라 "서울에 있는 DB"다.** 같은 서울이면 사업자는 무관하다.

---

## B — 스왑인 (해결)

### 재현

| 시점 | JVM `VmSwap` | 첫 요청 `uht` |
|---|---|---|
| 08-06 아침 | 174MB | **16.955초** |
| 08-07 아침 | 181MB | **20.104초** |
| 08-08 아침 | 144MB | **21.902초** |
| 재배포 직후 | 4.8MB | **1.95초** |

같은 세션의 후속 요청은 매번 0.83~0.99초로 돌아왔다.

### 느린 요청 구간의 실측

08-08 아침 20.104초 요청이 걸친 구간:

```
23:50:03  majflt=58206  jvm_swap_kb=147968
23:51:03  majflt=59903  jvm_swap_kb=122624   +1,697 / −24.8MB
23:52:04  majflt=60350  jvm_swap_kb=113280   +447   / −9.1MB
```

유휴 시 `majflt` 기준선은 분당 0~30회다.
[proc(5)](https://man7.org/linux/man-pages/man5/proc_pid_stat.5.html)의 12번 필드 `majflt` 정의는
*"major faults ... which have required loading a memory page from disk"* 다.

### 원인

| 항목 | 값 | 방법 |
|---|---|---|
| RAM | **961Mi** | `free -h` |
| CPU | **1** | `nproc` |
| GC | **SerialGC** | `[gc] Using Serial` |
| `swappiness` | 60 | `/proc/sys/vm/swappiness` |
| live set | **39~43MB** | `GC(34) 71M->39M(123M)` |

`docker-compose.prod.yml`의 `app`에 메모리 제한이 없어 `docker stats`의 LIMIT이 호스트 RAM과 같았다.
그 상태에서 `Dockerfile`의 `MaxRAMPercentage=75.0` / `InitialRAMPercentage=50.0`은
**힙 최대 721Mi / 기동 시 480Mi 커밋**이 된다. 실제 live set은 39~43MB다.

`ps aux --sort=-rss` 상위:

| 프로세스 | RSS |
|---|---|
| `java -jar app.jar` | 245,908 kB |
| **`fwupd`** | **115,548 kB** |
| `systemd-journald` | 53,444 kB |
| `dockerd` | 45,332 kB |
| `containerd` | 24,576 kB |

### 적용한 조치

| 조치 | 측정 |
|---|---|
| 힙을 `-Xms128m -Xmx320m` 절대값으로 고정 | 스왑 183MB → 144MB (증상 유지) |
| `fwupd` 중지 (`systemctl mask` + `stop`) | `available` 234Mi → 374Mi, 전체 스왑 404Mi → 317Mi |
| `vm.swappiness` 60 → 10 | 단독 효과 미측정 |

`fwupd`는 D-Bus 활성화 유닛이라 `disable`이 듣지 않아 `mask`를 썼다.
**힙 축소만으로는 증상이 사라지지 않았다** — 세 조치를 다 적용한 뒤에 해소됐다.

### 결과

| | 조치 전 | 조치 후 |
|---|---|---|
| 유휴 후 첫 요청 | **21.902초** (08-08) | **2.081초** (08-13, 약 22시간 유휴) |
| 세션 중 `majflt` 증가 | **+7,129** | **+50** |
| JVM `VmSwap` | 144MB | **23.6MB** |
| 세션 중 CPU 비율 | 미측정 | 8.4% |

---

## 관찰 장치

| 장치 | 잡는 것 | 기준선 |
|---|---|---|
| nginx `log_format timing` | 요청별 `rt`/`uct`/`uht`/`urt`, 401 대조군 | calendar 0.83 / 401 0.017 |
| `-Xlog:gc,safepoint` | GC 정지 | safepoint `Total` 최대 **1.72ms** |
| `~/db-tcp-probe.log` | NCP↔Aiven 네트워크 | 133~169ms |
| `~/swap-track.log` | `VmSwap`, minflt, majflt, utime, stime | `majflt` 유휴 시 분당 0~30 |

**401이 대조군인 이유** — 만료 토큰 요청은 필터에서 거절되어 DB를 타지 않는다.
앱을 오래 안 쓰다 열면 만료 토큰으로 401이 먼저 나가므로 대조군이 저절로 확보된다.

**프로브 해상도** — `swap-track`은 60초 간격이라 수 초짜리 요청을 정확히 가두지 못한다.
정밀 측정은 요청 직전·직후에 `/proc/1/stat`을 직접 읽었다.

`nginx` 컨테이너에는 `TZ`가 없어 로그가 **UTC**다(`app`에만 `TZ: Asia/Seoul`). 대조 시 9시간 차이에 주의.

---

## 기각된 가설

| 가설 | 기각 근거 |
|---|---|
| TLS 세션 만료 | 10분 유휴 후 cold−warm 차이 **0.076초** |
| Aiven이 유휴 커넥션을 끊음 | `wait_timeout = 28800` |
| 커넥션이 죽어 검증 타임아웃 소진 | 8일간 `Failed to validate connection` **1건** |
| 커넥션 획득 타임아웃 × 재시도 | `Connection is not available` **0건** |
| Aiven free tier 절전 | 실측은 200 응답 |
| OOM kill 후 재시작 | `RestartCount 0` |
| nginx upstream keepalive 부재 | `uct` **0.000~0.001초** |
| N+1 쿼리 | `getProfile`·`getCalendarDiaries` 모두 SELECT 1회 |
| GC 정지 | safepoint `Total` 최대 **1.72ms** |
| 네트워크 열화 | TCP 프로브 300ms 초과 **0건** |
| DB 서버 열화 | 밤새 health `uht` 0.230~0.275로 일정 |

---

## 정정한 것

- **`delayacct_blkio_ticks`(42번 필드)로는 스왑인 시간을 잴 수 없다.** `majflt`가 +7,129인
  구간에서도 이 값은 63에서 변하지 않았다. proc(5) 정의는 *"Aggregated block I/O delays"* 다.
- **"safepoint 정지 없음 → 스왑 아님"은 틀린 판정 기준이었다.** 페이지 폴트는 stop-the-world를
  일으키지 않으므로 safepoint 0건은 GC만 배제한다.
- **`major fault 1회 = 1페이지`가 아니다.** [커널 문서](https://docs.kernel.org/admin-guide/sysctl/vm.html)상
  `page-cluster` 기본값 3 = 폴트 1회에 8페이지를 읽는다(이 서버 실측값도 3).
- **`0.12 + 왕복수 × 0.127` 모델은 폐기했다.** 캘린더 SELECT 1회 제거로 −0.127초를 예상했으나
  실측은 **−0.027초**였다.
- **Hikari `max-lifetime` 단축** — 근거였던 `wait_timeout`이 8시간이라 성립하지 않는다.
- **기존 NCP 서버에 MySQL 컨테이너 추가** — 서버 스펙 확인 전의 제안. 961Mi에서는 불가능하다.

---

## 서버에 적용한 설정 (저장소 밖)

| 항목 | 값 | 영속화 |
|---|---|---|
| `fwupd` | `systemctl mask` + `stop` | 심볼릭 링크 |
| `vm.swappiness` | 10 | `/etc/sysctl.conf` |
| `kernel.task_delayacct` | 1 | `/etc/sysctl.conf` |

되돌리려면 `systemctl unmask fwupd`.

## 정리 시 제거할 것

- `Dockerfile`의 `-Xlog:gc,safepoint` (양이 많고 로그 로테이션 설정이 없다)
- `application-prod.yml`의 `com.example.hearu.auth: debug`, `com.example.hearu.common.logging: debug`
- 서버의 프로브 프로세스·로그

nginx `log_format timing`은 양이 적고 상시 유용하므로 남긴다.
**DB 이전 후 효과를 비교하려면 프로브도 그때까지 유지한다.**

---

## 남은 작업

**DB를 서울 리전으로 이전한다.** 지금 남은 지연의 거의 전부다 — 401이 0.017초인데 DB를 타는 요청은
0.83초다. free tier로는 리전을 지정할 수 없으므로 유료 플랜이나 다른 제공자가 필요하다.

## 별개 사실

액세스 토큰 만료가 **3분**이다. 3분 넘게 쉰 세션은 refresh를 먼저 지불하고, refresh 자체가
실측 **0.772초**다.
