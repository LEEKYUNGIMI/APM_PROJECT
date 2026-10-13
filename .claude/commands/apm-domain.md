---
description: apm-server에 새 도메인 한 벌(Entity·Request/Response/Search DTO·Repository·CustomRepository(+Impl)·Service·Controller)을 event 도메인 규약대로 생성한다
argument-hint: "<EntityName> [--fields \"name:String, durationMs:int, occurredAt:LocalDateTime\"] [--search \"appId:eq, uri:contains, occurredAt:range\"] [--path /api/<복수형>] [--dry-run]"
allowed-tools: Read, Write, Glob, Grep, Bash(ls:*), Bash(find:*), Bash(apm-root/gradlew -p apm-root :apm-server:compileJava*), AskUserQuestion
---

# /apm-domain — apm-server 도메인 생성

입력: `$ARGUMENTS`

**생성 전에 `apm-server-core` 스킬을 읽는다.** 이 커맨드는 그 스킬 §1~§3 규약을 그대로 적용한다.
기준 구현은 `apm-root/apm-server/src/main/java/com/leekyungmi/apm/apm_server/domain/event/` 다 — **그 코드를 베끼고 이름·필드만 바꾼다.** 새 구조를 발명하지 않는다.

## 1. 파라미터

| 파라미터 | 기본값 | 설명 |
|---------|--------|------|
| `EntityName` (필수) | — | PascalCase. 없으면 사용법만 출력하고 종료 |
| `--fields` | 없음 | `이름:Java타입` 을 `,`로 구분. 허용 타입: `String` `int` `long` `Integer` `Long` `double` `boolean` `LocalDateTime` 또는 이미 존재하는 enum 이름 |
| `--search` | 없음 (검색 API 생략) | `필드:eq` · `필드:contains`(String만) · `필드:range`(LocalDateTime만 → `from`/`to`) |
| `--path` | `/api/` + 소문자 복수형 | `@RequestMapping`. 반드시 `/api/`로 시작(CORS가 `/api/**`만 허용) |
| `--dry-run` | off | 파일을 쓰지 않고 생성 계획만 출력 |

- 도메인 패키지 = `lowercase(EntityName)`, 테이블명 = 소문자 snake 복수형(`Event` → `events`, `AlertRule` → `alert_rules`).
- `--fields`가 없으면 `AskUserQuestion`으로 한 번만 묻는다: "필드 없이 id만 있는 뼈대로 생성" vs "필드를 입력하겠다".
- 나머지는 묻지 않고 기본값을 쓴 뒤 §5 보고에 명시한다.

## 2. 착수 전 게이트 (하나라도 걸리면 생성하지 않고 보고)

1. `apm-server-core` 스킬을 읽었는가
2. `domain/<도메인>/`이 이미 있는가 → 있으면 중단 (덮어쓰기는 사용자가 명시할 때만)
3. 같은 클래스명(`<Name>`, `<Name>Controller` 등)이 저장소에 있는가 → Glob으로 확인, 있으면 중단
4. `--path`가 기존 `@RequestMapping`과 겹치는가 → 겹치면 중단
5. `--fields`에 `id`가 있는가 → 제거하고 보고 (id는 자동 생성)

## 3. 생성 파일 (루트: `apm-root/apm-server/src/main/java/com/leekyungmi/apm/apm_server/domain/<도메인>/`)

| 파일 | 규약 |
|------|------|
| `entity/<Name>.java` | `@Entity @Getter @NoArgsConstructor @Builder @AllArgsConstructor @Table(name="<table>")`, `@Id @GeneratedValue(strategy = GenerationType.IDENTITY) Long id`, enum 필드는 `@Enumerated(EnumType.STRING)`. setter 금지 |
| `dto/<Name>Request.java` | `@Getter @Builder @AllArgsConstructor @NoArgsConstructor` + `toEntity()` |
| `dto/<Name>Response.java` | `@Getter @Builder` + `static from(<Name>)` (id 포함) |
| `dto/<Name>Search.java` *(--search 시)* | 검색 필드만. range는 `from`/`to` |
| `repository/<Name>Repository.java` | `extends JpaRepository<<Name>, Long>` (+ `<Name>CustomRepository` — search 시) |
| `repository/<Name>CustomRepository.java` *(--search 시)* | `Page<<Name>> search(<Name>Search search, Pageable pageable);` |
| `repository/<Name>CustomRepositoryImpl.java` *(--search 시)* | `@Repository @RequiredArgsConstructor`, `JPAQueryFactory`, `BooleanExpression` 헬퍼(null이면 조건 무시), content+count 두 쿼리 → `PageImpl`, 정렬은 **`pageable.getSort()`를 반영**하거나 반영하지 않으면 보고에 명시 |
| `service/<Name>Service.java` | 클래스 `@Transactional(readOnly = true)`, `save`만 `@Transactional`, 반환은 Response DTO |
| `controller/<Name>Controller.java` | `@RestController @RequiredArgsConstructor @RequestMapping("<path>")`. `POST`(생성 → id), `GET /{id}`(단건), `GET`(search 시 `Page`, 아니면 목록). 반환은 `ResponseEntity.ok(...)` |

하지 않을 것: 기존 파일 수정, `application.yml` 변경, 요청에 없는 수정/삭제 API 발명, SDK·대시보드 변경, commit·push.

## 4. 생성 후 검증

1. `apm-root/gradlew -p apm-root :apm-server:compileJava -q` — PostToolUse hook이 파일마다 돌지만, 마지막에 한 번 더 직접 실행해 결과를 확인한다. 실패하면 고치고 다시 돌린다. Q클래스(`Q<Name>`)는 이 단계에서 생성된다.
2. 생성 파일이 실제로 있는지 확인한다.
3. 대조: Entity 필드 == Request 필드 == Response 필드(+id), `toEntity()`/`from()`에 빠진 필드가 없는지.

## 5. 보고 (한국어)

```
## 생성 결과
- 도메인: <도메인>  테이블: <table>  경로: <path>
- 생성 파일 N개 (경로 목록)
- API: POST <path> / GET <path>/{id} / GET <path>

## 적용한 기본값
(사용자가 주지 않아 추론한 값과 근거)

## 검증
- compileJava: 성공 / 실패(사유)
- 필드 대조 결과

## 남은 일
- 대시보드 연동이 필요하면 dashboard-expert (apm-dashboard-web 스킬)
- ddl-auto: create 라 서버 재시작 시 테이블이 생성된다(기존 데이터는 삭제됨)
```

## 6. 사용 예

```
/apm-domain AlertRule --fields "appId:String, metric:String, threshold:double, enabled:boolean" --search "appId:eq"
/apm-domain Deployment --fields "appId:String, version:String, deployedAt:LocalDateTime" --search "appId:eq, deployedAt:range" --dry-run
```
