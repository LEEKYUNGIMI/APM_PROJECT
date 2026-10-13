---
name: apm-dashboard-web
description: >-
  apm-root의 apm-dashboard(React 19 · TypeScript · Vite · Recharts 3 · dayjs · oxlint) 작업 규약.
  App.tsx 상태 흐름(필터·페이지·자동 새로고침 5초), src/api/client.ts(getJson · fetchEventPage · fetchEventSample · fetchStats · fetchApps · VITE_API_BASE_URL),
  src/types.ts 서버 DTO 타입, components(KpiCard · EventTable · FilterBar · ResponseTimeChart · EventTypeChart · StatusBadge),
  utils(buckets · format) 를 건드릴 때 반드시 먼저 읽을 것. "차트 추가", "필터 추가", "대시보드가 안 떠", "CORS 에러" 같은 주제도 대상이다.
---

# apm-dashboard 작업 규약

대상: `apm-root/apm-dashboard/` (Vite dev 포트 5173, API 기본 `http://localhost:8080`).

## 1. 구조

```
src/
├── main.tsx · App.tsx · App.css · index.css
├── types.ts                 # 서버 DTO와 1:1 (EventResponse, EventStatsResponse, PageResponse<T>, EventFilters)
├── api/client.ts            # 모든 HTTP 호출의 유일한 출입구
├── components/              # 프레젠테이션 컴포넌트, named export, Props interface
└── utils/{buckets,format}.ts# 순수 함수 (시간 버킷 집계, ms·%·숫자 포맷)
```

## 2. 규약

- **API**: 컴포넌트에서 `fetch` 금지 → `client.ts`에 `fetchXxx(filters, ...)` 함수 추가. `getJson`은 `undefined`·빈 문자열 파라미터를 자동으로 뺀다. 날짜는 `dayjs().format('YYYY-MM-DDTHH:mm:ss')`(타임존 없음, 서버 `LocalDateTime`과 맞춤).
- **타입**: 서버 응답 필드가 바뀌면 `types.ts`를 먼저 고친다. `EventType` 유니온은 서버 enum과 같은 철자.
- **상태**: 전역 상태 라이브러리 없음. `App.tsx`가 `filters/page/eventPage/sample/stats`를 들고 `loadData`(`Promise.all` 3건)로 갱신, `autoRefresh`면 5초 주기. 필터 바뀌면 `page`를 0으로.
- **차트**: Recharts. 시계열은 `fetchEventSample`(최대 300건) → `utils/buckets.ts`로 묶어서 그린다.
- **컴포넌트**: `export function X({...}: XProps)`, 함수 컴포넌트만. 클래스명 BEM 스타일(`kpi-card__label`, `kpi-card--bad`). 포맷은 `utils/format.ts` 재사용(`formatMs`, `formatPercent`, `formatNumber` ko-KR).
- **새 라이브러리 금지**(상태관리·UI킷·CSS 프레임워크). 필요하면 이유와 함께 제안만.

## 3. 알려진 함정

- `fetchEventSample`이 `sort=occurredAt,asc`를 보내지만 서버가 정렬을 무시하고 항상 최신순(desc)으로 준다(`apm-server-core` §4). `buckets.ts`가 클라이언트에서 다시 정렬하므로 순서는 맞지만, 기간 내 이벤트가 300건을 넘으면 **가장 오래된 300건이 아니라 최신 300건**만 차트에 들어간다.
- `fetchStats`는 `type`·`uri` 필터를 보내지 않는다 — KPI는 앱·기간 필터만 반영된다.
- CORS: 서버가 `http://localhost:*`만 허용. 다른 호스트로 띄우면 `WebConfig`를 바꿔야 한다.

## 4. 검증

```bash
cd apm-root/apm-dashboard
npm run build    # tsc -b && vite build — 타입 오류 확인
npm run lint     # oxlint
npm run dev      # 5173, apm-server 8080 필요
```
