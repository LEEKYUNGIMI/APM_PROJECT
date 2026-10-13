import dayjs from 'dayjs'
import type { EventResponse, PageResponse } from '../types'
import { EventTypeBadge, StatusCodeBadge } from './StatusBadge'
import { formatMs, formatNumber } from '../utils/format'

interface EventTableProps {
  page: PageResponse<EventResponse> | null
  loading: boolean
  onPrev: () => void
  onNext: () => void
}

export function EventTable({ page, loading, onPrev, onNext }: EventTableProps) {
  const isEmpty = !loading && (page?.content.length ?? 0) === 0

  return (
    <div className="table-card">
      <div className="table-scroll">
        <table className="event-table">
          <thead>
            <tr>
              <th>시간</th>
              <th>앱</th>
              <th>타입</th>
              <th>METHOD</th>
              <th>URI</th>
              <th>STATUS</th>
              <th>응답시간</th>
              <th>메시지</th>
            </tr>
          </thead>
          <tbody>
            {page?.content.map((event) => (
              <tr key={event.id}>
                <td className="mono">{dayjs(event.occurredAt).format('MM/DD HH:mm:ss')}</td>
                <td>{event.appId}</td>
                <td>
                  <EventTypeBadge type={event.type} />
                </td>
                <td className="mono">{event.method}</td>
                <td className="uri-cell" title={event.uri}>
                  {event.uri}
                </td>
                <td>
                  <StatusCodeBadge statusCode={event.statusCode} />
                </td>
                <td className={event.durationMs > 2000 ? 'mono duration-slow' : 'mono'}>
                  {formatMs(event.durationMs)}
                </td>
                <td className="message-cell" title={event.message ?? ''}>
                  {event.message ?? '-'}
                </td>
              </tr>
            ))}
            {isEmpty && (
              <tr>
                <td colSpan={8} className="empty-row">
                  조건에 맞는 이벤트가 없어요
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      <div className="table-footer">
        <span>
          {page
            ? `${page.number + 1} / ${Math.max(page.totalPages, 1)} 페이지 · 총 ${formatNumber(page.totalElements)}건`
            : ''}
        </span>
        <div className="table-pagination">
          <button type="button" className="btn" onClick={onPrev} disabled={!page || page.first}>
            이전
          </button>
          <button type="button" className="btn" onClick={onNext} disabled={!page || page.last}>
            다음
          </button>
        </div>
      </div>
    </div>
  )
}
