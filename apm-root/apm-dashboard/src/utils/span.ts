import dayjs from 'dayjs'
import type { EventResponse } from '../types'

/** span 시작(epoch μs). startTimeUs 우선, 없으면 startTimeMs, 그것도 없거나 0이면 occurredAt. */
export function spanStartUs(span: EventResponse): number {
  if (span.startTimeUs != null) return span.startTimeUs
  return (span.startTimeMs || dayjs(span.occurredAt).valueOf()) * 1000
}

/** span 소요(μs). durationUs 우선, 없으면 durationMs. */
export function spanDurationUs(span: EventResponse): number {
  return span.durationUs ?? span.durationMs * 1000
}

/** 상태 코드 표시. DB_QUERY 0 = 성공, HTTP_CLIENT 0 = 연결 실패. */
export function spanStatusLabel(span: EventResponse): string {
  if (span.type === 'DB_QUERY') return span.statusCode === 0 ? '0 (성공)' : `${span.statusCode} (SQLException)`
  if (span.type === 'HTTP_CLIENT' && span.statusCode === 0) return '0 (연결 실패)'
  return String(span.statusCode)
}

// JOIN 수식어(LEFT·OUTER 등) 사이에서는 끊지 않는다
const SQL_CLAUSE =
  /(?<!\b(?:LEFT|RIGHT|FULL|INNER|OUTER|CROSS))\s+(?=(?:FROM|WHERE|AND|OR|GROUP BY|ORDER BY|HAVING|LIMIT|OFFSET|VALUES|SET|UNION|(?:(?:LEFT|RIGHT|FULL|INNER|OUTER|CROSS)\s+)*JOIN)\b)/gi

/** 주요 SQL 절 앞에서 줄바꿈(AND·OR는 들여쓰기). 표시용 간이 포맷. */
export function formatSql(sql: string): string {
  return sql.replace(SQL_CLAUSE, (_, offset: number) => {
    return /^(?:AND|OR)\b/i.test(sql.slice(offset).trimStart()) ? '\n  ' : '\n'
  })
}
