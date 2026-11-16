import type { Series } from '../types'

const COLORS = ['#0e7c86', '#c0392b', '#7b4fd1', '#d98a00', '#2a6fdb', '#0f8a5f']

/** Multi-series line chart of percentage change since the start of the range. */
export function LineChart({ series, width = 1000, height = 340 }: { series: Series[]; width?: number; height?: number }) {
  const all = series.flatMap(s => s.points)
  if (!all.length) return <div className="empty">Pick one or more symbols</div>
  const pad = { l: 8, r: 56, t: 12, b: 26 }, w = width - pad.l - pad.r, h = height - pad.t - pad.b
  const x0 = Math.min(...all.map(p => p[0])), x1 = Math.max(...all.map(p => p[0])), lo = Math.min(0, ...all.map(p => p[1])), hi = Math.max(0, ...all.map(p => p[1])), span = hi - lo || 1
  const X = (d: number) => pad.l + ((d - x0) / (x1 - x0 || 1)) * w, Y = (v: number) => pad.t + h - ((v - lo) / span) * h
  const ticks = Array.from({ length: 5 }, (_, i) => lo + (span * i) / 4)
  const dates = [x0, x0 + (x1 - x0) / 2, x1].map(d => new Date(d * 86400000).toISOString().slice(0, 7))
  return (
    <div className="chart-wrap">
      <svg viewBox={`0 0 ${width} ${height}`} className="candles" role="img" aria-label="comparison chart">
        {ticks.map(t => <g key={t}><line x1={pad.l} x2={pad.l + w} y1={Y(t)} y2={Y(t)} className="grid" /><text x={pad.l + w + 6} y={Y(t) + 4} className="axis">{(t / 100).toFixed(1)}%</text></g>)}
        <line x1={pad.l} x2={pad.l + w} y1={Y(0)} y2={Y(0)} className="zero" />
        {series.map((s, i) => <polyline key={s.symbol} fill="none" stroke={COLORS[i % COLORS.length]} strokeWidth={1.8} points={s.points.map(p => `${X(p[0]).toFixed(1)},${Y(p[1]).toFixed(1)}`).join(' ')} />)}
        {[0, 1, 2].map(i => <text key={i} x={pad.l + (w * i) / 2} y={height - 6} textAnchor={i === 0 ? 'start' : i === 2 ? 'end' : 'middle'} className="axis">{dates[i]}</text>)}
      </svg>
      <div className="legend">{series.map((s, i) => { const last = s.points[s.points.length - 1]; return <span key={s.symbol}><i style={{ background: COLORS[i % COLORS.length] }} />{s.symbol} <b className={last && last[1] >= 0 ? 'up' : 'down'}>{last ? `${(last[1] / 100).toFixed(2)}%` : ''}</b></span> })}</div>
    </div>
  )
}
