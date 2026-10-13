import dayjs from 'dayjs'
import type { EventFilters, EventResponse, EventStatsResponse, PageResponse } from '../types'

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

export function fetchEventSample(filters: EventFilters, size = 300): Promise<PageResponse<EventResponse>> {
  return getJson<PageResponse<EventResponse>>('/api/events', {
    ...searchParams(filters),
    page: 0,
    size,
    sort: 'occurredAt,asc',
  })
}

export function fetchStats(filters: EventFilters): Promise<EventStatsResponse> {
  return getJson<EventStatsResponse>('/api/events/stats', {
    appId: filters.appId || undefined,
    from: searchParams(filters).from,
  })
}

export function fetchApps(): Promise<string[]> {
  return getJson<string[]>('/api/events/apps')
}
