export type EventType = 'HTTP_REQUEST' | 'SLOW_REQUEST' | 'ERROR' | 'DB_QUERY' | 'HTTP_CLIENT'

export interface EventResponse {
  id: number
  appId: string
  type: EventType
  uri: string
  method: string
  statusCode: number
  durationMs: number
  message: string | null
  serverIp: string | null
  occurredAt: string
  // 분산 추적 필드 — 계약 도입 이전에 저장된 이벤트는 null일 수 있다
  traceId: string | null
  spanId: string | null
  parentSpanId: string | null
  startTimeMs: number | null
  // μs 정밀도(1.5단계) — 이전에 저장된 이벤트는 null
  startTimeUs: number | null
  durationUs: number | null
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  first: boolean
  last: boolean
}

export interface EventStatsResponse {
  totalCount: number
  avgDurationMs: number
  p95DurationMs: number
  p99DurationMs: number
  errorRatePercent: number
  countByType: Partial<Record<EventType, number>>
}

export interface TimeseriesPoint {
  bucketStart: string
  count: number
  avgDurationMs: number
  p95DurationMs: number
  p99DurationMs: number
  errorCount: number
}

export interface EventFilters {
  appId: string
  type: EventType | ''
  uri: string
  rangeMinutes: number | null
}
