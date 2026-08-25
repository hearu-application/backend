# Prod 배포 GHCR 인증 — PAT → GITHUB_TOKEN 전환

2026-08-13 ~ 08-22, `GHCR_PAT`(개인 액세스 토큰) 만료로 prod 배포 파이프라인이 옛 이미지를 그대로 띄운 채
9일간 초록불을 유지한 사고가 있었다. PAT 재발급만으로는 같은 유형의 사고(토큰 재만료)가 다시 날 수 있어
인증 방식 자체를 바꿨다.

## 적용 내용

| 위치 | 변경 |
|---|---|
| `.github/workflows/prod.yml`의 `deploy` job | `permissions: packages: read` 추가 |
| 같은 job의 `Deploy` 스텝(`appleboy/ssh-action`) | `env`로 `GHCR_TOKEN=${{ secrets.GITHUB_TOKEN }}`, `GHCR_ACTOR=${{ github.actor }}`를 선언하고 `envs:`로 SSH 세션에 전달. 원격 서버의 `docker login`이 `secrets.GHCR_PAT` 대신 이 값을 사용 |

`GITHUB_TOKEN`은 워크플로 실행마다 GitHub가 자동 발급하는 토큰이라 **사람이 갱신할 대상이 아니다** — PAT
만료라는 사고 카테고리 자체가 없어진다. GHCR 인증 서버는 토큰이 어디서(러너/외부 서버) 왔는지가 아니라
토큰 값과 그 안의 권한(`packages: read`)·만료 여부만 검사하므로, SSH로 넘겨 원격 서버에서 쓰는 것도
정상 동작한다.
