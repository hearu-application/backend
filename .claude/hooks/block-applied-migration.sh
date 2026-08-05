#!/usr/bin/env bash
# PreToolUse(Edit|Write) — 이미 존재하는 Flyway 마이그레이션 파일의 수정을 차단한다.
#
# 왜: Flyway는 적용된 마이그레이션의 체크섬을 검증한다. 기존 파일을 고치면 체크섬이 어긋나
#     dev/prod가 기동 단계에서 실패한다. 컴파일·테스트로는 걸리지 않는다.
# 무엇을 허용하나: 새 V{n}__*.sql 파일 생성은 그대로 허용한다(아직 존재하지 않으므로).
#
# 훅 입력(stdin)은 JSON이다. jq가 없는 환경이라 grep/sed로 file_path만 뽑는다.

payload=$(cat)

path=$(printf '%s' "$payload" | grep -o '"file_path"[[:space:]]*:[[:space:]]*"[^"]*"' | head -1 | sed 's/.*"file_path"[[:space:]]*:[[:space:]]*"//; s/"$//')
[ -n "$path" ] || exit 0

# JSON 안의 경로는 Windows에서 "D:\\project\\..." 형태로 온다. 백슬래시를 슬래시로 정규화한다.
path=$(printf '%s' "$path" | tr '\\' '/' | sed 's|//*|/|g')

case "$path" in
  */db/migration/V*.sql) ;;
  *) exit 0 ;;
esac

# 존재하지 않으면 신규 생성이므로 통과시킨다.
[ -f "$path" ] || exit 0

cat <<'JSON'
{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"deny","permissionDecisionReason":"이미 존재하는 Flyway 마이그레이션 파일은 수정할 수 없습니다. Flyway가 체크섬을 검증하므로 적용된 파일을 고치면 dev/prod가 기동 단계에서 실패합니다. 파일을 고치지 말고 되돌리는 새 버전(V{다음번호}__revert_....sql)을 추가하세요. 자세한 내용은 .claude/rules/db-migration.md 참고."}}
JSON
