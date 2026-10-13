import { Bar, BarChart, CartesianGrid, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { EventStatsResponse } from '../types'

const COLORS: Record<string, string> = {
  HTTP_REQUEST: '#38bdf8',
  SLOW_QUERY: '#fbbf24',
  ERROR: '#f87171',
}

export function EventTypeChart({ countByType }: { countByType: EventStatsResponse['countByType'] | null }) {
  const data = Object.entries(countByType ?? {}).map(([type, count]) => ({ type, count }))
  const hasData = data.some((d) => d.count > 0)

  if (!hasData) {
    return <div className="chart-empty">표시할 데이터가 없어요</div>
  }

  return (
    <ResponsiveContainer width="100%" height={260}>
      <BarChart data={data} margin={{ top: 8, right: 16, left: 0, bottom: 0 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="#1f2937" />
        <XAxis dataKey="type" stroke="#8b96a5" fontSize={12} />
        <YAxis stroke="#8b96a5" fontSize={12} allowDecimals={false} />
        <Tooltip
          contentStyle={{ background: '#121924', border: '1px solid #1f2937', borderRadius: 8 }}
          labelStyle={{ color: '#e5e9f0' }}
        />
        <Bar dataKey="count" radius={[6, 6, 0, 0]}>
          {data.map((entry) => (
            <Cell key={entry.type} fill={COLORS[entry.type] ?? '#38bdf8'} />
          ))}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  )
}
