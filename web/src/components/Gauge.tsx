/** Half-circle gauge: equity as a share of the position, against the maintenance line. Red below the line. */
export function Gauge({ valueBps, maintenanceBps, max = 10000 }: { valueBps: number; maintenanceBps: number; max?: number }) {
  const clamp = (v: number) => Math.max(0, Math.min(max, v))
  const ang = (v: number) => Math.PI * (1 - clamp(v) / max)
  const pt = (v: number, r: number) => [100 + r * Math.cos(ang(v)), 100 - r * Math.sin(ang(v))]
  const arc = (from: number, to: number, r: number) => { const a = pt(from, r), b = pt(to, r); return `M${a[0]},${a[1]} A${r},${r} 0 0 1 ${b[0]},${b[1]}` }
  const needle = pt(valueBps, 62), mark = [pt(maintenanceBps, 52), pt(maintenanceBps, 84)]
  const call = valueBps < maintenanceBps
  return (
    <svg viewBox="0 0 200 120" className="gauge" role="img" aria-label="margin equity gauge">
      <path d={arc(0, maintenanceBps, 78)} className="g-bad" />
      <path d={arc(maintenanceBps, max, 78)} className="g-ok" />
      <line x1={mark[0][0]} y1={mark[0][1]} x2={mark[1][0]} y2={mark[1][1]} className="g-mark" />
      <line x1="100" y1="100" x2={needle[0]} y2={needle[1]} className={call ? 'g-needle bad' : 'g-needle'} />
      <circle cx="100" cy="100" r="5" className="g-hub" />
      <text x="100" y="116" textAnchor="middle" className="g-text">{(valueBps / 100).toFixed(1)}%</text>
    </svg>
  )
}
