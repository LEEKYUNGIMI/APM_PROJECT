import dayjs from 'dayjs'
import type { EventFilters, EventResponse, EventStatsResponse, PageResponse, TimeseriesPoint } from '../types'

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'
const DATE_FORMAT = 'YYYY-MM-DDTHH:mm:ss'

async function getJson<T>(path: string, params: Record<string, string | number | undefined> = {}): Promise<T> {
  const url = new URL(path, BASE_URL)
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== '') {
      url.searchParams.set(key, String(value))
    }
  })

  const res = await fetch(url)
  if (!res.ok) {
    throw new Error(`API 요청 실패 (${res.status}): ${path}`)
  }
  return res.json() as Promise<T>
}

function searchParams(filters: EventFilters) {
  const from = filters.rangeMinutes
    ? dayjs().subtract(filters.rangeMinutes, 'minute').format(DATE_FORMAT)
    : undefined

  return {
    appId: filters.appId || undefined,
    type: filters.type || undefined,
    uri: filters.uri || undefined,
    from,
  }
}

export function fetchEventPage(
  filters: EventFilters,
  page: number,
  size: number,
): Promise<PageResponse<EventResponse>> {
  return getJson<PageResponse<EventResponse>>('/api/events', {
    ...searchParams(filters),
    page,
    size,
    sort: 'occurredAt,desc',
  })
}

export function fetchStats(filters: EventFilters): Promise<EventStatsResponse> {
  return getJson<EventStatsResponse>('/api/events/stats', {
    appId: filters.appId || undefined,
    from: searchParams(filters).from,
  })
}

/** 요청 타입만 집계된다(서버 계약). type·uri 필터는 반영되지 않는다. */
export function fetchTimeseries(filters: EventFilters, bucketSeconds: number): Promise<TimeseriesPoint[]> {
  return getJson<TimeseriesPoint[]>('/api/events/timeseries', {
    appId: filters.appId || undefined,
    from: searchParams(filters).from,
    bucketSeconds,
  })
}

export function fetchTrace(traceId: string): Promise<EventResponse[]> {
  return getJson<EventResponse[]>(`/api/traces/${encodeURIComponent(traceId)}`)
}

export function fetchApps(): Promise<string[]> {
  return getJson<string[]>('/api/events/apps')
}
