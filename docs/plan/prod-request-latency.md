# prod 요청 지연 조사

dev(Railway)에서는 재현되지 않고 prod에서만 나타난다. 코드는 동일하다.
아래는 전부 실측이며, 미확인은 그렇다고 표시했다. 측정 2026-08-05 ~ 08-06.

| # | 증상 | 원인 | 상태 |
|---|---|---|---|
| **A** | 모든 API 요청이 **0.8~0.9초** | DB가 인도 리전 | **확정** |
| **B** | 유휴 후 첫 요청이 **10~20초** | JVM 힙 페이지 스왑인 | **상관 확정 · 정량 미확인** |

두 문제는 독립적이고 조치도 별개다. 하나로 설명하려는 시도가 반복해서 실패했다.

---

## A — DB가 인도에 있다

| 측정 | 값 |
|---|---|
| 앱 서버 (RDAP `223.130.151.14`) | NAVER Cloud, **KR** |
| DB 리전 (Aiven 콘솔) | **인도** |
| DB 왕복 1회 (TCP 프로브 상시) | **133~169ms** |

**요청 시간의 98%가 DB 왕복이다.** 같은 엔드포인트를 DB 유무로 갈라 잰 값:

| 요청 | DB | `uht` |
|---|---|---|
| `GET /diaries/calendar` **401** (필터에서 거절) | 없음 | **0.017** |
| `GET /diaries/calendar` **200** | 있음 | **0.871** |

앱 로그의 단계별 간격도 같다 — JWT 서명 2회(로컬) **4ms**, 사용자 조회 SELECT 1회 **139ms**.

**조치가 막힌 지점** — Aiven free tier는 지역 그룹(Asia Pacific 등)만 고를 수 있고 구체 리전은
Aiven이 배정한다. 서울로 강제할 수단이 없다. 리전 지정은 유료 플랜에서만 가능하다.
목표는 "NCP Cloud DB"가 아니라 **"서울에 있는 DB"** 다 — 같은 서울이면 사업자는 무관하다.

---

## B — 유휴 후 첫 요청

### 재현 성공

재배포로 스왑을 초기화한 뒤 21시간 방치하니 증상이 그대로 돌아왔다.

| JVM `VmSwap` | 첫 요청 |
|---|---|
| 183MB (구 프로세스, 11일 가동) | **9.786초** |
| **4.8MB** (재배포 직후) | **1.95초** → 2회차 0.53초 |
| 174MB (21시간 후) | **16.955초** |
| 181MB (45시간 후) | **20.104초** |

**스왑이 없을 때만 빠르다.** 축적 속도는 4.8MB(0h) → 62MB(3h) → 174MB(21h) → 181MB(45h)로
하루 안에 포화한다. **재배포는 치료가 아니라 증상 리셋이다.**

재현 당시 세션:
```
23:52:41  health (10분 주기)      0.242   ← 2분 전까지 정상
23:54:45  oauth                  16.955
23:54:48  profile                 2.766
23:54:55  lock-setting/verify     1.138
23:54:58  calendar                2.547
```

### 뜨거운 경로와 차가운 경로가 갈린다

| 경로 | 밤새 | 아침 첫 요청 |
|---|---|---|
| `/actuator/health` | 10분마다 실행 → **메모리 상주** | **0.242초** (정상) |
| oauth·profile·calendar | 미사용 → **스왑아웃** | **16.955 → 2.8 → 1.1 → 2.5초** |

health는 DB 검증 쿼리를 포함하는데 밤새 0.236~0.274초로 일정했다.
**DB·네트워크가 정상인 상태에서 앱의 차가운 경로만 느렸다.**

### 원인 구조

`docker-compose.prod.yml`의 `app`에 메모리 제한이 없어 `UseContainerSupport`가
호스트 RAM(**961Mi**)을 기준으로 계산한다. 서버는 **1 vCPU**, GC는 **SerialGC**(`Using Serial`).

| `Dockerfile` 설정 | 결과 |
|---|---|
| `MaxRAMPercentage=75.0` | 힙 최대 **721Mi** |
| `InitialRAMPercentage=50.0` | 기동 시 **480Mi 커밋** (실제 RSS는 330MiB) |

쓰지 않는 힙 페이지가 스왑으로 밀려나고, 유휴 후 첫 요청이 그것을 다시 읽어온다.

### major page fault 관측

20.104초 요청이 걸친 1분 구간의 `majflt` 증가폭:

```
23:50:03  majflt=58206   ┐
23:51:03  majflt=59903   ┘ +1,697   ← 요청 구간
23:52:04  majflt=60350     +447
```

유휴 기준선은 **분당 0~30회**(최대 208회)다. 그 구간만 **+1,697회**로 튀었다.

`majflt`는 `/proc/<pid>/stat` 12번 필드이며 [proc(5)](https://man7.org/linux/man-pages/man5/proc_pid_stat.5.html)
정의는 *"major faults ... which have required loading a memory page from disk"* — **디스크를 탄
페이지 폴트만** 센다. 10번 필드 `minflt`는 디스크를 타지 않은 것이다.

세션 중 스왑이 오히려 늘었다(`swap_mb 476 → 500`, `jvm_swap 176 → 181MB`) — 페이지를
읽어오는 동시에 다른 페이지가 밀려나는 상태다.

### 정량 근거는 아직 없다

**"1,697회가 20초를 설명한다"는 확인되지 않았다.** 그러려면 폴트 1회당 지연을 알아야 하는데
측정한 적이 없다. 다음 두 가지 때문에 단순 곱셈으로 추정할 수도 없다.

- **Linux는 swap readahead를 한다.** [커널 문서](https://docs.kernel.org/admin-guide/sysctl/vm.html)상
  `page-cluster` 기본값 3 = **폴트 1회에 8페이지를 읽어온다.** 폴트 수와 읽은 바이트 수가
  1:1이 아니다.
- 폴트 1회당 디스크 지연을 재지 않았다.

**남은 측정** — `/proc/<pid>/stat` 42번 필드 `delayacct_blkio_ticks`
(*"Aggregated block I/O delays, measured in clock ticks (centiseconds)"*)가 프로세스가 블록 I/O로
대기한 시간을 직접 준다. 느린 요청 구간에서 이 값이 **약 2,000(=20초)** 만큼 뛰면 역산 없이
정량 확정된다. 커널의 delay accounting이 꺼져 있으면(`kernel.task_delayacct=0`) 항상 0으로
나오므로 먼저 확인해야 한다.

최종 증명은 **힙 설정을 고쳐 증상이 사라지는 것**이다. 아직 하지 않았다.

---

## 관찰 장치

전부 진단용. 원인 확정 후 제거한다.

| 장치 | 잡는 것 | 기준선 |
|---|---|---|
| nginx `log_format timing` | 요청별 `uht`, **401 대조군** | calendar 0.871 / 401 0.017 |
| `-Xlog:gc,safepoint` | JVM stop-the-world | 최대 **1.72ms** |
| `~/db-tcp-probe.log` | NCP↔Aiven 네트워크 | **133~169ms** |
| `~/swap-track.log` | 스왑·major fault | `majflt=23525` |

**401이 대조군인 이유** — 만료 토큰 요청은 필터에서 거절되어 DB를 타지 않는다.
앱을 오래 안 쓰다 열면 만료 토큰으로 401이 먼저 나가므로 대조군이 저절로 확보된다.

---

## 기각된 가설

| 가설 | 기각 근거 |
|---|---|
| JVM 워밍업 | 프로세스 생애 1회뿐인데 증상은 반복된다 |
| TLS 세션 만료 | 10분 유휴 후 cold−warm 차이 **0.076초** |
| Aiven이 유휴 커넥션을 끊음 | `wait_timeout = 28800`(8시간) |
| 커넥션이 죽어 검증 타임아웃 소진 | 8일간 `Failed to validate connection` **1건** |
| 커넥션 획득 타임아웃 × 재시도 | `Connection is not available` **0건** |
| Aiven free tier 절전 | 꺼졌다면 실패로 나타난다. 실측은 200 응답 |
| OOM kill 후 재시작 | `RestartCount 0` |
| JWKS 재조회 | prod 서버에서 직접 측정 **0.110~0.160초** |
| OAuth 경로 특유의 지연 | 2회차 로그인 총 **0.53초**. 1회차 1.537초는 클래스 로딩 |
| nginx upstream keepalive 부재 | `uct` 실측 **0.000~0.001초** |
| N+1 쿼리 | `getProfile`·`getCalendarDiaries` 모두 SELECT 1회 |
| **GC 정지** | 재현 시각에 safepoint 1초 이상 **0건** |
| **네트워크 열화** | 재현 시각에 TCP 프로브 300ms 초과 **0건** |
| **DB 서버 열화** | 밤새 health 0.236~0.274초로 일정 |

### 철회·정정한 것

- **"safepoint 정지 없음 → 스왑 아님"은 틀린 판정 기준이었다.** 페이지 폴트는 stop-the-world를
  일으키지 않는다. 해당 스레드만 디스크 I/O를 기다린다. safepoint 0건은 GC만 배제한다.
- **`0.12 + 왕복수 × 0.127` 모델** — 캘린더 SELECT 1회 제거로 −0.127초를 예상했으나 실측 −0.027초.
  왕복 수를 세는 도구로 쓸 수 없다. 남는 사실은 "요청 시간 대부분이 DB 왕복, 왕복 1회 133~169ms"뿐.
- **Hikari `max-lifetime` 단축** — 근거였던 `wait_timeout`이 8시간이라 성립하지 않는다.
- **기존 NCP 서버에 MySQL 컨테이너 추가** — 서버 스펙 확인 전의 제안. **961Mi에서는 불가능하다.**

---

## 조치

| # | 조치 | 비고 |
|---|---|---|
| A | DB를 서울로 이전 | free tier로는 불가. 덤프에 `flyway_schema_history`가 포함되므로 이력은 유지된다 |
| B | `InitialRAMPercentage=50.0` 제거 또는 절대값 고정, 혹은 서버 증설 | 실제 힙 사용량 미측정(`jcmd`가 JRE 이미지에 없음) |

**정리 시 제거** — `Dockerfile`의 `-Xlog:gc,safepoint`(양이 많고 로그 로테이션 없음),
`application-prod.yml`의 `com.example.hearu.auth: debug`, 서버의 프로브 프로세스·로그.
nginx `log_format timing`은 양이 적고 상시 유용하므로 남긴다.

---

## 별개 사실

액세스 토큰 만료가 **3분**이다. 3분 넘게 쉰 세션은 매번 refresh를 먼저 지불하고,
refresh 자체가 실측 **0.772초**이므로 A를 증폭시킨다. 다만 만료 시간은 보안 정책이라
지연을 이유로 바꿀 사안인지는 별도 판단이 필요하다.
