export function formatMs(ms: number): string {
  if (!Number.isFinite(ms)) return '-'
  if (ms >= 1000) return `${(ms / 1000).toFixed(2)}s`
  return `${Math.round(ms)}ms`
}

/** 1ms 미만은 `340μs`, 1s 미만은 `1.23ms`, 그 이상은 `1.23s`. */
export function formatUs(us: number): string {
  if (!Number.isFinite(us)) return '-'
  if (us < 1000) return `${Math.round(us)}μs`
  if (us >= 1_000_000) return `${(us / 1_000_000).toFixed(2)}s`
  return `${(us / 1000).toFixed(2)}ms`
}

export function formatPercent(value: number): string {
  if (!Number.isFinite(value)) return '-'
  return `${value.toFixed(1)}%`
}

export function formatNumber(value: number): string {
  return value.toLocaleString('ko-KR')
}
