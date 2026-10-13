#!/usr/bin/env bash
# SessionStart: knot-vault 색인과 inbox 대기 건수를 세션 컨텍스트에 넣는다.
ROOT="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "$0")/../.." && pwd)}"
V="$ROOT/knot-vault"
[ -f "$V/index.md" ] || exit 0
pending=$(find "$V/inbox" -type f ! -name '.gitkeep' 2>/dev/null | wc -l | tr -d ' ')
echo "## 프로젝트 지식 (knot-vault) — 설계 배경·결정 이유가 필요하면 knot-vault/wiki/<슬러그>.md 를 읽는다"
sed -n '/^## /,$p' "$V/index.md"
if [ "$pending" -gt 0 ]; then
  echo
  echo "knot-vault/inbox 에 미처리 소스 ${pending}건 — 사용자에게 '/knot ingest' 실행을 제안할 것."
fi
