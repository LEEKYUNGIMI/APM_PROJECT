import { useCallback, useEffect, useState } from 'react'
import './App.css'
import { fetchApps, fetchEventPage, fetchStats, fetchTimeseries, fetchTrace } from './api/client'
import { EventTable } from './components/EventTable'
import { EventTypeChart } from './components/EventTypeChart'
import { FilterBar } from './components/FilterBar'
import { KpiCard } from './components/KpiCard'
import { PercentileChart } from './components/PercentileChart'
import { TraceWaterfall } from './components/TraceWaterfall'
import type { EventFilters, EventResponse, EventStatsResponse, PageResponse, TimeseriesPoint } from './types'
import { formatMs, formatNumber, formatPercent } from './utils/format'

const PAGE_SIZE = 10
const AUTO_REFRESH_INTERVAL_MS = 5000

const DEFAULT_FILTERS: EventFilters = {
  appId: '',
  type: '',
  uri: '',
  rangeMinutes: 60,
}

/** 어느 기간이든 약 60개 버킷이 되도록 맞춘다. '전체'는 1시간 버킷. */
function bucketSecondsFor(rangeMinutes: number | null): number {
  return rangeMinutes ?? 3600
}

function App() {
  const [apps, setApps] = useState<string[]>([])
  const [filters, setFilters] = useState<EventFilters>(DEFAULT_FILTERS)
  const [page, setPage] = useState(0)
  const [eventPage, setEventPage] = useState<PageResponse<EventResponse> | null>(null)
  const [timeseries, setTimeseries] = useState<TimeseriesPoint[]>([])
  const [stats, setStats] = useState<EventStatsResponse | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [autoRefresh, setAutoRefresh] = useState(true)
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null)
  const [traceId, setTraceId] = useState<string | null>(null)
  const [traceSpans, setTraceSpans] = useState<EventResponse[]>([])
  const [traceLoading, setTraceLoading] = useState(false)
  const [traceError, setTraceError] = useState<string | null>(null)

  useEffect(() => {
    fetchApps()
      .then(setApps)
      .catch(() => setApps([]))
  }, [])

  const handleFiltersChange = useCallback((next: EventFilters) => {
    setFilters(next)
    setPage(0)
  }, [])

  const loadData = useCallback(async () => {
    setLoading(true)
    try {
      const [eventsRes, timeseriesRes, statsRes] = await Promise.all([
        fetchEventPage(filters, page, PAGE_SIZE),
        fetchTimeseries(filters, bucketSecondsFor(filters.rangeMinutes)),
        fetchStats(filters),
      ])
      setEventPage(eventsRes)
      setTimeseries(timeseriesRes)
      setStats(statsRes)
      setError(null)
      setLastUpdated(new Date())
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : 'API 서버에 연결할 수 없어요. apm-server(8080)가 실행 중인지 확인해주세요.',
      )
    } finally {
      setLoading(false)
    }
  }, [filters, page])

  useEffect(() => {
    loadData()
  }, [loadData])

  useEffect(() => {
    if (!autoRefresh) return
    const id = setInterval(loadData, AUTO_REFRESH_INTERVAL_MS)
    return () => clearInterval(id)
  }, [autoRefresh, loadData])

  const handleSelectTrace = useCallback(async (id: string) => {
    setTraceId(id)
    setTraceSpans([])
    setTraceError(null)
    setTraceLoading(true)
    try {
      setTraceSpans(await fetchTrace(id))
    } catch (err) {
      setTraceError(err instanceof Error ? err.message : '트레이스를 불러오지 못했어요.')
    } finally {
      setTraceLoading(false)
    }
  }, [])

  const handleCloseTrace = useCallback(() => setTraceId(null), [])

  const slowCount = stats?.countByType.SLOW_REQUEST ?? 0
  const errorRate = stats?.errorRatePercent ?? 0

  return (
    <div className="app">
      <header className="app-header">
        <div>
          <h1>APM Dashboard</h1>
          <p className="app-subtitle">실시간 애플리케이션 성능 모니터링</p>
        </div>
      </header>

      <FilterBar
        filters={filters}
        apps={apps}
        onChange={handleFiltersChange}
        autoRefresh={autoRefresh}
        onToggleAutoRefresh={() => setAutoRefresh((prev) => !prev)}
        onManualRefresh={loadData}
        lastUpdated={lastUpdated}
      />

      {error && <div className="error-banner">⚠ {error}</div>}

      <section className="kpi-grid">
        <KpiCard label="총 요청" value={formatNumber(stats?.totalCount ?? 0)} />
        <KpiCard label="평균 응답시간" value={formatMs(stats?.avgDurationMs ?? 0)} />
        <KpiCard label="p95 응답시간" value={formatMs(stats?.p95DurationMs ?? 0)} />
        <KpiCard label="p99 응답시간" value={formatMs(stats?.p99DurationMs ?? 0)} />
        <KpiCard label="에러율" value={formatPercent(errorRate)} tone={errorRate > 5 ? 'bad' : 'default'} />
        <KpiCard
          label="느린 요청 (SLOW_REQUEST)"
          value={formatNumber(slowCount)}
          tone={slowCount > 0 ? 'warn' : 'default'}
        />
      </section>

      <section className="chart-grid">
        <div className="chart-card">
          <h2>응답시간 추이 (평균 · p95 · p99)</h2>
          <PercentileChart points={timeseries} />
        </div>
        <div className="chart-card">
          <h2>이벤트 타입 분포</h2>
          <EventTypeChart countByType={stats?.countByType ?? null} />
        </div>
      </section>

      <section>
        <h2 className="section-title">최근 이벤트</h2>
        <EventTable
          page={eventPage}
          loading={loading}
          onPrev={() => setPage((p) => Math.max(p - 1, 0))}
          onNext={() => setPage((p) => p + 1)}
          onSelectTrace={handleSelectTrace}
        />
      </section>

      {traceId && (
        <TraceWaterfall
          traceId={traceId}
          spans={traceSpans}
          loading={traceLoading}
          error={traceError}
          onClose={handleCloseTrace}
        />
      )}
    </div>
  )
}

export default App
