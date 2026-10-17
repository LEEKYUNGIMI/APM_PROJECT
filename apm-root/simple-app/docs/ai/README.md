# simple-app — AI 하네스 문서

apm-sdk를 붙여 보는 데모 앱. 포트 8081, `apm.app.id: simple-app`.

- 실행: 저장소 루트에서 `./gradlew :simple-app:bootRun` (apm-server 8080이 떠 있어야 전송 성공)
- DB: H2 메모리 + JDBC(`schema.sql`·`data.sql`, products 20건, H2 함수 `SLEEP` = `db/H2Functions`)
- API: `/api/products` CRUD(JdbcTemplate) · `/api/products/n-plus-one`(N+1, 쿼리당 110ms → SLOW_REQUEST + DB_QUERY 21개) · `/api/products/slow`(2.5초 → SLOW_REQUEST) · `/api/products/error`(예외 → ERROR) · `/api/orders`(RestClient로 자기 `/api/products` 호출 → 한 trace에 서버 span 2개 + HTTP_CLIENT)
- SDK 자동설정만 사용한다(앱 쪽 별도 APM 설정 없음).

상세 규약: 오케스트레이터의 `apm-sdk-core` 스킬
