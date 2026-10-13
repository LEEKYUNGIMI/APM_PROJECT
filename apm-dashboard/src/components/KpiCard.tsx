type Tone = 'default' | 'good' | 'warn' | 'bad'

interface KpiCardProps {
  label: string
  value: string
  tone?: Tone
}

export function KpiCard({ label, value, tone = 'default' }: KpiCardProps) {
  return (
    <div className={`kpi-card kpi-card--${tone}`}>
      <div className="kpi-card__label">{label}</div>
      <div className="kpi-card__value">{value}</div>
    </div>
  )
}
