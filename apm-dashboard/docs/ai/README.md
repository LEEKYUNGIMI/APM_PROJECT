# apm-dashboard — AI 하네스 문서

apm-server 이벤트를 보여 주는 대시보드. KPI 카드 · 응답시간/타입 차트 · 이벤트 테이블 · 필터 · 5초 자동 새로고침.

- 스택: React 19 · TypeScript · Vite · Recharts 3 · dayjs · oxlint
- 실행: `npm run dev` (5173) · 검증: `npm run build`(tsc 포함) · `npm run lint`
- API 주소: `VITE_API_BASE_URL` (기본 `http://localhost:8080`)

## 구조

```
src/
├── App.tsx            # 상태(filters·page·stats…) + loadData(3건 병렬) + 자동 새로고침
├── types.ts           # 서버 DTO와 1:1
├── api/client.ts      # 모든 HTTP 호출의 유일한 출입구
├── components/        # KpiCard · EventTable · FilterBar · ResponseTimeChart · EventTypeChart · StatusBadge
└── utils/             # buckets(시간 버킷) · format(ms·%·숫자)
```

## 규약

- 컴포넌트에서 `fetch` 직접 호출 금지 → `api/client.ts`에 함수 추가.
- 서버 응답이 바뀌면 `types.ts`부터 고친다.
- 상태관리·UI킷·CSS 프레임워크를 새로 들이지 않는다. 클래스명은 BEM 스타일.

## 주의

- 차트 샘플은 최대 300건이고 서버가 최신순으로 줘서, 기간 내 이벤트가 많으면 최신 300건만 그려진다.
- KPI(stats)에는 `type`·`uri` 필터가 반영되지 않는다.

상세 규약: 오케스트레이터의 `apm-dashboard-web` 스킬
