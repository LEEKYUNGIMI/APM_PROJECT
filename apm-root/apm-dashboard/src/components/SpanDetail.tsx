import { useState } from 'react'
import type { EventResponse } from '../types'
import { formatUs } from '../utils/format'
import { formatSql, spanDurationUs, spanStartUs, spanStatusLabel } from '../utils/span'
import { EventTypeBadge } from './StatusBadge'

interface SpanDetailProps {
  span: EventResponse
  traceStartUs: number
  onClose: () => void
}

export function SpanDetail({ span, traceStartUs, onClose }: SpanDetailProps) {
  const [copied, setCopied] = useState(false)
  const isSql = span.type === 'DB_QUERY'
  const text = isSql ? span.uri : `${span.method} ${span.uri}`

  const copy = () => {
    navigator.clipboard.writeText(span.uri).then(
      () => {
        setCopied(true)
        setTimeout(() => setCopied(false), 1500)
      },
      () => setCopied(false),
    )
  }

  return (
    <div className="span-detail">
      <div className="span-detail__header">
        <EventTypeBadge type={span.type} />
        <dl className="span-detail__meta">
          <dt>상태</dt>
          <dd className="mono">{spanStatusLabel(span)}</dd>
          <dt>소요</dt>
          <dd className="mono">{formatUs(spanDurationUs(span))}</dd>
          <dt>시작</dt>
          <dd className="mono">+{formatUs(spanStartUs(span) - traceStartUs)}</dd>
        </dl>
        <div className="span-detail__actions">
          <button type="button" className="btn" onClick={copy}>
            {copied ? '복사됨' : isSql ? 'SQL 복사' : 'URI 복사'}
          </button>
          <button type="button" className="btn" onClick={onClose}>
            접기
          </button>
        </div>
      </div>
      <pre className="span-detail__text mono">{isSql ? formatSql(text) : text}</pre>
      {span.message && <pre className="span-detail__error mono">{span.message}</pre>}
    </div>
  )
}
