import { useMemo, useState } from 'react'
import type { BoardRow } from '../types'
import { money, pct, px, qty } from '../format'
import { useFlash } from '../hooks'
import { Spark } from './Spark'

type SortKey = 'symbol' | 'changeBps' | 'valueMills' | 'trades'

function Last({ v, cls }: { v: number; cls: string }) { const f = useFlash(v); return <td className={`r num ${cls} ${f ? 'flash-' + f : ''}`}>{px(v)}</td> }

export function PriceBoard({ rows, selected, onPick }: { rows: BoardRow[]; selected: string; onPick: (s: string) => void }) {
  const [key, setKey] = useState<SortKey>('symbol'), [asc, setAsc] = useState(true), [filter, setFilter] = useState('')
  const view = useMemo(() => {
    const f = rows.filter(r => (r.symbol + r.name).toLowerCase().includes(filter.toLowerCase()))
    return [...f].sort((a, b) => (key === 'symbol' ? a.symbol.localeCompare(b.symbol) : a[key] - b[key]) * (asc ? 1 : -1))
  }, [rows, key, asc, filter])
  const th = (k: SortKey, label: string, cls = '') => <th className={`${cls} sortable`} onClick={() => { if (k === key) setAsc(!asc); else { setKey(k); setAsc(true) } }}>{label}{k === key ? (asc ? ' ▲' : ' ▼') : ''}</th>
  const heat = (bps: number) => (bps === 0 ? '' : bps > 0 ? `up h${Math.min(4, Math.ceil(bps / 100))}` : `down h${Math.min(4, Math.ceil(-bps / 100))}`)
  return (
    <section className="card">
      <div className="card-head"><h2>Prices</h2><input className="search" placeholder="Filter symbol or name" value={filter} onChange={e => setFilter(e.target.value)} /></div>
      <div className="scroll">
        <table className="board" data-testid="board">
          <thead><tr>
            {th('symbol', 'Symbol')}<th>Company</th><th className="r offer">Offer shares</th><th className="r offer">Offer</th><th className="r">Last</th><th className="r bidh">Bid</th><th className="r bidh">Bid shares</th>
            {th('trades', 'Trades', 'r')}<th className="r">Average</th><th className="r">Shares</th>{th('valueMills', 'Riyal', 'r')}<th className="r">Prev close</th><th className="r">High</th><th className="r">Low</th><th>Trend</th><th className="r">Change</th>{th('changeBps', 'Change %', 'r')}
          </tr></thead>
          <tbody>
            {view.map(r => (
              <tr key={r.symbol} className={r.symbol === selected ? 'sel' : ''} onClick={() => onPick(r.symbol)} data-testid={`row-${r.symbol}`}>
                <td><b>{r.symbol}</b></td><td>{r.name}</td>
                <td className="r num offer">{r.offerShares ? qty(r.offerShares) : ''}</td><td className="r num offer">{r.offerPriceMills ? px(r.offerPriceMills) : ''}</td>
                <Last v={r.lastPriceMills} cls={heat(r.changeBps)} />
                <td className="r num bidh">{r.bidPriceMills ? px(r.bidPriceMills) : ''}</td><td className="r num bidh">{r.bidShares ? qty(r.bidShares) : ''}</td>
                <td className="r num">{qty(r.trades)}</td><td className="r num">{r.averageMills ? px(r.averageMills) : ''}</td><td className="r num">{qty(r.shares)}</td><td className="r num">{money(r.valueMills)}</td>
                <td className="r num">{px(r.prevCloseMills)}</td><td className="r num">{r.highMills ? px(r.highMills) : ''}</td><td className="r num">{r.lowMills ? px(r.lowMills) : ''}</td>
                <td><Spark values={r.spark} width={96} height={24} up={r.changeBps >= 0} /></td>
                <td className={`r num ${heat(r.changeBps)}`}>{px(r.changeMills)}</td><td className={`r num ${heat(r.changeBps)}`}>{pct(r.changeBps)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  )
}
