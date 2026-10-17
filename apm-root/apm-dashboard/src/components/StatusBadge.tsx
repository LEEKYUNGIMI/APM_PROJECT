import type { EventType } from '../types'

const TYPE_LABEL: Record<EventType, string> = {
  HTTP_REQUEST: 'REQUEST',
  SLOW_REQUEST: 'SLOW',
  ERROR: 'ERROR',
  DB_QUERY: 'DB',
  HTTP_CLIENT: 'CLIENT',
}

export function EventTypeBadge({ type }: { type: EventType }) {
  return <span className={`badge badge--type-${type.toLowerCase()}`}>{TYPE_LABEL[type]}</span>
}

export function StatusCodeBadge({ statusCode }: { statusCode: number }) {
  const tone = statusCode >= 500 ? 'bad' : statusCode >= 400 ? 'warn' : 'good'
  return <span className={`badge badge--${tone}`}>{statusCode}</span>
}
