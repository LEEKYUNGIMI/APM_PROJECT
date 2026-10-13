---
name: apm-sdk-core
description: >-
  apm-root의 apm-sdk(모니터링 대상 Spring MVC 앱에 붙는 수집 라이브러리)와 simple-app(데모) 작업 규약.
  ApmAutoConfig 자동설정(AutoConfiguration.imports) · apm.server.url / apm.app.id 프로퍼티 · ApmInterceptor 이벤트 분류(ERROR · SLOW_QUERY 2000ms · HTTP_REQUEST) ·
  ApmTraceFilter / TraceContext(ThreadLocal traceId) · ApmEventSender 비동기 전송 · RingBuffer(offer · poll · drainTo · droppedCount) ·
  전송 스레드 apm-event-sender · shutdown/DisposableBean · 동시성 테스트를 건드릴 때 반드시 먼저 읽을 것.
  "이벤트가 두 번 찍혀" · "이벤트가 안 들어가" · "앱 종료 시 이벤트 유실" · "요청이 느려졌어" · "appId가 UUID로 찍혀" 같은 주제도 대상이다.
---

# apm-sdk / simple-app 작업 규약

대상: `apm-root/apm-sdk/` (패키지 `com.leekyungmi.apm.sdk`, 라이브러리 jar — `bootJar` 비활성), `apm-root/simple-app/` (포트 8081, SDK 의존).

## 1. 수집 흐름

```
[요청 스레드]                                         [전송 스레드: apm-event-sender, 데몬]
ApmInterceptor.preHandle   → request attr startTime
ApmInterceptor.afterCompletion
   ├─ type = ex!=null ? ERROR : duration>2000 ? SLOW_QUERY : HTTP_REQUEST
   └─ ApmEventSender.send(...) → RingBuffer.offer ──▶ drainTo(batch,100) → 건별 POST {apm.server.url}/api/events
                                 (가득 차면 버림)        (0건이면 100ms sleep)
```

| 파일 | 역할 |
|------|------|
| `config/ApmAutoConfig` | `META-INF/spring/...AutoConfiguration.imports`에 등록. 생성자에서 `ApmEventSender` 직접 생성(빈 아님), 인터셉터를 `/api/**`(제외 `/api/apm/**`)에 등록, `destroy()`에서 `shutdown()` |
| `interceptor/ApmInterceptor` | 소요시간·타입 분류·서버 IP 조회 후 `send` |
| `interceptor/ApmTraceFilter` + `trace/TraceContext` | ThreadLocal traceId 실험 코드. 자동설정에는 연결 안 됨 |
| `sender/ApmEventSender` | 버퍼 4096 · 배치 100 · 유휴 100ms. `send`는 버퍼에 넣기만, `occurredAt`은 send 시점 |
| `buffer/RingBuffer` | 2의 거듭제곱 용량, 누적 long head/tail, `synchronized`, 가득 차면 `false` + `droppedCount++` |

설계 배경: `apm-root/docs/ring-buffer.md`, `knot-vault/wiki/ring-buffer.md` · `drop-on-full.md` · `apm-event-sender.md`.

## 2. 라이브러리 원칙

1. **요청 스레드 비블로킹** — I/O·sleep·대기는 전송 스레드에서만. `offer`는 실패해도 즉시 반환.
2. **호스트 앱 보호** — 수집 경로 예외가 사용자 요청을 깨면 안 된다.
3. **유실은 집계** — 버린 이벤트는 `droppedCount`로 센다(아직 보고 경로 없음).
4. **종료 시 비우기** — 전송기를 만들면 반드시 `shutdown()`이 호출되는 경로(`DisposableBean`/`destroyMethod`)를 둔다.
5. **의존성 최소** — 현재 `spring-boot-starter-web`만.

## 3. 알려진 함정 (코드에서 확인된 것)

- **simple-app에서 이벤트 이중 수집**: SDK 자동설정이 인터셉터+전송기를 만들고, `apm-root/simple-app/.../ApmConfig`가 **두 번째** `ApmEventSender` 빈(URL 하드코딩 `http://localhost:8080`)과 `ApmTraceFilter`(`/*`)를 또 등록한다. `/api/**` 요청 1건에 이벤트 2건, 전송 스레드 2개. 필터 쪽 전송기는 `shutdown`이 연결되지 않았다.
- **필터 이벤트의 appId = traceId**: `ApmTraceFilter`가 `send(traceId, ...)`로 호출해 appId 자리에 UUID가 들어가고 serverIp는 null. 대시보드 앱 목록에 UUID가 쌓이는 원인.
- `ApmAutoConfig`는 `@AutoConfiguration`이 아니라 `@Configuration`이고, `@Value apmServerUrl` 필드는 생성자 파라미터와 중복(미사용). 프로퍼티가 없으면 앱 기동이 실패한다(기본값 없음).
- 타입 분류 문자열(`"ERROR"` 등)이 하드코딩 — 서버 `EventType` enum과 철자가 맞아야 한다(`apm-event-contract`).
- `InetAddress.getLocalHost()`를 요청마다 **요청 스레드에서** 호출 — 환경에 따라 느릴 수 있다.
- 예외가 `@ExceptionHandler`에서 처리되면 `afterCompletion`의 `ex`는 null이라 `ERROR`로 분류되지 않는다.
- 인터셉터의 `System.out.println` 디버그 출력, `Test.java`(ThreadLocal 실험), `send`의 파라미터명 오타 `sppId` — 요청 없으면 건드리지 말고 언급만.

## 4. 테스트 규약

- 위치 `apm-root/apm-sdk/src/test/java/...`, JUnit 5, **한글 메서드명** `가득_차면_버리고_dropped를_센다()`.
- 동시성 테스트는 `CountDownLatch`로 동시 출발, 결정적 결과만 assert. 경쟁 조건 재현은 출력만(불안정 테스트 금지).

```bash
apm-root/gradlew -p apm-root :apm-sdk:test
apm-root/gradlew -p apm-root :simple-app:bootRun     # 8081, apm-server(8080)가 떠 있어야 전송 성공
curl localhost:8081/api/products/slow    # SLOW_QUERY 시연
curl localhost:8081/api/products/error   # ERROR 시연
```
