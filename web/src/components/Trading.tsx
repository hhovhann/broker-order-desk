import { useEffect, useState } from 'react'
import { call, get } from '../api'
import type { BoardRow, Depth as DepthT, OrderView, Report, Rules, TradePrint } from '../types'
import { millsFromInput, money, pct, px, qty, reason, signed } from '../format'
import { usePoll } from '../hooks'
import { Depth } from './Depth'
import { Spark } from './Spark'

interface Props { rows: BoardRow[]; selected: string; onPick: (s: string) => void; account: Report | null; rules: Rules | null; toast: (kind: 'ok' | 'bad' | 'info', text: string) => void; refreshAccount: () => void }

let seq = 0

export function Trading({ rows, selected, onPick, account, rules, toast, refreshAccount }: Props) {
  const row = rows.find(r => r.symbol === selected) ?? rows[0]
  const [side, setSide] = useState<'BUY' | 'SELL'>('BUY'), [quantity, setQuantity] = useState('1000'), [price, setPrice] = useState('')
  const [busy, setBusy] = useState(false), [tab, setTab] = useState<'orders' | 'tape'>('orders')
  useEffect(() => { if (row) setPrice(px(row.offerPriceMills || row.lastPriceMills)) }, [row?.symbol]) // eslint-disable-line react-hooks/exhaustive-deps
  const [depth] = usePoll<DepthT>(() => get(`/api/v1/market/${selected}/book`), [selected], 800)
  const [tape] = usePoll<TradePrint[]>(() => get(`/api/v1/market/${selected}/trades`), [selected], 1000)
  const [orders, reloadOrders] = usePoll<OrderView[]>(() => get('/api/v1/orders'), [], 1000)
  if (!row) return null

  const q = parseInt(quantity || '0', 10), p = millsFromInput(price || '0'), notional = Number.isFinite(q * p) ? q * p : 0
  const held = account?.positions.find(x => x.symbol === selected)?.quantity ?? 0
  const low = rules ? Math.floor(row.prevCloseMills * (100 - rules.bandPercent) / 100) : 0, high = rules ? Math.floor(row.prevCloseMills * (100 + rules.bandPercent) / 100) : 0
  const hints: string[] = []
  if (rules && p > 0 && (p < low || p > high)) hints.push(`Price is outside the daily band ${px(low)} to ${px(high)}`)
  if (side === 'BUY' && account && notional > account.buyingPowerMills) hints.push(`Order value ${money(notional)} is above buying power ${money(account.buyingPowerMills)}`)
  if (side === 'SELL' && q > held) hints.push(`You hold ${qty(held)} ${selected}`)

  const submit = async () => {
    setBusy(true)
    const r = await call<OrderView>('POST', '/api/v1/orders', { clientOrderId: `ui-${Date.now()}-${++seq}`, symbol: selected, side, quantity: q, limitPriceMills: p })
    setBusy(false)
    if (r.status === 201) {
      const o = r.data
      if (o.status === 'REJECTED') toast('bad', `Rejected: ${reason(o.rejectReason)}`)
      else if (o.status === 'FILLED') toast('ok', `${o.side} ${qty(o.quantity)} ${o.symbol} filled at ${px(o.avgPriceMills)}`)
      else if (o.status === 'PARTIALLY_FILLED') toast('ok', `${qty(o.filledQty)} of ${qty(o.quantity)} filled, the rest rests in the book`)
      else toast('info', `${o.side} ${qty(o.quantity)} ${o.symbol} @ ${px(o.limitPriceMills)} is resting in the book`)
    } else toast('bad', `Request failed (${r.status})`)
    reloadOrders(); refreshAccount()
  }
  const cancel = async (id: number) => {
    const r = await call<OrderView>('DELETE', `/api/v1/orders/${id}`)
    toast(r.status === 200 ? 'info' : 'bad', r.status === 200 ? `Order ${id} cancelled, reservation released` : 'Could not cancel: the order is no longer resting')
    reloadOrders(); refreshAccount()
  }

  return (
    <div className="trading">
      <aside className="card symbols">
        <h2>Symbols</h2>
        {rows.map(r => <button key={r.symbol} className={`sym ${r.symbol === selected ? 'on' : ''}`} onClick={() => onPick(r.symbol)}><b>{r.symbol}</b><span>{px(r.lastPriceMills)}</span><em className={r.changeBps >= 0 ? 'up' : 'down'}>{pct(r.changeBps)}</em></button>)}
      </aside>
      <div className="center">
        <section className="card hero" data-testid="hero">
          <div><div className="hero-sym">{row.symbol}</div><div className="muted">{row.name}</div></div>
          <Stat label="Last" value={px(row.lastPriceMills)} sub={`${signed(px(row.changeMills))} (${pct(row.changeBps)})`} tone={row.changeBps >= 0 ? 'up' : 'down'} />
          <Stat label="Offer" value={row.offerPriceMills ? px(row.offerPriceMills) : '-'} sub={row.offerShares ? qty(row.offerShares) : ''} />
          <Stat label="Bid" value={row.bidPriceMills ? px(row.bidPriceMills) : '-'} sub={row.bidShares ? qty(row.bidShares) : ''} />
          <Stat label="Shares" value={qty(row.shares)} sub={`${qty(row.trades)} trades`} />
          <Stat label="Riyal" value={money(row.valueMills)} sub={row.highMills ? `H ${px(row.highMills)} · L ${px(row.lowMills)}` : ''} />
          <div className="hero-chart"><Spark values={row.spark} width={220} height={56} area up={row.changeBps >= 0} /></div>
        </section>
        <section className="card ticket" data-testid="ticket">
          <div className="seg">
            <button className={side === 'BUY' ? 'on buy' : ''} data-testid="side-buy" onClick={() => setSide('BUY')}>Buy</button>
            <button className={side === 'SELL' ? 'on sell' : ''} data-testid="side-sell" onClick={() => setSide('SELL')}>Sell</button>
          </div>
          <label>Shares<input data-testid="qty" inputMode="numeric" value={quantity} onChange={e => setQuantity(e.target.value.replace(/\D/g, ''))} /></label>
          <label>Limit price (QAR)<input data-testid="price" inputMode="decimal" value={price} onChange={e => setPrice(e.target.value)} /></label>
          <div className="est"><span className="muted">Order value</span><b>{money(notional)}</b></div>
          <button className={`primary ${side === 'BUY' ? 'buy' : 'sell'}`} data-testid="submit-order" disabled={busy || q <= 0 || p <= 0} onClick={() => void submit()}>{side === 'BUY' ? 'Buy' : 'Sell'} {selected}</button>
          <div className="hints">{hints.map(h => <div key={h} className="hint">⚠ {h}</div>)}{!hints.length && <div className="hint ok">Pre-trade checks look fine. The server decides.</div>}</div>
          {rules && <div className="muted small">Daily band ±{rules.bandPercent}% around {px(row.prevCloseMills)}. Buying power {account ? money(account.buyingPowerMills) : '-'}.</div>}
        </section>
        <section className="card">
          <div className="tabs"><button className={tab === 'orders' ? 'on' : ''} onClick={() => setTab('orders')}>Orders</button><button className={tab === 'tape' ? 'on' : ''} onClick={() => setTab('tape')}>Trades</button></div>
          {tab === 'orders' ? (
            <div className="scroll short"><table data-testid="orders"><thead><tr><th>Id</th><th>Symbol</th><th>Side</th><th className="r">Qty</th><th className="r">Price</th><th className="r">Filled</th><th className="r">Avg</th><th>Status</th><th></th></tr></thead>
              <tbody>{(orders ?? []).map(o => (
                <tr key={o.orderId}><td>{o.orderId}</td><td>{o.symbol}</td><td className={o.side === 'BUY' ? 'up' : 'down'}>{o.side}</td><td className="r num">{qty(o.quantity)}</td><td className="r num">{px(o.limitPriceMills)}</td><td className="r num">{qty(o.filledQty)}</td><td className="r num">{o.avgPriceMills ? px(o.avgPriceMills) : ''}</td>
                  <td><span className={`pill ${o.status}`}>{o.status.replace('_', ' ')}</span>{o.rejectReason && <span className="why"> {reason(o.rejectReason)}</span>}</td>
                  <td>{(o.status === 'NEW' || o.status === 'PARTIALLY_FILLED') && <button className="link" data-testid={`cancel-${o.orderId}`} onClick={() => void cancel(o.orderId)}>Cancel</button>}</td></tr>))}
                {!orders?.length && <tr><td colSpan={9} className="empty">No orders yet</td></tr>}</tbody></table></div>
          ) : (
            <div className="scroll short"><table><thead><tr><th>Time</th><th className="r">Price</th><th className="r">Shares</th></tr></thead>
              <tbody>{(tape ?? []).map((t, i) => <tr key={i}><td>{t.executedAt.slice(11, 19)}</td><td className="r num">{px(t.priceMills)}</td><td className="r num">{qty(t.quantity)}</td></tr>)}{!tape?.length && <tr><td colSpan={3} className="empty">No trades yet</td></tr>}</tbody></table></div>
          )}
        </section>
      </div>
      <aside className="card depthcard"><h2>Depth <span className="muted">{selected}</span></h2><Depth depth={depth} /></aside>
    </div>
  )
}

function Stat({ label, value, sub, tone }: { label: string; value: string; sub?: string; tone?: 'up' | 'down' }) {
  return <div className="stat"><div className="muted small">{label}</div><div className={`stat-v ${tone ?? ''}`}>{value}</div><div className="small muted">{sub}</div></div>
}
