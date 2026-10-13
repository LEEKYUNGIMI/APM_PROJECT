---
name: apm-server-core
description: >-
  apm-root의 apm-server 모듈(Java 21 · Spring Boot 3.5 · Spring Data JPA · QueryDSL 5 jakarta · PostgreSQL) 작업 규약.
  domain/<도메인>/{controller,dto,entity,repository,service} 패키지 구조, JpaRepository + CustomRepository + Impl(JPAQueryFactory) 패턴,
  BooleanExpression 동적 조건, Page/Pageable 검색, 통계 집계(EventStatsResponse), DTO toEntity()/from() 변환,
  QEvent 같은 Q클래스 생성, CORS(WebConfig), application.yml(ddl-auto) 을 건드릴 때 반드시 먼저 읽을 것.
  EventController · EventService · EventRepository · EventCustomRepositoryImpl · EventSearch · /api/events · /api/events/stats ·
  /api/events/apps · "통계가 이상해" · "정렬이 안 먹어" · "QEvent를 못 찾음" · "데이터가 재시작하면 사라져" 같은 주제도 대상이다.
---

# apm-server 작업 규약

대상: `apm-root/apm-server/` (루트 패키지 `com.leekyungmi.apm.apm_server`). 포트 8080, DB `jdbc:postgresql://localhost:5432/apm_db`.

## 1. 패키지 구조 (새 도메인도 이대로)

```
apm_server/
├── ApmServerApplication.java
├── domain/<도메인>/
│   ├── <Enum>.java                 # 도메인 enum은 도메인 루트 (예: event/EventType.java)
│   ├── controller/<X>Controller.java
│   ├── dto/<X>Request · <X>Response · <X>Search · <X>StatsResponse
│   ├── entity/<X>.java
│   ├── repository/<X>Repository · <X>CustomRepository · <X>CustomRepositoryImpl
│   └── service/<X>Service.java
└── global/config/                  # QueryDslConfig(JPAQueryFactory 빈), WebConfig(CORS)
```

## 2. 계층별 규약 (기존 event 코드 기준)

| 계층 | 규약 |
|------|------|
| Entity | `@Entity @Getter @NoArgsConstructor @Builder @AllArgsConstructor @Table(name="<복수형>")`. setter 없음. `@Id @GeneratedValue(strategy = IDENTITY)`. enum은 `@Enumerated(EnumType.STRING)` |
| Request DTO | `@Getter @Builder @AllArgsConstructor @NoArgsConstructor` + `toEntity()` |
| Response DTO | `@Getter @Builder` + `static from(Entity)` |
| Search DTO | 검색 조건 필드만. 컨트롤러에서 `@ModelAttribute` 생략형으로 바인딩(`search(EventSearch search, Pageable pageable)`) |
| Repository | `interface XRepository extends JpaRepository<X, Long>, XCustomRepository` |
| CustomRepositoryImpl | `@Repository @RequiredArgsConstructor`, `private final JPAQueryFactory jpaQueryFactory`. 이름은 반드시 `<Custom인터페이스>Impl` |
| Service | 클래스에 `@Transactional(readOnly = true)`, 쓰기 메서드에만 `@Transactional`. 엔티티는 밖으로 내보내지 않고 Response DTO로 변환 |
| Controller | `@RestController @RequiredArgsConstructor @RequestMapping("/api/<복수형>")`, 반환은 `ResponseEntity.ok(...)`. 날짜 파라미터는 `@DateTimeFormat(iso = DATE_TIME) LocalDateTime` |

## 3. QueryDSL 패턴

```java
QEvent e = QEvent.event;
jpaQueryFactory.selectFrom(e)
    .where(appIdEq(search.getAppId()), typeEq(search.getType()))   // null 조건은 자동 무시
    ...

private BooleanExpression appIdEq(String appId) {
    return StringUtils.hasText(appId) ? QEvent.event.appId.eq(appId) : null;
}
```

- 문자열 조건은 `StringUtils.hasText`, 객체 조건은 `!= null`로 판단해 null 반환.
- 페이징 = content 쿼리(`offset/limit`) + count 쿼리(같은 where) → `new PageImpl<>(content, pageable, total)`.
- 집계 결과가 null일 수 있으면(`avg`, 0건) 기본값으로 바꿔 반환. enum별 집계는 `EnumMap`에 모든 값을 0으로 채운 뒤 덮어쓴다.
- Q클래스는 annotationProcessor가 `build/generated/sources/annotationProcessor/`에 만든다. 엔티티를 추가·수정했으면 **먼저 compileJava**해야 `QX`가 보인다.

## 4. 알려진 함정 (코드에서 확인된 것)

- **정렬 파라미터 무시**: `EventCustomRepositoryImpl.search`가 `orderBy(e.occurredAt.desc())`로 고정이라 `?sort=occurredAt,asc`가 반영되지 않는다. 대시보드 `fetchEventSample`이 asc를 보내지만 실제로는 desc로 온다.
- **에러율 기준 불일치**: `findErrorRate`는 `statusCode >= 400` 기준, `EventType.ERROR`는 SDK가 예외 발생 여부로 분류한다. 두 숫자가 다를 수 있다.
- **stats 한 번에 쿼리 4번**(countByType·avg·errorRate의 total·errorCount). `totalCount`는 `countByType` 합으로 계산한다.
- **`ddl-auto: create`**: 서버를 재시작할 때마다 테이블을 지우고 다시 만든다. 데이터 유지가 필요하면 `update`(또는 마이그레이션 도구)로 바꿔야 한다 — 요청 없이 바꾸지 말 것.
- 설정 정본은 `application.yml` (`application.properties`는 삭제됨).
- `apm-root/apm-server/`에 별도 `gradlew`·`settings.gradle`이 남아 있지만 **루트에서 `apm-root/gradlew -p apm-root :apm-server:...`로 빌드**한다.
- `apm-root/apm-server/src/main/java/com/leekyungmi/apm/apm_sdk/build.gradle`은 잘못 들어간 파일로 보인다(빌드에 쓰이지 않음). 요청 없이 지우지 말고 언급만.
- CORS는 `/api/**`에 `http://localhost:*`만 허용(`WebConfig`). 새 API를 `/api` 밖에 두면 대시보드에서 막힌다.

## 5. 검증

```bash
apm-root/gradlew -p apm-root :apm-server:compileJava -q     # 컴파일 (Q클래스 생성 포함)
apm-root/gradlew -p apm-root :apm-server:test               # contextLoads는 PostgreSQL 필요
```
