import { useState } from 'react'
import type { Candle } from '../types'
import { px, qty } from '../format'

/** Candlestick chart in SVG: wicks, bodies, volume bars, price grid, crosshair with OHLC. Green closes above the open. */
export function Candles({ data, width = 1000, height = 380, integer = false }: { data: Candle[]; width?: number; height?: number; integer?: boolean }) {
  const f = (v: number) => (integer ? Math.round(v).toLocaleString('en-US') : px(v))
  const [hover, setHover] = useState<number | null>(null)
  if (data.length < 2) return <div className="empty">No history</div>
  const pad = { l: 8, r: 64, t: 10, b: 26 }, volH = 56, w = width - pad.l - pad.r, h = height - pad.t - pad.b - volH
  const hi = Math.max(...data.map(c => c.high)), lo = Math.min(...data.map(c => c.low)), span = hi - lo || 1
  const maxV = Math.max(...data.map(c => c.volume)) || 1
  const step = w / data.length, bw = Math.max(1, Math.min(10, step * 0.7))
  const y = (v: number) => pad.t + h - ((v - lo) / span) * h
  const x = (i: number) => pad.l + step * (i + 0.5)
  const grid = Array.from({ length: 5 }, (_, i) => lo + (span * i) / 4)
  const labels = [0, Math.floor(data.length / 4), Math.floor(data.length / 2), Math.floor((data.length * 3) / 4), data.length - 1]
  const hc = hover !== null ? data[hover] : null
  return (
    <div className="chart-wrap">
      <svg viewBox={`0 0 ${width} ${height}`} className="candles" role="img" aria-label="candlestick chart"
        onMouseMove={e => { const r = (e.currentTarget as SVGSVGElement).getBoundingClientRect(); const px0 = ((e.clientX - r.left) / r.width) * width; setHover(Math.max(0, Math.min(data.length - 1, Math.floor((px0 - pad.l) / step)))) }}
        onMouseLeave={() => setHover(null)}>
        {grid.map(g => <g key={g}><line x1={pad.l} x2={pad.l + w} y1={y(g)} y2={y(g)} className="grid" /><text x={pad.l + w + 6} y={y(g) + 4} className="axis">{f(g)}</text></g>)}
        {data.map((c, i) => {
          const up = c.close >= c.open, top = y(Math.max(c.open, c.close)), bot = y(Math.min(c.open, c.close))
          return <g key={c.date} className={up ? 'cu' : 'cd'}>
            <line x1={x(i)} x2={x(i)} y1={y(c.high)} y2={y(c.low)} />
            <rect x={x(i) - bw / 2} y={top} width={bw} height={Math.max(1, bot - top)} />
            <rect x={x(i) - bw / 2} y={pad.t + h + volH - (c.volume / maxV) * (volH - 6)} width={bw} height={(c.volume / maxV) * (volH - 6)} className="vol" />
          </g>
        })}
        {labels.map(i => <text key={i} x={x(i)} y={height - 6} textAnchor="middle" className="axis">{data[i].date.slice(0, 7)}</text>)}
        {hover !== null && <line x1={x(hover)} x2={x(hover)} y1={pad.t} y2={pad.t + h + volH} className="cross" />}
        <line x1={pad.l} x2={pad.l + w} y1={y(data[data.length - 1].close)} y2={y(data[data.length - 1].close)} className="lastline" />
        <rect x={pad.l + w + 2} y={y(data[data.length - 1].close) - 9} width={58} height={18} rx={3} className="lasttag" /><text x={pad.l + w + 31} y={y(data[data.length - 1].close) + 4} textAnchor="middle" className="lasttext">{f(data[data.length - 1].close)}</text>
      </svg>
      <div className="ohlc muted small">{hc ? `${hc.date}   O ${f(hc.open)}   H ${f(hc.high)}   L ${f(hc.low)}   C ${f(hc.close)}   Vol ${qty(hc.volume)}` : ' '}</div>
    </div>
  )
}
