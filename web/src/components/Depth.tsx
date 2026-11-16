import type { Depth as DepthT } from '../types'
import { px, qty } from '../format'

/** Order book depth: offers above, bids below, volume bars behind the numbers, order count in the first column. */
export function Depth({ depth }: { depth: DepthT | null }) {
  const asks = [...(depth?.asks ?? [])].reverse(), bids = depth?.bids ?? []
  const max = Math.max(1, ...asks.map(l => l.quantity), ...bids.map(l => l.quantity))
  const spread = depth && depth.asks[0] && depth.bids[0] ? depth.asks[0].priceMills - depth.bids[0].priceMills : null
  const row = (l: { priceMills: number; quantity: number; orders: number }, kind: 'ask' | 'bid') => (
    <tr key={kind + l.priceMills} className={`depth-row ${kind}`} data-testid={`depth-${kind}`}>
      <td className="c">{l.orders}</td>
      <td className="bar"><i style={{ width: `${(l.quantity / max) * 100}%` }} /><span>{qty(l.quantity)}</span></td>
      <td className="r price">{px(l.priceMills)}</td>
    </tr>
  )
  return (
    <table className="depth" data-testid="depth">
      <thead><tr><th className="c">#</th><th>Shares</th><th className="r">Price</th></tr></thead>
      <tbody>
        {asks.map(l => row(l, 'ask'))}
        <tr className="spread"><td colSpan={3}>{spread === null ? 'no two-sided market' : `spread ${px(spread)}`}</td></tr>
        {bids.map(l => row(l, 'bid'))}
        {!asks.length && !bids.length && <tr><td colSpan={3} className="empty">The book is empty</td></tr>}
      </tbody>
    </table>
  )
}
