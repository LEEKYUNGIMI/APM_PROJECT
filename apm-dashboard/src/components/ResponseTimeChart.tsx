import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { Bucket } from '../utils/buckets'

export function ResponseTimeChart({ buckets }: { buckets: Bucket[] }) {
  if (buckets.length === 0) {
    return <div className="chart-empty">표시할 데이터가 없어요</div>
  }

  return (
    <ResponsiveContainer width="100%" height={260}>
      <AreaChart data={buckets} margin={{ top: 8, right: 16, left: 0, bottom: 0 }}>
        <defs>
          <linearGradient id="durationGradient" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#38bdf8" stopOpacity={0.35} />
            <stop offset="100%" stopColor="#38bdf8" stopOpacity={0} />
          </linearGradient>
        </defs>
        <CartesianGrid strokeDasharray="3 3" stroke="#1f2937" />
        <XAxis dataKey="time" stroke="#8b96a5" fontSize={12} />
        <YAxis stroke="#8b96a5" fontSize={12} unit="ms" />
        <Tooltip
          contentStyle={{ background: '#121924', border: '1px solid #1f2937', borderRadius: 8 }}
          labelStyle={{ color: '#e5e9f0' }}
          formatter={(value) => [`${value}ms`, '평균 응답시간']}
        />
        <Area
          type="monotone"
          dataKey="avgDuration"
          stroke="#38bdf8"
          fill="url(#durationGradient)"
          strokeWidth={2}
        />
      </AreaChart>
    </ResponsiveContainer>
  )
}
