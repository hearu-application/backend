# 운영 알림 Slack → Discord 전환 (적용 완료)

운영 장애 알림 채널을 Slack Webhook에서 Discord Webhook으로 옮겼다.

앞으로 추가되는 알림(예: [`ai-response-stuck-sweep.md`](ai-response-stuck-sweep.md)의 스윕 결과 통보)은
Discord를 쓴다.

---

## 배포 순서 (주의)

`application.yml`이 `${DISCORD_WEBHOOK_URL}`을 **기본값 없이** 요구하므로, 값이 없으면 기동에 실패한다.

1. GitHub Actions 시크릿 `PROD_DISCORD_WEBHOOK_URL` 등록
2. 로컬 `.env`의 `SLACK_WEBHOOK_URL` → `DISCORD_WEBHOOK_URL` 교체 (`.env`는 gitignore 대상)
3. 코드 배포
4. 기동 후 `PROD_SLACK_WEBHOOK_URL` 시크릿 제거

## 적용 내용

| 위치 | 변경 |
|---|---|
| `DiscordNotifierClient` | `SlackNotifierClient` 대체. 페이로드 `{"text":…}` → `{"content":…}` |
| `RestClientConfig` | 빈 `slackRestClient` → `discordRestClient` (connect 3s / read 5s 유지) |
| `RefreshTokenCleanupScheduler` | `@Recover`의 알림 호출부 교체 (유일한 호출부) |
| `application.yml` | `slack.webhook.url` → `discord.webhook.url: ${DISCORD_WEBHOOK_URL}` |
| `.github/workflows/prod.yml` | `DISCORD_WEBHOOK_URL=${{ secrets.PROD_DISCORD_WEBHOOK_URL }}` |
| 테스트 | `DiscordNotifierClientWiringTest`, `RefreshTokenCleanupScheduler{,Wiring}Test` |
| 문서 | `docs/architecture.md`, `CLAUDE.md`(환경변수·Key Infrastructure·scope `discord`), `.claude/rules/coding-style.md` |

## 전환하며 내린 결정

1. **`Notifier` 인터페이스를 만들지 않았다** — 호출부가 `RefreshTokenCleanupScheduler` 하나뿐이고
   두 채널을 병행할 계획이 없다. 추상화는 두 번째 채널이 실제로 생길 때 넣는다.
2. **2000자 절단은 클라이언트가 한다** — Discord Webhook의 `content` 상한이 2000자이고 넘기면
   400으로 거부된다. 알림 메시지에는 외부 예외 메시지(`e.getMessage()`)가 그대로 실려 길이를
   호출부가 보장할 수 없으므로, `DiscordNotifierClient`가 보내기 전에 잘라낸다.
3. **로그 태그 `[NOTIFIER][*]`와 오류 삼킴은 그대로 뒀다** — 알림 실패가 본 로직(스케줄러 복구
   경로)을 깨뜨려서는 안 된다는 전제가 채널과 무관하게 유지된다.
