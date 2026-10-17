# `.claude/agents/` — 두 계층의 서브에이전트

이 폴더에는 **목적이 다른 두 계층**의 에이전트 정의가 공존한다. 등록·수정 시 계층을 반드시 구분할 것.

## 1. 오케스트레이션 Worker Pool

파일 기반 멀티에이전트 시스템의 정식 워커. **능력 슬롯에 배정**되며 `workers_approved` 승인 게이트 대상이다.
배정 정본은 `_shared/capability-profile.md`, 슬롯 정의는 `_shared/routing.md`, 호출 스펙은 `_shared/backends.json`.

| 파일 | name | 슬롯 |
|------|------|------|
| `claude-main.md` | `claude-main` | strategist — 기획·설계·원인 분석 (파일 쓰기 없음) |
| `claude-reviewer.md` | `claude-reviewer` | reviewer — 산출물 리뷰 (읽기 전용) |
| `review-expert.md` | `review-expert` | reviewer — apm-root 코드 리뷰 (Google 리뷰 가이드 + APM·OTel 체크, 읽기 전용) |

engineer 슬롯(구현)은 전담 워커 없이 Orchestrator가 직접 하거나 아래 도메인 서브에이전트에 위임한다.
도메인 전문성은 슬롯이 아니므로 이 계층에 넣지 않는다.

## 2. 도메인 서브에이전트 (apm-root 모듈 전담)

apm-root를 **실제 개발할 때** 쓰는 모듈 전담 에이전트. 일반 Claude Code 서브에이전트로 동작한다 —
직접 파일을 쓰고 컴파일로 검증한다. 승인 게이트 대상이 아니며 `backends.json`에도 등록하지 않는다.
각 에이전트는 시작 시 짝 스킬을 먼저 읽는다.

| 파일 | name | 모듈 | 짝 스킬 |
|------|------|------|--------|
| `server-expert.md` | `server-expert` | `apm-server` — 수집·검색·통계 API | `apm-server-core` |
| `sdk-expert.md` | `sdk-expert` | `apm-sdk` · `simple-app` — 수집 라이브러리·데모 | `apm-sdk-core` |
| `dashboard-expert.md` | `dashboard-expert` | `apm-dashboard` — React 대시보드 | `apm-dashboard-web` |

모듈 경계를 넘는 변경(이벤트 필드 추가 등)은 세 에이전트 모두 `apm-event-contract` 스킬을 따른다.

**이 계층에 에이전트를 추가하려면** 이 표에 한 줄 추가하고 `.claude/agents/<name>.md` 를 둔다.
