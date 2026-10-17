import type { EventFilters, EventType } from '../types'

interface FilterBarProps {
  filters: EventFilters
  apps: string[]
  onChange: (filters: EventFilters) => void
  autoRefresh: boolean
  onToggleAutoRefresh: () => void
  onManualRefresh: () => void
  lastUpdated: Date | null
}

const RANGE_OPTIONS: { label: string; value: number | null }[] = [
  { label: '15분', value: 15 },
  { label: '1시간', value: 60 },
  { label: '6시간', value: 360 },
  { label: '24시간', value: 1440 },
  { label: '전체', value: null },
]

const TYPE_OPTIONS: { label: string; value: EventType | '' }[] = [
  { label: '요청 전체', value: '' },
  { label: 'HTTP_REQUEST', value: 'HTTP_REQUEST' },
  { label: 'SLOW_REQUEST', value: 'SLOW_REQUEST' },
  { label: 'ERROR', value: 'ERROR' },
  { label: 'DB_QUERY', value: 'DB_QUERY' },
  { label: 'HTTP_CLIENT', value: 'HTTP_CLIENT' },
]

export function FilterBar({
  filters,
  apps,
  onChange,
  autoRefresh,
  onToggleAutoRefresh,
  onManualRefresh,
  lastUpdated,
}: FilterBarProps) {
  return (
    <div className="filter-bar">
      <div className="filter-bar__group">
        <select
          className="filter-select"
          value={filters.appId}
          onChange={(e) => onChange({ ...filters, appId: e.target.value })}
        >
          <option value="">전체 앱</option>
          {apps.map((app) => (
            <option key={app} value={app}>
              {app}
            </option>
          ))}
        </select>

        <select
          className="filter-select"
          value={filters.type}
          onChange={(e) => onChange({ ...filters, type: e.target.value as EventType | '' })}
        >
          {TYPE_OPTIONS.map((opt) => (
            <option key={opt.label} value={opt.value}>
              {opt.label}
            </option>
          ))}
        </select>

        <input
          className="filter-input"
          placeholder="URI 검색 (예: /api/products)"
          value={filters.uri}
          onChange={(e) => onChange({ ...filters, uri: e.target.value })}
        />
      </div>

      <div className="filter-bar__group">
        <div className="range-toggle">
          {RANGE_OPTIONS.map((opt) => (
            <button
              key={opt.label}
              type="button"
              className={filters.rangeMinutes === opt.value ? 'range-btn range-btn--active' : 'range-btn'}
              onClick={() => onChange({ ...filters, rangeMinutes: opt.value })}
            >
              {opt.label}
            </button>
          ))}
        </div>
      </div>

      <div className="filter-bar__group filter-bar__group--right">
        {lastUpdated && (
          <span className="last-updated">업데이트 {lastUpdated.toLocaleTimeString('ko-KR')}</span>
        )}
        <button type="button" className="btn" onClick={onManualRefresh}>
          새로고침
        </button>
        <label className="auto-refresh-toggle">
          <input type="checkbox" checked={autoRefresh} onChange={onToggleAutoRefresh} />
          실시간 갱신 (5s)
        </label>
      </div>
    </div>
  )
}
