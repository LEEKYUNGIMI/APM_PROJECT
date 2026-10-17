import { Fragment, useEffect, useState } from 'react'
import type { EventResponse } from '../types'
import { formatUs } from '../utils/format'
import { spanDurationUs, spanStartUs } from '../utils/span'
import { QueryList } from './QueryList'
import { SpanDetail } from './SpanDetail'
import { EventTypeBadge } from './StatusBadge'

interface TraceWaterfallProps {
  traceId: string
  spans: EventResponse[]
  loading: boolean
  error: string | null
  onClose: () => void
}

interface SpanRow {
  span: EventResponse
  depth: number
}

type Tab = 'waterfall' | 'queries'

/** parentSpanId 기준 DFS. 부모가 이 trace에 없으면(원격 부모 포함) 루트로 취급한다. */
function buildRows(spans: EventResponse[]): SpanRow[] {
  const ids = new Set(spans.map((s) => s.spanId))
  const children = new Map<string, EventResponse[]>()
  const roots: EventResponse[] = []

  spans.forEach((span) => {
    if (span.parentSpanId && ids.has(span.parentSpanId)) {
      const list = children.get(span.parentSpanId) ?? []
      list.push(span)
      children.set(span.parentSpanId, list)
    } else {
      roots.push(span)
    }
  })

  const byStart = (a: EventResponse, b: EventResponse) => spanStartUs(a) - spanStartUs(b)
  const rows: SpanRow[] = []
  const visit = (span: EventResponse, depth: number) => {
    rows.push({ span, depth })
    const kids = span.spanId ? (children.get(span.spanId) ?? []) : []
    kids.sort(byStart).forEach((kid) => visit(kid, depth + 1))
  }
  roots.sort(byStart).forEach((root) => visit(root, 0))
  return rows
}

export function TraceWaterfall({ traceId, spans, loading, error, onClose }: TraceWaterfallProps) {
  const [tab, setTab] = useState<Tab>('waterfall')
  const [selectedId, setSelectedId] = useState<number | null>(null)

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  const rows = buildRows(spans)
  const traceStart = Math.min(...spans.map(spanStartUs))
  const traceEnd = Math.max(...spans.map((s) => spanStartUs(s) + spanDurationUs(s)))
  const total = Math.max(traceEnd - traceStart, 1)
  const queryCount = spans.filter((s) => s.type === 'DB_QUERY').length

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal__header">
          <div>
            <h2>트레이스</h2>
            <div className="mono modal__subtitle">{traceId}</div>
          </div>
          <button type="button" className="btn" onClick={onClose}>
            닫기
          </button>
        </div>

        {loading && <div className="chart-empty">불러오는 중…</div>}
        {error && <div className="error-banner">⚠ {error}</div>}
        {!loading && !error && rows.length === 0 && (
          <div className="chart-empty">이 트레이스의 span이 없어요</div>
        )}

        {!loading && !error && rows.length > 0 && (
          <div className="range-toggle trace-tabs">
            <button
              type="button"
              className={tab === 'waterfall' ? 'range-btn range-btn--active' : 'range-btn'}
              onClick={() => setTab('waterfall')}
            >
              워터폴
            </button>
            <button
              type="button"
              className={tab === 'queries' ? 'range-btn range-btn--active' : 'range-btn'}
              onClick={() => setTab('queries')}
            >
              쿼리 목록 ({queryCount})
            </button>
          </div>
        )}

        {!loading && !error && rows.length > 0 && tab === 'queries' && <QueryList spans={spans} />}

        {!loading && !error && rows.length > 0 && tab === 'waterfall' && (
          <div className="waterfall">
            <div className="waterfall__summary">
              span {rows.length}개 · 전체 {formatUs(total)} · 행을 클릭하면 상세를 볼 수 있어요
            </div>
            {rows.map(({ span, depth }) => {
              const offset = spanStartUs(span) - traceStart
              const duration = spanDurationUs(span)
              const label = `${span.method} ${span.uri}`
              const isSelected = span.id === selectedId
              return (
                <Fragment key={span.id}>
                  <div
                    className={isSelected ? 'waterfall__row waterfall__row--selected' : 'waterfall__row'}
                    onClick={() => setSelectedId(isSelected ? null : span.id)}
                  >
                    <div className="waterfall__label" style={{ paddingLeft: depth * 16 }}>
                      <EventTypeBadge type={span.type} />
                      <span className="waterfall__name" title={span.message ? `${label}\n${span.message}` : label}>
                        {label}
                      </span>
                    </div>
                    <div className="waterfall__track">
                      <div
                        className={`waterfall__bar waterfall__bar--${span.type.toLowerCase()}`}
                        style={{
                          left: `${(offset / total) * 100}%`,
                          width: `${Math.max((duration / total) * 100, 0.5)}%`,
                        }}
                        title={`+${formatUs(offset)} · ${formatUs(duration)}`}
                      />
                    </div>
                    <div className="waterfall__duration mono">{formatUs(duration)}</div>
                  </div>
                  {isSelected && (
                    <SpanDetail span={span} traceStartUs={traceStart} onClose={() => setSelectedId(null)} />
                  )}
                </Fragment>
              )
            })}
          </div>
        )}
      </div>
    </div>
  )
}
