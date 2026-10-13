---
description: 프로젝트 지식 위키(knot-vault)를 운용한다 — save(자료 넣기) · ingest(위키화) · query(질문) · lint(건강검진)
argument-hint: "<save|ingest|query|lint> [파일경로 | 질문]"
allowed-tools: Read, Write, Edit, Glob, Grep, Bash(git -C knot-vault:*), Bash(cd knot-vault*), Bash(cp:*), Bash(mv:*), Bash(ls:*), Bash(find:*), Bash(python3 knot-vault/scripts/lint.py), Bash(python3 scripts/lint.py)
---

# /knot — 프로젝트 지식 공유 (LLM Wiki)

입력: `$ARGUMENTS` → 첫 단어가 작업(`$1`), 나머지가 인자.

vault 위치: `knot-vault/` — apm-orchestrator 안에 있지만 **별도 로컬 git 저장소**다.
git 명령은 반드시 `git -C knot-vault ...` 로 vault 저장소에 대해서만 실행한다. apm-root 코드 저장소에 커밋하지 않는다.

**어떤 작업이든 먼저 `knot-vault/schema.md`를 정독한다.** 아래는 진입점일 뿐 규약 정본은 schema다.

| `$1` | 하는 일 |
|------|--------|
| `save <경로>` | 지정한 md/txt 파일(예: `apm-root/docs/xxx.md`)을 `knot-vault/inbox/`에 **복사**한다(원본은 그대로). 그 다음 바로 `ingest`를 이어서 할지 사용자에게 묻는다 |
| `ingest` | `knot-vault/prompts/ingest.md`를 그대로 실행한다 (가드 → inbox 가장 오래된 1건 → wiki 페이지 생성·갱신 → index/log → raw로 이동 → lint → vault 커밋) |
| `query <질문>` | `knot-vault/prompts/query.md`를 실행한다. 근거가 vault에 없으면 없다고 답한다. 재사용 가치가 있으면 note로 저장할지 묻는다 |
| `lint` | `knot-vault/prompts/lint.md`를 실행한다. 판단이 필요한 항목은 고치지 않고 목록으로 보고한다 |
| (없음/기타) | 사용법과 현재 상태(`index.md` 페이지 수, `inbox/` 대기 건수)만 출력하고 종료 |

## apm-root 운용 메모

- 무엇을 넣나: `docs/`의 설계 문서, 장애·디버깅 회고, 성능 실험 결과, 면접 대비 정리처럼 **코드만 봐서는 알 수 없는 "왜"**.
- 코드 구조·규약 자체는 `.claude/skills/`가 정본이다. 위키에 같은 내용을 복제하지 않는다.
- 세션 시작 시 SessionStart hook이 `index.md`를 컨텍스트에 넣고 inbox 대기 건수를 알려 준다.
- 커밋 메시지 트레일러는 실제 실행 모델명으로 남긴다(schema §git 규약).
