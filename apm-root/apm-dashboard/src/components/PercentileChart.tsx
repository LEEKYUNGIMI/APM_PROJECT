import dayjs from 'dayjs'
import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { TimeseriesPoint } from '../types'
import { formatMs } from '../utils/format'

const SERIES = [
  { key: 'avgDurationMs', name: '평균', color: '#38bdf8' },
  { key: 'p95DurationMs', name: 'p95', color: '#fbbf24' },
  { key: 'p99DurationMs', name: 'p99', color: '#f87171' },
] as const

export function PercentileChart({ points }: { points: TimeseriesPoint[] }) {
  if (points.length === 0) {
    return <div className="chart-empty">표시할 데이터가 없어요</div>
  }

  const data = points.map((p) => ({ ...p, time: dayjs(p.bucketStart).format('HH:mm:ss') }))

  return (
    <ResponsiveContainer width="100%" height={260}>
      <LineChart data={data} margin={{ top: 8, right: 16, left: 0, bottom: 0 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="#1f2937" />
        <XAxis dataKey="time" stroke="#8b96a5" fontSize={12} />
        <YAxis stroke="#8b96a5" fontSize={12} unit="ms" />
        <Tooltip
          contentStyle={{ background: '#121924', border: '1px solid #1f2937', borderRadius: 8 }}
          labelStyle={{ color: '#e5e9f0' }}
          formatter={(value, name) => [formatMs(Number(value)), name]}
        />
        <Legend wrapperStyle={{ fontSize: 12 }} />
        {SERIES.map((s) => (
          <Line
            key={s.key}
            type="monotone"
            dataKey={s.key}
            name={s.name}
            stroke={s.color}
            strokeWidth={2}
            dot={false}
          />
        ))}
      </LineChart>
    </ResponsiveContainer>
  )
}
