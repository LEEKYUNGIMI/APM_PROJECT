# apm-dashboard — AI 하네스 문서

apm-server 이벤트를 보여 주는 대시보드. KPI 카드(p95·p99 포함) · 퍼센타일/타입 차트 · 이벤트 테이블 · 트레이스 워터폴 · 필터 · 5초 자동 새로고침.

- 스택: React 19 · TypeScript · Vite · Recharts 3 · dayjs · oxlint
- 실행: `npm run dev` (5173) · 검증: `npm run build`(tsc 포함) · `npm run lint`
- API 주소: `VITE_API_BASE_URL` (기본 `http://localhost:8080`)

## 구조

```
src/
├── App.tsx            # 상태(filters·page·stats…) + loadData(page·timeseries·stats 병렬) + 트레이스 모달 상태 + 자동 새로고침
├── types.ts           # 서버 DTO와 1:1
├── api/client.ts      # 모든 HTTP 호출의 유일한 출입구
├── components/        # KpiCard · EventTable · FilterBar · PercentileChart · EventTypeChart · StatusBadge · TraceWaterfall(탭: 워터폴|쿼리 목록) · SpanDetail · QueryList
└── utils/             # format(ms·μs·%·숫자) · span(μs 우선 시작·소요, 상태 라벨, SQL 간이 줄바꿈)
```

## 규약

- 컴포넌트에서 `fetch` 직접 호출 금지 → `api/client.ts`에 함수 추가.
- 서버 응답이 바뀌면 `types.ts`부터 고친다.
- 상태관리·UI킷·CSS 프레임워크를 새로 들이지 않는다. 클래스명은 BEM 스타일.

## 주의

- 응답시간 차트는 서버 `/api/events/timeseries` 집계(요청 타입만). 버킷 크기 = rangeMinutes초('전체'는 3600초) → 약 60개 점.
- KPI(stats)·시계열에는 `type`·`uri` 필터가 반영되지 않는다(앱·기간만).
- 목록 `type` 미지정 = 요청 타입(HTTP_REQUEST·SLOW_REQUEST·ERROR)만. DB_QUERY·HTTP_CLIENT는 타입 필터로 보거나 행 클릭 → 트레이스 워터폴(`/api/traces/{traceId}`)에서 본다.

상세 규약: 오케스트레이터의 `apm-dashboard-web` 스킬
- 워터폴 오프셋·길이는 `startTimeUs`·`durationUs` 우선, null(1.5단계 이전 데이터)이면 ms 필드×1000. 표시는 `formatUs`(340μs · 1.23ms · 1.23s).
