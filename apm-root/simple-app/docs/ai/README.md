# simple-app — AI 하네스 문서

apm-sdk를 붙여 보는 데모 앱. 포트 8081, `apm.app.id: simple-app`.

- 실행: 저장소 루트에서 `./gradlew :simple-app:bootRun` (apm-server 8080이 떠 있어야 전송 성공)
- API: `/api/products` CRUD(메모리 저장) · `/api/products/slow`(2.5초 → SLOW_QUERY) · `/api/products/error`(예외 → ERROR)

## 주의

- SDK 자동설정(인터셉터)과 `config/ApmConfig`(두 번째 `ApmEventSender` + `ApmTraceFilter`)가 함께 동작해 `/api/**` 요청 1건에 이벤트가 2건 생긴다.
- `ApmConfig`의 서버 URL은 `http://localhost:8080` 하드코딩.

상세 규약: 오케스트레이터의 `apm-sdk-core` 스킬
