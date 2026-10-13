# apm-sdk — AI 하네스 문서

모니터링 대상 Spring MVC 앱에 붙는 수집 라이브러리(jar, `bootJar` 비활성). 요청마다 이벤트를 만들어 apm-server로 비동기 전송한다.

- 스택: Java 21 · spring-boot-starter-web
- 사용하는 앱이 설정할 프로퍼티: `apm.server.url`, `apm.app.id` (기본값 없음 — 없으면 기동 실패)
- 빌드/테스트: 저장소 루트에서 `./gradlew :apm-sdk:compileJava` · `./gradlew :apm-sdk:test`

## 흐름

```
요청 스레드: ApmInterceptor.afterCompletion → 타입 분류(예외=ERROR, >2000ms=SLOW_QUERY, 그 외 HTTP_REQUEST)
            → ApmEventSender.send() → RingBuffer.offer (가득 차면 버림 + droppedCount)
전송 스레드(apm-event-sender, 데몬): drainTo(100) → 건별 POST /api/events, 비면 100ms 대기
종료: ApmAutoConfig.destroy() → shutdown() → 남은 이벤트 최대 3초 전송
```

| 패키지 | 내용 |
|--------|------|
| `config` | `ApmAutoConfig` (AutoConfiguration.imports 등록, 인터셉터를 `/api/**`에 등록) |
| `interceptor` | `ApmInterceptor`, `ApmTraceFilter`(traceId 실험) |
| `sender` | `ApmEventSender` |
| `buffer` | `RingBuffer` — 설계 설명은 `docs/ring-buffer.md` |
| `trace` | `TraceContext` (ThreadLocal) |

## 원칙

- 요청 스레드를 블로킹하지 않는다. 네트워크·sleep은 전송 스레드에서만.
- SDK 오류가 호스트 앱 요청을 실패시키면 안 된다.
- 테스트는 JUnit 5, 한글 메서드명. 동시성 테스트는 결정적 결과만 assert.

## 주의

- `ApmTraceFilter`는 traceId를 appId 자리에 넣어 보낸다.
- 이벤트 키 이름은 서버 `EventRequest` 필드명과 같아야 한다(오타는 컴파일 에러 없이 null 저장).

상세 규약: 오케스트레이터의 `apm-sdk-core` 스킬
