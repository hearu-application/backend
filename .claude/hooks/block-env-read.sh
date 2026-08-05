#!/usr/bin/env bash
# PreToolUse(Read|Grep|Bash) — .env 파일 내용이 대화 컨텍스트로 들어오는 것을 차단한다.
#
# 왜: .env에는 DB_PASSWORD · JWT_SECRET_KEY · LLM_API_KEY · OAuth 키가 들어 있다.
#     .gitignore는 커밋만 막을 뿐, 읽어서 컨텍스트에 올리는 경로는 막지 못한다.
#     컨텍스트에 올라간 값은 전송·저장되므로 유출로 취급한다.
# 값이 필요하면: application.yml의 참조 이름(${DB_URL} 등)만 보고 판단하고,
#     실제 값은 사용자에게 물어본다.
#
# jq가 없는 환경이라 grep/sed로 필요한 필드만 뽑는다.

payload=$(cat)

field() {
  printf '%s' "$payload" \
    | grep -o "\"$1\"[[:space:]]*:[[:space:]]*\"[^\"]*\"" \
    | head -1 \
    | sed "s/.*\"$1\"[[:space:]]*:[[:space:]]*\"//; s/\"$//"
}

deny() {
  cat <<JSON
{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"deny","permissionDecisionReason":"$1"}}
JSON
  exit 0
}

REASON=".env에는 DB 비밀번호·JWT 서명 키·LLM API 키·OAuth 키가 들어 있어 읽을 수 없습니다. 파일을 읽으면 값이 대화 컨텍스트에 남습니다. 어떤 변수가 필요한지는 application.yml의 참조 이름으로 확인하고, 실제 값이 필요하면 사용자에게 물어보세요."

# --- Read / Grep : 경로 기반 ---
for f in file_path path notebook_path; do
  p=$(field "$f")
  [ -n "$p" ] || continue
  p=$(printf '%s' "$p" | tr '\\' '/')
  base=${p##*/}
  case "$base" in
    .env|.env.*) deny "$REASON" ;;
  esac
done

# --- Bash : 내용을 출력하는 명령 + .env 조합만 막는다 ---
# `ls -la`나 .gitignore 편집처럼 .env를 언급만 하는 명령은 통과시킨다.
cmd=$(field "command")
if [ -n "$cmd" ]; then
  case "$cmd" in
    *.env*)
      if printf '%s' "$cmd" | grep -qE '(^|[|;&[:space:]])(cat|type|less|more|head|tail|strings|xxd|od|nl|source|\.)([[:space:]]|$)|grep[^|]*\.env|awk[^|]*\.env|sed[^|]*\.env'; then
        deny "$REASON"
      fi
      ;;
  esac
fi

exit 0
