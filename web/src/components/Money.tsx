import { useState } from 'react'
import { call, get } from '../api'
import type { Alert, Bank, BoardRow, Member, Print, QuoteResult, OrderView, Wallet } from '../types'
import { millsFromInput, money, px, qty, reason, timeOf } from '../format'
import { useI18n } from '../i18n'
import { usePoll } from '../hooks'

type Toast = (kind: 'ok' | 'bad' | 'info', text: string) => void
let seq = 0

export function WalletPage({ toast, refreshAccount }: { toast: Toast; refreshAccount: () => void }) {
  const { t } = useI18n()
  const [wallet, reload] = usePoll<Wallet>(() => get('/api/v1/wallet'), [], 3000)
  const [banks] = usePoll<Bank[]>(() => get('/api/v1/wallet/banks'), [], 600000)
  const [dir, setDir] = useState<'DEPOSIT' | 'WITHDRAW'>('DEPOSIT'), [amount, setAmount] = useState('10000'), [bank, setBank] = useState('Demo Bank A')
  const mills = millsFromInput(amount || '0'), selected = banks?.find(b => b.name === bank)
  const submit = async () => {
    const r = await call<{ detail?: string }>('POST', '/api/v1/wallet/transfers', { clientRef: `ui-${Date.now()}-${++seq}`, direction: dir, amountMills: mills, bank })
    if (r.status === 201) toast('ok', `${dir === 'DEPOSIT' ? 'Deposited' : 'Withdrew'} ${money(mills)} via ${selected?.rail.replace('_', '+')}`)
    else toast('bad', (r.data as { detail?: string })?.detail ?? `Request failed (${r.status})`)
    reload(); refreshAccount()
  }
  return (
    <div className="grid2">
      <section className="card form" data-testid="wallet-form"><h2>{t('Wallet')}</h2>
        <div className="tiles small3"><div className="tile"><div className="muted small">{t('Cash balance')}</div><div className={`tile-v ${(wallet?.cashMills ?? 0) < 0 ? 'down' : ''}`} data-testid="wallet-cash">{wallet ? money(wallet.cashMills) : '-'}</div></div>
          <div className="tile"><div className="muted small">{t('Buy orders value')}</div><div className="tile-v">{wallet ? money(wallet.reservedMills) : '-'}</div></div><div className="tile"><div className="muted small">{t('Withdrawable')}</div><div className="tile-v">{wallet ? money(wallet.withdrawableMills) : '-'}</div></div></div>
        <div className="seg"><button className={dir === 'DEPOSIT' ? 'on buy' : ''} data-testid="wallet-deposit" onClick={() => setDir('DEPOSIT')}>{t('Deposit')}</button><button className={dir === 'WITHDRAW' ? 'on sell' : ''} data-testid="wallet-withdraw" onClick={() => setDir('WITHDRAW')}>{t('Withdraw')}</button></div>
        <label>{t('Amount (QAR)')}<input data-testid="wallet-amount" inputMode="decimal" value={amount} onChange={e => setAmount(e.target.value)} /></label>
        <label>{t('Bank')}<select data-testid="wallet-bank" value={bank} onChange={e => setBank(e.target.value)}>{(banks ?? []).map(b => <option key={b.name} value={b.name}>{b.name} · {b.rail.replace('_', '+')}</option>)}</select></label>
        <button className={`primary ${dir === 'DEPOSIT' ? 'buy' : 'sell'}`} data-testid="wallet-submit" disabled={mills <= 0} onClick={() => void submit()}>{dir === 'DEPOSIT' ? t('Deposit') : t('Withdraw')}</button>
        <div className="muted small">A withdrawal can never reach into the margin loan or into cash reserved by open buy orders. Simulated rails, synthetic banks.</div>
      </section>
      <section className="card"><h2>{t('Transfers')}</h2><table data-testid="transfers"><thead><tr><th>{t('Time')}</th><th>{t('Side')}</th><th className="r">{t('Amount (QAR)')}</th><th>{t('Bank')}</th><th>{t('Status')}</th></tr></thead>
        <tbody>{(wallet?.transfers ?? []).map(x => <tr key={x.id}><td>{timeOf(x.createdAt.replace(' ', 'T'))}</td><td className={x.direction === 'DEPOSIT' ? 'up' : 'down'}>{x.direction}</td><td className="r num">{money(x.amountMills)}</td><td>{x.bank} <span className="muted small">{x.rail.replace('_', '+')}</span></td><td><span className="pill FILLED">{x.status}</span></td></tr>)}
          {!wallet?.transfers.length && <tr><td colSpan={5} className="empty">—</td></tr>}</tbody></table></section>
    </div>
  )
}

export function AlertsPage({ rows, toast }: { rows: BoardRow[]; toast: Toast }) {
  const { t } = useI18n()
  const [alerts, reload] = usePoll<Alert[]>(() => get('/api/v1/alerts'), [], 3000)
  const [symbol, setSymbol] = useState('SBNK'), [direction, setDirection] = useState<'ABOVE' | 'BELOW'>('ABOVE'), [price, setPrice] = useState('')
  const row = rows.find(r => r.symbol === symbol)
  const create = async () => {
    const r = await call<{ detail?: string }>('POST', '/api/v1/alerts', { symbol, direction, priceMills: millsFromInput(price || '0') })
    toast(r.status === 201 ? 'ok' : 'bad', r.status === 201 ? `Alert set: ${symbol} ${direction.toLowerCase()} ${price}` : (r.data as { detail?: string })?.detail ?? 'Could not create the alert')
    reload()
  }
  const remove = async (id: number) => { await call('DELETE', `/api/v1/alerts/${id}`); reload() }
  return (
    <div className="grid2">
      <section className="card form" data-testid="alert-form"><h2>{t('Create alert')}</h2>
        <label>{t('Symbol')}<select data-testid="alert-symbol" value={symbol} onChange={e => setSymbol(e.target.value)}>{rows.map(r => <option key={r.symbol}>{r.symbol}</option>)}</select></label>
        <div className="seg"><button className={direction === 'ABOVE' ? 'on buy' : ''} data-testid="alert-above" onClick={() => setDirection('ABOVE')}>{t('Above')}</button><button className={direction === 'BELOW' ? 'on sell' : ''} data-testid="alert-below" onClick={() => setDirection('BELOW')}>{t('Below')}</button></div>
        <label>{t('Price')} (QAR)<input data-testid="alert-price" inputMode="decimal" value={price} placeholder={row ? px(row.lastPriceMills) : ''} onChange={e => setPrice(e.target.value)} /></label>
        <button className="primary" data-testid="alert-submit" disabled={!price} onClick={() => void create()}>{t('Create alert')}</button>
        <div className="muted small">Fires once, the moment a trade prints at or through your price. You also get a pop-up if you are online.</div>
      </section>
      <section className="card"><h2>{t('Alerts')}</h2><table data-testid="alerts"><thead><tr><th>{t('Symbol')}</th><th></th><th className="r">{t('Price')}</th><th>{t('Status')}</th><th></th></tr></thead>
        <tbody>{(alerts ?? []).map(a => <tr key={a.id}><td><b>{a.symbol}</b></td><td>{a.direction === 'ABOVE' ? t('Above') : t('Below')}</td><td className="r num">{px(a.priceMills)}</td>
          <td><span className={`pill ${a.status === 'TRIGGERED' ? 'FILLED' : 'NEW'}`}>{a.status === 'TRIGGERED' ? t('Triggered') : t('Active')}</span> {a.triggeredPriceMills && <span className="muted small">@ {px(a.triggeredPriceMills)}</span>}</td>
          <td>{a.status === 'ACTIVE' && <button className="link" onClick={() => void remove(a.id)}>{t('Cancel')}</button>}</td></tr>)}{!alerts?.length && <tr><td colSpan={5} className="empty">—</td></tr>}</tbody></table></section>
    </div>
  )
}

const KIND_COLOR: Record<Print['kind'], string> = { LP_BUY: '#16217a', LP_SELL: '#b3261e', MARKET_BUY: '#b08d2c', MARKET_SELL: '#f2cf3c' }
const KIND_LABEL: Record<Print['kind'], string> = { LP_BUY: 'Liquidity provider buy', LP_SELL: 'Liquidity provider sell', MARKET_BUY: 'Market buy', MARKET_SELL: 'Market sell' }

export function LiquidityPage({ rows, canQuote, toast, selected }: { rows: BoardRow[]; canQuote: boolean; toast: Toast; selected: string }) {
  const { t } = useI18n()
  const [sym, setSym] = useState(selected), [bid, setBid] = useState(''), [ask, setAsk] = useState(''), [quantity, setQuantity] = useState('1000'), [last, setLast] = useState<QuoteResult | null>(null)
  const [members] = usePoll<Member[]>(() => get('/api/v1/liquidity/members'), [], 5000)
  const [prints] = usePoll<Print[]>(() => get(`/api/v1/liquidity/${sym}/activity`), [sym], 3000)
  const row = rows.find(r => r.symbol === sym)
  const b = millsFromInput(bid || '0'), a = millsFromInput(ask || '0'), mid = row?.lastPriceMills ?? 0
  const spreadBps = a > 0 && b > 0 ? Math.round(((a - b) * 10000) / ((a + b) / 2)) : 0
  const send = async () => {
    const r = await call<QuoteResult>('POST', '/api/v1/quotes', { quoteId: `q${Date.now()}-${++seq}`, symbol: sym, quantity: parseInt(quantity, 10), bidPriceMills: b, askPriceMills: a })
    if (r.status === 200) { setLast(r.data); toast('ok', `Quoted ${sym}: bid ${px(b)} / offer ${px(a)}`) } else toast('bad', 'Quote rejected: the bid must be below the offer and inside the daily band')
  }
  const pts = prints ?? [], W = 900, H = 240
  const lo = Math.min(...pts.map(p => p.priceMills), Infinity), hi = Math.max(...pts.map(p => p.priceMills), -Infinity), span = hi - lo || 1
  return (
    <div className="stack">
      <div className="grid2">
        {canQuote && <section className="card form" data-testid="quote-form"><h2>{t('Quote')} <span className="muted">{sym}</span></h2>
          <label>{t('Symbol')}<select data-testid="q-symbol" value={sym} onChange={e => setSym(e.target.value)}>{rows.map(r => <option key={r.symbol}>{r.symbol}</option>)}</select></label>
          <label>{t('Shares')}<input data-testid="q-qty" value={quantity} onChange={e => setQuantity(e.target.value.replace(/\D/g, ''))} /></label>
          <label>{t('Bid')} (QAR)<input data-testid="q-bid" value={bid} placeholder={px(mid - 100)} onChange={e => setBid(e.target.value)} /></label>
          <label>{t('Offer')} (QAR)<input data-testid="q-ask" value={ask} placeholder={px(mid + 100)} onChange={e => setAsk(e.target.value)} /></label>
          <div className="est"><span className="muted">Spread</span><b>{spreadBps ? `${spreadBps} bps` : '-'}</b></div>
          <button className="primary" data-testid="q-submit" disabled={!b || !a || a <= b} onClick={() => void send()}>{t('Quote')}</button>
          {last && <div className="muted small">Last: bid {last.bid.status}, offer {last.ask.status} {reason(last.bid.rejectReason || last.ask.rejectReason)}</div>}
        </section>}
        <section className="card"><h2>{t('Members')}</h2><table data-testid="members"><thead><tr><th>{t('Account')}</th><th className="r">Symbols</th><th className="r">{t('Orders')}</th><th className="r">Filled</th><th className="r">{t('Trades')}</th></tr></thead>
          <tbody>{(members ?? []).map(m => <tr key={m.owner}><td><b>{m.owner}</b></td><td className="r num">{m.quotedSymbols}</td><td className="r num">{m.orders}</td><td className="r num">{qty(m.filledShares)}</td><td className="r num">{m.trades}</td></tr>)}</tbody></table></section>
      </div>
      <section className="card"><div className="card-head"><h2>Liquidity provider activity</h2><select data-testid="lp-symbol" value={sym} onChange={e => setSym(e.target.value)}>{rows.map(r => <option key={r.symbol}>{r.symbol}</option>)}</select></div>
        <div className="legend">{(Object.keys(KIND_COLOR) as Print['kind'][]).map(k => <span key={k}><i style={{ background: KIND_COLOR[k] }} />{KIND_LABEL[k]}</span>)}</div>
        <svg viewBox={`0 0 ${W} ${H}`} className="scatter" data-testid="scatter" role="img" aria-label="liquidity activity">
          {pts.length === 0 && <text x={W / 2} y={H / 2} textAnchor="middle" className="axis">No trades yet</text>}
          {pts.map((p, i) => <circle key={i} cx={20 + (pts.length - 1 - i) * ((W - 40) / Math.max(1, pts.length - 1))} cy={14 + (H - 28) - ((p.priceMills - lo) / span) * (H - 28)} r={3 + Math.min(4, p.quantity / 800)} fill={KIND_COLOR[p.kind]} opacity={0.85}><title>{`${p.kind} ${qty(p.quantity)} @ ${px(p.priceMills)}`}</title></circle>)}
        </svg>
      </section>
    </div>
  )
}

export const _o: OrderView | null = null
