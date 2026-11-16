interface Props { values: number[]; width?: number; height?: number; area?: boolean; up?: boolean | null }

/** Tiny SVG line (and optional area) chart. No chart library: the data is a handful of points. */
export function Spark({ values, width = 120, height = 28, area = false, up = null }: Props) {
  if (values.length < 2) return <svg width={width} height={height} className="spark"><line x1="0" x2={width} y1={height / 2} y2={height / 2} className="spark-flat" /></svg>
  const min = Math.min(...values), max = Math.max(...values), span = max - min || 1
  const pts = values.map((v, i) => [(i / (values.length - 1)) * (width - 2) + 1, height - 3 - ((v - min) / span) * (height - 6)])
  const d = pts.map((p, i) => `${i ? 'L' : 'M'}${p[0].toFixed(1)},${p[1].toFixed(1)}`).join(' ')
  const dir = up ?? values[values.length - 1] >= values[0]
  return (
    <svg width={width} height={height} className={`spark ${dir ? 'up' : 'down'}`} role="img" aria-label="price trend">
      {area && <path d={`${d} L${width - 1},${height} L1,${height} Z`} className="spark-area" />}
      <path d={d} className="spark-line" />
    </svg>
  )
}
