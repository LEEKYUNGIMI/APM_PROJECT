import type { EventResponse } from '../types'
import { formatUs } from '../utils/format'
import { spanDurationUs, spanStartUs } from '../utils/span'

interface QueryGroup {
  sql: string
  count: number
  totalUs: number
}

/** 트레이스 안 DB_QUERY를 실행 순서대로 + 동일 SQL 묶음 요약. 2회 이상 반복은 N+1 후보로 강조. */
export function QueryList({ spans }: { spans: EventResponse[] }) {
  const queries = spans
    .filter((s) => s.type === 'DB_QUERY')
    .sort((a, b) => spanStartUs(a) - spanStartUs(b))

  if (queries.length === 0) {
    return <div className="chart-empty">이 트레이스에는 DB 쿼리가 없어요</div>
  }

  const groupMap = new Map<string, QueryGroup>()
  queries.forEach((q) => {
    const group = groupMap.get(q.uri) ?? { sql: q.uri, count: 0, totalUs: 0 }
    group.count += 1
    group.totalUs += spanDurationUs(q)
    groupMap.set(q.uri, group)
  })
  const groups = [...groupMap.values()].sort((a, b) => b.count - a.count || b.totalUs - a.totalUs)
  const repeated = new Set(groups.filter((g) => g.count >= 2).map((g) => g.sql))
  const totalUs = queries.reduce((sum, q) => sum + spanDurationUs(q), 0)

  return (
    <div className="query-list">
      <div className="waterfall__summary">
        쿼리 {queries.length}개 · 고유 SQL {groups.length}개 · 합계 {formatUs(totalUs)}
        {repeated.size > 0 && <span className="query-list__hint"> · 반복 실행 SQL {repeated.size}개 (N+1 의심)</span>}
      </div>

      <h3 className="query-list__title">동일 SQL 요약</h3>
      <table className="query-table">
        <thead>
          <tr>
            <th>SQL</th>
            <th className="query-table__num">횟수</th>
            <th className="query-table__num">합계</th>
            <th className="query-table__num">평균</th>
          </tr>
        </thead>
        <tbody>
          {groups.map((g) => (
            <tr key={g.sql} className={g.count >= 2 ? 'query-table__row--repeated' : undefined}>
              <td className="query-table__sql mono">{g.sql}</td>
              <td className="query-table__num mono">
                {g.count}
                {g.count >= 2 && <span className="badge badge--warn query-list__badge">N+1?</span>}
              </td>
              <td className="query-table__num mono">{formatUs(g.totalUs)}</td>
              <td className="query-table__num mono">{formatUs(g.totalUs / g.count)}</td>
            </tr>
          ))}
        </tbody>
      </table>

      <h3 className="query-list__title">실행 순서</h3>
      <table className="query-table">
        <thead>
          <tr>
            <th className="query-table__num">#</th>
            <th>SQL</th>
            <th className="query-table__num">소요</th>
          </tr>
        </thead>
        <tbody>
          {queries.map((q, i) => (
            <tr key={q.id} className={repeated.has(q.uri) ? 'query-table__row--repeated' : undefined}>
              <td className="query-table__num mono">{i + 1}</td>
              <td className={`query-table__sql mono${q.statusCode !== 0 ? ' query-table__sql--error' : ''}`}>
                {q.uri}
              </td>
              <td className="query-table__num mono">{formatUs(spanDurationUs(q))}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
