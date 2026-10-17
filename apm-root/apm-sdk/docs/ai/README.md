# apm-sdk — AI 하네스 문서

모니터링 대상 Spring MVC 앱에 붙는 수집 라이브러리(jar, `bootJar` 비활성). 요청·쿼리·외부 호출마다 span 이벤트를 만들어 apm-server로 비동기 전송한다(W3C traceparent 전파).

- 스택: Java 21 · spring-boot-starter-web
- 사용하는 앱이 설정할 프로퍼티: `apm.server.url`, `apm.app.id` (기본값 없음 — 없으면 기동 실패)
- 빌드/테스트: 저장소 루트에서 `./gradlew :apm-sdk:compileJava` · `./gradlew :apm-sdk:test`

## 흐름

```
요청 스레드: ApmTraceFilter(/api/*, /api/apm/ 제외) → TraceContext.start(traceparent) → 서버 span
              ├ DataSource 프록시(jdbc) → 쿼리 1회 = DB_QUERY span (부모 = 서버 span)
              └ RestClient/RestTemplate 인터셉터(client) → HTTP_CLIENT span + traceparent 주입
            요청 종료 → 예외 또는 5xx=ERROR, ≥2000ms=SLOW_REQUEST, 그 외 HTTP_REQUEST
            → ApmEventSender.send() → RingBuffer.offer (가득 차면 버림 + droppedCount)
전송 스레드(apm-event-sender, 데몬): serverIp 1회 조회 · drainTo(100) → POST /api/events/batch 1회, 실패 시 로그 후 버림
종료: ApmEventSender 빈 destroyMethod=shutdown → 남은 이벤트 최대 3초 전송
```

| 패키지 | 내용 |
|--------|------|
| `config` | `ApmAutoConfig` (AutoConfiguration.imports 등록. 전송기 빈·필터·RestClient/RestTemplate 커스터마이저·DataSource BeanPostProcessor) |
| `interceptor` | `ApmTraceFilter` — 요청 1건 = 서버 span 1개 |
| `jdbc` | `ApmDataSourcePostProcessor`, `JdbcProxies` (java.lang.reflect.Proxy) |
| `client` | `ApmClientHttpInterceptor` |
| `sender` | `ApmEventSender` |
| `buffer` | `RingBuffer` — 설계 설명은 `docs/ring-buffer.md` |
| `trace` | `TraceContext` (ThreadLocal 현재 span, traceparent 파싱), `SpanContext` |

## 원칙

- 요청 스레드를 블로킹하지 않는다. 네트워크·sleep은 전송 스레드에서만.
- SDK 오류가 호스트 앱 요청을 실패시키면 안 된다.
- 테스트는 JUnit 5, 한글 메서드명. 동시성 테스트는 결정적 결과만 assert.

## 주의

- span은 요청 스레드에 현재 서버 span이 있을 때만 생긴다(기동 스크립트·별도 스레드의 쿼리/호출은 계측 안 됨).
- HTTP_CLIENT 계측은 `RestClient.Builder`/`RestTemplateBuilder`로 만든 클라이언트에만 붙는다. SDK 내부 전송용 RestTemplate은 `new`로 만들어 제외.
- 이벤트 키 이름은 서버 `EventRequest` 필드명과 같아야 한다(오타는 컴파일 에러 없이 null 저장).

상세 규약: 오케스트레이터의 `apm-sdk-core` 스킬
