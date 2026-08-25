# Prod 배포 파이프라인 신뢰성 개선

2026-08-13 ~ 08-22, `GHCR_PAT`(개인 액세스 토큰) 만료로 prod 배포 파이프라인이 옛 이미지를 그대로 띄운 채
9일간 초록불을 유지한 사고가 있었다. PAT 재발급만으로는 같은 유형의 사고가 다시 날 수 있어, 인증 방식과
스크립트의 실패 전파 방식을 함께 고쳤다.

## 적용 내용

| 위치 | 변경 |
|---|---|
| `.github/workflows/prod.yml`의 `deploy` job | `permissions: packages: read` 추가 |
| 같은 job의 `Deploy` 스텝(`appleboy/ssh-action`) | `env`로 `GHCR_TOKEN=${{ secrets.GITHUB_TOKEN }}`, `GHCR_ACTOR=${{ github.actor }}`를 선언하고 `envs:`로 SSH 세션에 전달. 원격 서버의 `docker login`이 `secrets.GHCR_PAT` 대신 이 값을 사용 |
| 같은 스텝 | `script_stop: true` 추가, 스크립트 첫 줄에 `set -euo pipefail` 추가 — `docker login`·`pull` 등 어느 명령이 실패해도 그 자리에서 job이 실패로 끝난다 |
| 헬스체크 루프 | `curl ... && break` / `[ $i -eq 12 ] && exit 1` 형태는 `set -e` 하에서 실패한 curl이 곧바로 스크립트를 종료시켜 재시도 루프 자체가 안 돈다. `if curl ...; then break; fi` / `if [ "$i" -eq 12 ]; then exit 1; fi`로 바꿔 `set -e`의 예외 대상(if 조건)에 넣음 |
| `.env`의 `DOCKER_TAG` | `prod`(mutable) → `prod-${{ github.sha }}`(불변). `docker compose pull app`이 이번 커밋 전용 태그를 명시적으로 요구하게 되어, pull이 실패해도 옛 이미지로 조용히 넘어갈 방법이 없다 |
| `up -d` 직후 | `docker inspect hearu-prod`로 실행 중인 이미지의 `org.opencontainers.image.revision` 라벨(메타데이터 액션이 빌드 시 자동으로 넣음)을 읽어 `${{ github.sha }}`와 비교, 다르면 `exit 1` |

`GITHUB_TOKEN`은 워크플로 실행마다 GitHub가 자동 발급하는 토큰이라 **사람이 갱신할 대상이 아니다** — PAT
만료라는 사고 카테고리 자체가 없어진다. GHCR 인증 서버는 토큰이 어디서(러너/외부 서버) 왔는지가 아니라
토큰 값과 그 안의 권한(`packages: read`)·만료 여부만 검사하므로, SSH로 넘겨 원격 서버에서 쓰는 것도
정상 동작한다.

`script_stop`/`set -e`는 이번 사고의 3번째 사슬(로그인·pull 실패가 스크립트를 안 멈춤)을 없앤다. 다만
`set -e`를 스크립트에 넣을 때 기존 헬스체크 루프가 `&&` 체인으로 짜여 있어 그대로 두면 **정상 상황에서도
첫 재시도 실패에 스크립트가 죽는** 부작용이 있었다 — 그래서 루프를 `if`문으로 다시 썼다.
