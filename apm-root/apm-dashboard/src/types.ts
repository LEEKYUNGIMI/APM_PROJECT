export type EventType = 'HTTP_REQUEST' | 'SLOW_QUERY' | 'ERROR'

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
  errorRatePercent: number
  countByType: Record<EventType, number>
}

export interface EventFilters {
  appId: string
  type: EventType | ''
  uri: string
  rangeMinutes: number | null
}
