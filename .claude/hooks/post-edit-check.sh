#!/usr/bin/env bash
# PostToolUse(Edit|Write): apm-root 안에서 수정한 파일에 맞춰 컴파일/린트를 돌리고, 실패하면 exit 2로 오류를 Claude에게 돌려준다.
ROOT="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "$0")/../.." && pwd)}"
APM="$ROOT/apm-root"
f=$(jq -r '.tool_input.file_path // .tool_response.filePath // empty')
[ -z "$f" ] && exit 0
case "$f" in "$APM"/*) rel="${f#"$APM"/}" ;; *) exit 0 ;; esac

case "$rel" in
  apm-server/*.java|apm-sdk/*.java|simple-app/*.java)
    module="${rel%%/*}"
    out=$(cd "$APM" && ./gradlew ":$module:compileJava" -q --offline 2>&1) || {
      echo "[hook] $module compileJava 실패 (apm-root/$rel):" >&2
      echo "$out" | grep -E 'error:|\.java:[0-9]+' | head -30 >&2
      exit 2
    }
    ;;
  apm-dashboard/src/*.ts|apm-dashboard/src/*.tsx)
    out=$(cd "$APM/apm-dashboard" && npx --no-install oxlint "${rel#apm-dashboard/}" 2>&1) || {
      echo "[hook] oxlint 실패 (apm-root/$rel):" >&2
      echo "$out" | head -40 >&2
      exit 2
    }
    ;;
esac
exit 0
