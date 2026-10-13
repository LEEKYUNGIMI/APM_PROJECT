---
name: apm-event-contract
description: >-
  apm-root 모듈 간 이벤트 데이터 계약(SDK → apm-server → apm-dashboard). 이벤트 필드 추가·삭제·이름 변경,
  EventType 값(HTTP_REQUEST · SLOW_QUERY · ERROR) 추가, /api/events 요청·응답 형태 변경, 날짜 형식,
  "SDK가 보낸 값이 서버에 null로 저장돼", "대시보드에 새 필드가 안 보여" 같은 모듈 경계 문제를 다룰 때 반드시 먼저 읽을 것.
---

# 이벤트 계약 (SDK ↔ Server ↔ Dashboard)

한 필드가 **세 모듈 다섯 군데 이상**에 흩어져 있다. 한 곳만 고치면 조용히 null이 저장되거나 화면에서 사라진다.

## 1. 현재 필드

| 필드 | 타입(서버) | SDK에서 채우는 곳 | 비고 |
|------|-----------|-----------------|------|
| `appId` | String | `apm.app.id` | 필터 기준 |
| `type` | `EventType` | `ApmInterceptor` 분류 문자열 | enum 철자 일치 필수 |
| `uri` | String | `request.getRequestURI()` | 대시보드 부분일치 검색 |
| `method` | String | `request.getMethod()` | |
| `statusCode` | int | `response.getStatus()` | 서버 에러율 기준(≥400) |
| `durationMs` | int | 인터셉터 측정 | SLOW 기준 2000ms |
| `message` | String | 예외 메시지 | 없으면 null |
| `serverIp` | String | `InetAddress.getLocalHost()` | |
| `occurredAt` | LocalDateTime | `send()` 시점 `LocalDateTime.now()` | ISO-8601, 타임존 없음 |
| `id` | Long | (서버 생성) | 응답에만 |

## 2. 엔드포인트

| 메서드 · 경로 | 요청 | 응답 | 사용처 |
|--------------|------|------|-------|
| `POST /api/events` | `EventRequest` JSON (단건) | 생성된 id(Long) | SDK 전송 스레드 |
| `GET /api/events` | `appId,type,uri,from,to,page,size` | `Page<EventResponse>` | 대시보드 테이블·차트 샘플 |
| `GET /api/events/stats` | `appId,from,to` | `EventStatsResponse{totalCount,avgDurationMs,errorRatePercent,countByType}` | KPI·타입 차트 |
| `GET /api/events/apps` | — | `string[]` | 앱 필터 목록 |

## 3. 필드 추가 체크리스트 (순서대로)

1. 서버 `entity/Event.java` 필드 (`ddl-auto: create`라 컬럼은 재시작 시 생김)
2. 서버 `dto/EventRequest.java` 필드 + `toEntity()`
3. 서버 `dto/EventResponse.java` 필드 + `from()`
4. 검색 조건이면 `EventSearch` + `EventCustomRepositoryImpl`에 `BooleanExpression` 헬퍼
5. SDK `ApmEventSender.send(...)` 시그니처와 `body.put(...)` (키 이름 = 서버 필드명)
6. SDK 호출부 `ApmInterceptor`(필요하면 `ApmTraceFilter`)
7. 대시보드 `src/types.ts` → `api/client.ts`(필터면) → 컴포넌트
8. 검증: `apm-root/gradlew -p apm-root :apm-server:compileJava :apm-sdk:compileJava -q` + `cd apm-root/apm-dashboard && npm run build`

`EventType` 값 추가도 같은 방식: 서버 enum · SDK 분류 문자열 · 대시보드 `EventType` 유니온 · `FilterBar` 옵션 · `StatusBadge` 라벨 · `EventTypeChart` 색상. 서버 `countByType`은 `EnumMap`이라 자동 반영된다.

## 4. 주의

- SDK는 `Map<String,Object>`로 보내므로 **키 오타는 컴파일 에러가 아니라 서버 null 저장**으로 나타난다.
- 서버가 모르는 키는 Jackson 기본 설정에서 무시된다 — 역시 조용히 사라진다.
