# 인증서 갱신 견고화 (설계 — Step 1 구현 완료, prod 배포·검증 대기)

> 2026-09-16 prod 인증서 만료 장애의 후속. 그 장애는 `docker-compose.prod.yml`의 certbot
> deploy-hook(`docker exec ...`)이 이미지에 docker CLI가 없어 검증 단계에서 `certbot renew`를
> 통째로 중단시켜, **자동 갱신이 한 번도 성공하지 못한 채** 인증서가 만료된 것이었다.
> hook 제거로 자동 갱신 자체는 복구했다(커밋 `443e9af`).
>
> **방향: 리버스 프록시를 바꾸거나(Caddy) 앞단에 벤더를 두지 않고(Cloudflare), 현재 nginx+certbot
> 스택을 그대로 유지하면서 "조용한 실패"를 시끄럽게 만드는 데 집중한다.** 왜 이 방향인지는 아래 참고.

## 왜 스택을 안 바꾸나 (검토 기록)

- **Cloudflare 앞단** — 인증서 관리를 이관하는 본질 해결이지만, 프록시를 쓰려면 도메인 NS를 Cloudflare로
  넘겨야 한다. 우리 도메인 `hearu.p-e.kr`은 내도메인.한국 **무료 서브도메인**이라 NS 위임이 되는지부터
  불확실하고, SSL 모드 footgun·벤더 의존이 붙는다. 도메인 제약이 실질 리스크.
- **Caddy/Traefik 전환** — 갱신 취약성을 구조적으로 없애지만, nginx 대비 생태계가 작고 마이그레이션
  비용/위험이 있다. nginx는 실무 주류(점유율 수 배)라 유지 시 자료·인수인계 이점이 크다.
- **결론** — 갱신 버그는 이미 고쳤고(`443e9af`), 남은 위험은 "그게 또 조용히 깨지는 것"뿐이다. 그렇다면
  **스택을 갈아엎는 대신 감지·검증 계층을 얇게 얹는** 편이 규모에 맞는다.

## 무엇을 막는가

핵심 실패 모드는 **"앱은 정상인데 인증서만 조용히 갱신 실패"** 다(이번 장애가 그랬다 — 앱은 `UP`, 인증서만
만료). 이 경우 **앱이 떠 있으므로 앱 안에서 도는 감시가 정확히 잡는다.** "앱 자체가 다운"은 별개의 더 큰
문제이고, 그건 Step 3(외부 uptime)로 얇게 보완한다.

자동 갱신을 멈출 수 있는(그리고 전부 조용한) 원인: rate limit, 포트 80 차단·nginx 다운으로 ACME
challenge 실패, 디스크 풀, certbot 컨테이너 크래시/재부팅 후 미기동, 갱신은 됐지만 nginx 리로드 실패,
ACME 정책 변경.

## 견고화 단계

### Step 0 — 이미 고친 것을 prod에 반영하고 검증 (선행·필수)

`443e9af`(dev 커밋)은 아직 prod 미배포다. 이게 서버에 살기 전엔 나머지가 무의미하다.

- [ ] dev → main 배포(prod는 main push 시 compose를 서버로 scp).
- [ ] 서버에서 갱신 루프 생존 확인:
  ```bash
  docker compose -f docker-compose.prod.yml up -d nginx certbot
  docker logs --tail 20 certbot          # hook 에러가 더는 안 나오는지
  docker inspect nginx --format '{{.State.Status}}'   # running
  ```

### Step 1 — 앱 내 인증서 만료 감시 → Discord (핵심)

**감지 원리(업계 표준).** Prometheus `blackbox_exporter`의 `probe_ssl_earliest_cert_expiry`와 동일하게,
**TLS 핸드셰이크에서 받은 인증서의 `notAfter`** 로 남은 일수를 낸다. 자동 갱신이 정상이면 certbot이 만기
30일 전에 발급하므로 서빙 인증서는 **항상 30일 이상** 남는다 → **남은 일수 < 임계치(21~30일) = 갱신
실패 중** 이라는 직접 신호. 만료됐거나 핸드셰이크가 실패하면 지표가 조용해지므로, **연결/핸드셰이크 실패도
알림 신호로 삼는다.**

**어디서 검사하나.** 앱의 `@Scheduled` 작업이 compose 네트워크의 `nginx:443`에 SNI `hearu.p-e.kr`로
`SSLSocket` 연결해 **실제 서빙되는 인증서**의 `notAfter`를 읽는다(디스크 PEM이 아니라 서빙 인증서를 봐서
"갱신됐지만 리로드 안 됨"까지 잡는다).

**패키지 — `common.cert`(관심사 패키지).** 어느 도메인에도 속하지 않는 교차 관심사이고 구성요소가
판독기·스케줄러·프로퍼티로 여러 계층에 걸친다. 이 코드베이스는 그런 경우 관심사명으로 묶는다 —
`common.encrypt`(변환기+러너+예외+프로퍼티)·`common.logging`·`common.security`가 선례. 계층별로 쪼개
`common.scheduler`를 새로 만들지 않는다(존재하지 않고, 유일한 스케줄러는 자기 도메인 안의 `auth.scheduler`).

```
CertExpiryMonitor (@Scheduled, common.cert)
  └─ TlsCertificateInspector.readNotAfter("nginx", 443, sni="hearu.p-e.kr", timeout)
        → Duration.between(now, notAfter).toDays()
        → daysLeft < warnThresholdDays 이면 (알림 주기에 한해) DiscordNotifierClient.sendNotification(...)
        → 핸드셰이크/연결 자체 실패해도 알림
```

**알림 주기(피로 방지).** 임계치 미만인 동안 매일 핑을 보내면 실제 경고가 묻힌다. 로그(WARN)는 매일
남기되 Discord 알림은 **여유 구간 5일 간격 + 마지막 7일(및 만료 후) 매일**만 보낸다
(`CertExpiryJudge.isNotifyMilestone`). 스케줄러가 하루 1회 도는 전제의 **무상태 판정**이라 상태 저장이
없어 재시작에도 안전하다. 만료(음수 daysLeft)는 `toDays()`가 0으로 절삭돼 오해를 부르므로 문구를
"이미 만료됨"으로 구분한다.

설정(값은 base `application.yml`에 두어 프로필 미지정에도 로드된다 — `scheduler.*.cron` 선례.
`CertProperties`에 필드 레벨 기본값은 없어, `cert` 블록을 지우면 `timeout` NPE·`${cert.check.cron}`
SpEL 미해결로 **기동 실패**한다):

| 키 | 예시 | 비고 |
|---|---|---|
| `cert.check.cron` | `0 0 9 * * *` (09:00 KST) | 기존 `scheduler.*.cron`과 같은 방식 |
| `cert.sni-host` | `hearu.p-e.kr` | SNI/서버 블록 매칭 |
| `cert.target-host` | `nginx` | compose 네트워크 대상 |
| `cert.target-port` | `443` | |
| `cert.warn-threshold-days` | `21` | 정상 시 항상 30일↑ |
| `cert.timeout` | `3s` | connect/read (외부 호출 타임아웃 규칙) |

`CertExpiryMonitor`는 `@Profile("prod")`다 — nginx 종단이 prod 토폴로지에만 있어, dev(Railway)·local에선
빈이 안 떠 스케줄도 안 걸린다(공용 Discord 웹훅으로 오탐이 가던 것 차단). 설정값은 위처럼 전 프로필에
로드되지만 빈은 prod에서만 뜬다.

구현 태스크:
- [x] `common.cert` 패키지 생성.
- [x] 설정 바인딩 추가(위 표, 기본값 포함). 기존 config 바인딩 컨벤션에 통일 — `CertProperties`(`cert.*`),
      `cert.check.cron`만 `RefreshTokenCleanupScheduler` 선례대로 `@Scheduled` SpEL에서 직접 참조.
- [x] `TlsCertificateInspector` — `nginx:443` SNI 프로브, trust-all + 호스트네임검증 off로 핸드셰이크만
      성립시켜 `getPeerCertificates()[0].getNotAfter()` 반환. **connect/read 타임아웃 필수.**
- [x] 판정 로직을 순수 메서드로 분리(`CertExpiryJudge.daysLeft`/`shouldWarn`/`isNotifyMilestone`,
      given notAfter·now·threshold).
- [x] `CertExpiryMonitor` — `@Scheduled` + `RefreshTokenCleanupScheduler` 선례대로 소폭 재시도, 판독
      실패 시 알림. 알림 문구에 남은 일수(만료 시 "이미 만료됨")·notAfter·대상 호스트·시각(KST). 로그
      레벨은 정상 INFO(하트비트 — prod 앱 로그가 INFO라 성공도 남겨야 "안 돎"과 구분됨) / 임계치
      미만·실패 WARN·ERROR(logging.md 기준).
- [x] 테스트 — 판정 로직 경계값 단위 테스트(`CertExpiryJudgeTest`) + Mockito 단위 테스트
      (`CertExpiryMonitorTest`) + 빈/프로퍼티 와이어링 테스트(`CertExpiryMonitorWiringTest`,
      `RefreshTokenCleanupSchedulerWiringTest` 선례). TLS 소켓 부분(`TlsCertificateInspectorTest`)은
      실서버 의존이라 연결 실패 → `CertInspectionException` 래핑만 얇게 확인.

**미확인 — 실서버 검증 안 함:** 실제 `nginx:443` 컨테이너를 대상으로 한 동작 확인은 Step 0(prod 배포)
이후에나 가능하다. 지금은 유닛 테스트로만 검증했다.

### Step 2 — 배포 시점 인증서 검증 (회귀 방지)

`prod.yml` deploy 스크립트는 이미 (a) 실행 이미지 SHA 일치, (b) `/actuator/health` 헬스체크를 한다.
여기에 **서빙 인증서 남은 일수 체크**를 더해, 배포 시점에 인증서가 임박/만료 상태면 배포를 실패시킨다.

- [ ] deploy 검증에 `openssl s_client ... | openssl x509 -checkend $((21*86400))` 류 확인 추가
      (실패 시 배포 중단·로그).

### Step 3 — "앱 다운"은 WMS가 이미 담당 (알림만 Discord로 모으기)

Step 1은 "앱 업 + 인증서 문제"를 잡지만 "앱 자체 다운"은 못 잡는다. 그 역할은 **이미 쓰고 있는 NCP
WMS(Web Service Monitoring System)** 가 한다 — 가용성·응답속도·오류를 감시한다(단, NCP 공식 문서상
**SSL 인증서 만료는 감시 항목이 아니다** → 그래서 Step 1이 필요). 새 uptime 도구를 붙일 이유는 없다.

남은 일은 **WMS 알림을 Discord로 모으는 것**(아래 알림 아키텍처 원칙). WMS는 Webhook 채널을 지원하므로
가능하되, WMS 웹훅 페이로드가 Discord 형식(`{content}`)·Discord의 Slack 호환 엔드포인트(`/slack`) 중
어느 것과도 안 맞으면 **작은 어댑터/릴레이 한 단계**가 필요할 수 있다 — 설정 시 테스트로 확인한다.

> NCP Certificate Manager도 만료 알림(30일 전부터 5일 간격, 이메일·SMS)을 제공하지만, **CM에 등록된
> 인증서 사본**의 만료일을 보는 것이라 certbot이 서버에서 자동 갱신하는 우리 인증서엔 그대로 안 맞는다
> (갱신 때마다 재업로드 필요·오탐 위험). CM을 제대로 쓰려면 NCP LB로 TLS 종단을 옮기는 아키텍처 변경이
> 따르므로 이번엔 채택하지 않는다.

## 알림 아키텍처 원칙 — 감지기는 여럿, 종착지는 하나

알림을 "한 플랫폼이 전부 감지"하게 만들려 하지 않는다. 실무 정석은 **감지기(detector)와 알림
종착지(sink)를 분리**하는 것이다.

- **감지기는 여럿이 정상이다** — 인프라 도달성은 WMS가, 앱 내부 이벤트(스케줄러·인증서 만료·비즈니스
  실패)는 앱이 본다. 하나의 도구가 인프라·앱·인증서를 다 제대로 감시하는 경우는 드물다. 감지기가 둘
  이상인 것은 파편화가 아니다.
- **종착지는 하나로 모은다** — 사람이 결국 알림을 받는 곳은 **Discord 단일 채널**로 통일한다. 종착지가
  이메일·SMS·Discord로 흩어지는 것이 진짜 파편화이자 알림 피로의 원인이다.

우리 구성에 적용하면: WMS(도달성) + 앱 스케줄러(인증서·RefreshToken 등) 등 **감지기는 여럿 → 전부
Discord로 라우팅**. 새 감지 도구를 늘리기보다, 기존 감지기의 알림을 Discord로 모으는 데 집중한다.
규모가 커지면 종착지가 Discord에서 온콜/인시던트 플랫폼(PagerDuty·Opsgenie·Grafana OnCall)으로 바뀌지만
**"여러 감지기 → 하나의 종착지"라는 원리는 동일**하다.

## 이번 장애에서 나온 규칙 (재발 방지)

- **컨테이너에서 외부 도구(`docker`·`curl`·`openssl` 등)에 의존하기 전에, 그 이미지에 실제로 존재하는지
  확인한다.** 이번 만료의 직접 원인이 `certbot/certbot` 이미지에 없는 `docker`를 deploy-hook이 호출한
  것이었다. 알림·검증 스크립트를 어느 컨테이너에 넣든 이 확인을 먼저 한다.

## 비목표

- 리버스 프록시 교체(Caddy/Traefik)·앞단 CDN(Cloudflare) 도입 — 이번엔 안 한다(위 검토 기록 참조).
- 자동 갱신 로직 자체 수정 — 이미 `443e9af`에서 완료.

## 문서 갱신

- Step 1이 새 스케줄러를 추가하므로 적용 시 `CLAUDE.md` Key Infrastructure의 Scheduling 행에 한 줄 추가.
- 런타임 흐름 변화가 아니므로 `docs/architecture.md`는 건드리지 않는다.
- 각 Step 적용 시 이 문서 상단 상태와 해당 체크박스를 갱신한다.
