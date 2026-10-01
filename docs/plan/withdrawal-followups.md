# 회원 탈퇴 후속 과제

탈퇴 유예·하드 삭제([`../withdrawal-grace-period.md`](../withdrawal-grace-period.md)) 이후 남은 일.

## Apple revoke / Kakao unlink

- Apple App Store 가이드라인 5.1.1(v): 계정 삭제 시 Sign in with Apple 토큰을 REST API로 revoke해야 한다.
- Kakao: 서비스 탈퇴 시 연결 끊기(unlink) 호출 권장.
- 현재 서버는 `idToken`만 받아 provider의 access/refresh token이 없다. 클라이언트가 authorization code
  (Apple) 또는 access token(Kakao)을 보내도록 계약을 바꿔야 한다.
- 호출 시점(탈퇴 즉시 vs 하드 삭제 시)도 함께 정한다. 유예 중 복구를 허용하므로 하드 삭제 시점이 후보다.

## 배포 체크리스트

- [ ] 배포 전 prod 기존 탈퇴자 수 확인: `SELECT COUNT(*) FROM user WHERE deleted_at IS NOT NULL`
      (실행당 처리 인원 상한은 `architecture.md` §4.7)
- [ ] 첫 정각 실행 후 `[Scheduler][WithdrawalPurge] 완료` 로그의 purged/failed 확인
- [ ] 앱 팀에 계약 공유(서버 먼저 배포)
- [ ] 롤백하면 그동안 하드 삭제가 멈춘다. 재배포 시 밀린 대상이 처리되는지 확인

## 코드 밖 확인 필요

- 개인정보처리방침·탈퇴 화면에 "탈퇴 후 24시간 보관 뒤 파기" 고지가 필요한지(법적 확인)
- DB 백업이 있다면 백업 내 탈퇴자 데이터의 보존 기간 정책
