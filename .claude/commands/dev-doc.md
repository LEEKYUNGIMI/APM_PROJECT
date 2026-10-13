---
description: 개발산출물 생성 (API명세서, 테이블정의서, 프로그램목록, 단위테스트케이스, 화면정의서, 전체)
argument-hint: <산출물종류> [대상모듈/도메인] [출력경로]
allowed-tools: Read, Glob, Grep, Write, Bash(ls:*), Bash(find:*), Bash(mkdir:*)
---

# 개발산출물 생성

## 입력 파라미터

- 전체 입력: `$ARGUMENTS`
- `$1` 산출물종류 (필수): `api` | `table` | `program` | `test` | `screen` | `all`
  - 한글 별칭 허용: `API명세서`, `테이블정의서`, `프로그램목록`, `단위테스트케이스`, `화면정의서`, `전체`
- `$2` 대상 (선택, 기본값 `all`): 모듈명(`apm-server`, `apm-sdk`, `apm-dashboard`, `simple-app`) 또는 도메인명(예: `event`)
- `$3` 출력경로 (선택, 기본값 `docs/deliverables`)

`$1`이 비어 있거나 위 값에 해당하지 않으면 산출물을 만들지 말고 사용법만 출력하고 종료한다:

```
사용법: /dev-doc <산출물종류> [대상] [출력경로]
  예) /dev-doc api apm-server
      /dev-doc table event
      /dev-doc all all docs/deliverables
```

## 공통 규칙

1. 반드시 실제 소스코드를 읽고 작성한다. 코드에 없는 내용은 추측하지 말고 `-` 또는 `(확인필요)`로 표기한다.
2. `build/`, `node_modules/`, `dist/`, `.gradle/` 등 생성물 디렉토리는 분석에서 제외한다.
3. 결과는 Markdown 표 형식으로 `$3/<파일명>` 에 저장한다 (디렉토리가 없으면 생성). 기존 파일이 있으면 덮어쓴다.
4. 각 문서 상단에 다음 헤더를 넣는다:

   | 항목 | 내용 |
   |---|---|
   | 프로젝트 | APM (apm-root) |
   | 산출물명 | (산출물명) |
   | 대상 | `$2` |
   | 작성일 | (오늘 날짜, YYYY-MM-DD) |
   | 버전 | v1.0 |

5. 작성 후 생성한 파일 경로와 항목 수 요약만 짧게 보고한다.

## 산출물별 작성 기준

### `api` → `API명세서.md`
- 대상: `@RestController` / `@Controller` 클래스
- 목록표: | No | API ID | API명 | Method | URI | 설명 | Controller.메서드 |
  - API ID 규칙: `API-<도메인 대문자>-<3자리 순번>` (예: `API-EVENT-001`)
- API별 상세:
  - 요청 파라미터 표: | 구분(Path/Query/Body) | 필드명 | 타입 | 필수 | 설명 |
    - `@RequestParam(required=…)`, `@PathVariable`, `@RequestBody` DTO 필드, `Pageable`(page/size/sort)까지 반영
  - 응답 표: | 필드명 | 타입 | 설명 | (DTO/record 필드를 펼쳐서 작성, `Page<T>`는 content/totalElements 등 포함)
  - 요청/응답 JSON 예시

### `table` → `테이블정의서.md`
- 대상: `@Entity` 클래스 (테이블명은 `@Table(name)` 우선, 없으면 클래스명 snake_case)
- 테이블 목록표: | No | 테이블명 | 엔티티 | 설명 |
- 테이블별 컬럼표: | No | 컬럼명 | 데이터타입 | 길이 | PK | NULL | 기본값 | 설명 |
  - 컬럼명은 필드명의 snake_case (Spring 기본 네이밍 전략)
  - Java 타입 → DB 타입 매핑 (Long→BIGINT, int→INT, String→VARCHAR(255), LocalDateTime→DATETIME(6), Enum(STRING)→VARCHAR, 확인 불가 시 `(확인필요)`)
  - `@Id`, `@GeneratedValue`, `@Column(nullable/length)`, primitive 타입 NOT NULL 여부 반영
  - Enum 컬럼은 허용값 나열

### `program` → `프로그램목록.md`
- 대상: 모든 소스 파일 (Java, TS/TSX)
- 표: | No | 모듈 | 프로그램ID | 프로그램명(클래스/컴포넌트) | 유형 | 패키지/경로 | 설명 |
  - 유형: Controller / Service / Repository / Entity / DTO / Config / Filter / Interceptor / Component / API Client / Util 등
  - 프로그램ID 규칙: `PGM-<모듈약어>-<3자리 순번>` (SERVER, SDK, DASH, SAMPLE)

### `test` → `단위테스트케이스.md`
- 대상: Service, Controller, Repository의 public 메서드
- 표: | No | 테스트ID | 대상(클래스.메서드) | 테스트 시나리오 | 사전조건 | 입력값 | 기대결과 | 결과 |
  - 메서드별 정상 케이스 1건 이상 + 경계/예외 케이스 1건 이상
  - 테스트ID 규칙: `UT-<도메인 대문자>-<3자리 순번>`
  - `결과` 컬럼은 비워둔다 (수행 시 기입)

### `screen` → `화면정의서.md`
- 대상: `apm-dashboard/src` (React 컴포넌트)
- 화면 목록표: | No | 화면ID | 화면명 | 컴포넌트 | 설명 |
- 화면별 상세:
  - 구성요소 표: | No | 항목명 | 유형(입력/버튼/표/차트 등) | 설명 | 연계 API |
  - 사용 API 목록 (`api/` 호출 함수 → 서버 URI 매핑)

### `all`
- 위 5종을 모두 순서대로 생성한다 (`$2`가 프론트엔드를 포함하지 않으면 `screen`은 생략).
