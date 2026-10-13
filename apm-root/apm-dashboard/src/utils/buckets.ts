import dayjs from 'dayjs'
import type { EventResponse } from '../types'

export interface Bucket {
  time: string
  avgDuration: number
  count: number
  errorCount: number
}

/**
 * events는 표본(최대 수백 건)이라 서버에 시계열 집계 쿼리를 새로 만드는 대신
 * 클라이언트에서 균등 시간 구간으로 나눠 평균/에러 수를 계산한다.
 */
export function buildBuckets(events: EventResponse[], bucketCount = 24): Bucket[] {
  if (events.length === 0) return []

  const sorted = [...events].sort(
    (a, b) => new Date(a.occurredAt).getTime() - new Date(b.occurredAt).getTime(),
  )
  const start = new Date(sorted[0].occurredAt).getTime()
  const end = new Date(sorted[sorted.length - 1].occurredAt).getTime()
  const bucketSize = Math.max((end - start) / bucketCount, 1)

  const raw = Array.from({ length: bucketCount }, (_, i) => ({
    time: start + i * bucketSize,
    sum: 0,
    count: 0,
    errorCount: 0,
  }))

  sorted.forEach((event) => {
    const occurredAt = new Date(event.occurredAt).getTime()
    const index = Math.min(Math.floor((occurredAt - start) / bucketSize), bucketCount - 1)
    const bucket = raw[Math.max(index, 0)]
    bucket.sum += event.durationMs
    bucket.count += 1
    if (event.statusCode >= 400) bucket.errorCount += 1
  })

  return raw.map((bucket) => ({
    time: dayjs(bucket.time).format('HH:mm:ss'),
    avgDuration: bucket.count > 0 ? Math.round(bucket.sum / bucket.count) : 0,
    count: bucket.count,
    errorCount: bucket.errorCount,
  }))
}
