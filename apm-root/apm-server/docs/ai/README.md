# apm-server — AI 하네스 문서

이벤트 수집·검색·통계 API 서버. SDK가 보낸 이벤트를 저장하고 대시보드에 조회 API를 제공한다.

- 스택: Java 21 · Spring Boot 3.5 · Spring Data JPA · QueryDSL 5(jakarta) · PostgreSQL
- 포트 8080 · DB `jdbc:postgresql://localhost:5432/apm_db` · 설정 정본 `src/main/resources/application.yml`
- 빌드: 저장소 루트에서 `./gradlew :apm-server:compileJava` (모듈 안의 별도 `gradlew`는 쓰지 않음)

## 구조

```
com.leekyungmi.apm.apm_server
├── domain/<도메인>/{controller,dto,entity,repository,service} + 도메인 enum
└── global/config/  QueryDslConfig(JPAQueryFactory) · WebConfig(CORS /api/** ← localhost:*)
```

## API (domain/event)

| 메서드 · 경로 | 설명 |
|--------------|------|
| `POST /api/events` | 이벤트 1건 저장 → id |
| `GET /api/events` | `appId,type,uri,from,to,page,size` 검색 → `Page<EventResponse>` |
| `GET /api/events/stats` | `appId,from,to` → 총 건수·평균 응답시간·에러율·타입별 건수 |
| `GET /api/events/apps` | 앱 ID 목록 |

## 규약

- Entity: `@Builder` + 생성자, setter 없음. Request DTO는 `toEntity()`, Response DTO는 `static from()`.
- 동적 검색은 `<X>CustomRepositoryImpl`에서 `BooleanExpression` 헬퍼(null 반환 시 조건 무시).
- Service는 클래스에 `@Transactional(readOnly = true)`, 쓰기 메서드만 `@Transactional`.

## 주의

- `ddl-auto: create` — 재시작하면 테이블을 다시 만들어 데이터가 사라진다.
- 검색 정렬은 `occurredAt desc` 고정(`sort` 파라미터 무시).
- 에러율은 `statusCode >= 400` 기준이라 `EventType.ERROR` 건수와 다를 수 있다.

상세 규약: 오케스트레이터의 `apm-server-core` 스킬 · 모듈 간 계약: `apm-event-contract` 스킬
