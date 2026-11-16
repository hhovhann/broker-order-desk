import type { AuditLine, Report, StatementLine } from '../types'
import { money, pct, px, qty, timeOf } from '../format'
import { Gauge } from './Gauge'
import { useState } from 'react'

export function Portfolio({ account }: { account: Report | null }) {
  if (!account) return <div className="card empty">Loading…</div>
  const total = Math.max(1, account.marketValueMills)
  return (
    <div className="stack">
      <div className="tiles">
        <Tile label="Cash balance" value={money(account.cashMills)} tone={account.cashMills < 0 ? 'down' : ''} note={account.cashMills < 0 ? 'negative cash is a margin loan' : ''} />
        <Tile label="Portfolio value" value={money(account.marketValueMills)} note="at last traded price" />
        <Tile label="Equity" value={money(account.equityMills)} />
        <Tile label="Buy orders value" value={money(account.reservedMills)} note="reserved by open buy orders" />
        <Tile label="Buying power" value={money(account.buyingPowerMills)} note="cash plus margin line, minus reserved" />
      </div>
      <section className="card"><h2>Holdings</h2>
        <table data-testid="portfolio"><thead><tr><th>Symbol</th><th className="r">Shares</th><th className="r">Last</th><th className="r">Value</th><th>Weight</th></tr></thead>
          <tbody>{account.positions.map(p => <tr key={p.symbol}><td><b>{p.symbol}</b></td><td className="r num">{qty(p.quantity)}</td><td className="r num">{px(p.lastPriceMills)}</td><td className="r num">{money(p.valueMills)}</td><td><div className="weight"><i style={{ width: `${(p.valueMills / total) * 100}%` }} /></div></td></tr>)}
            {!account.positions.length && <tr><td colSpan={5} className="empty">No holdings</td></tr>}</tbody></table>
      </section>
    </div>
  )
}

const Tile = ({ label, value, note, tone }: { label: string; value: string; note?: string; tone?: string }) => <div className="card tile"><div className="muted small">{label}</div><div className={`tile-v ${tone ?? ''}`}>{value}</div><div className="small muted">{note}</div></div>

export function Margin({ account }: { account: Report | null }) {
  if (!account) return <div className="card empty">Loading…</div>
  const hasLoan = account.loanMills > 0
  return (
    <div className="stack">
      {account.marginCall && <div className="banner bad" data-testid="margin-call">MARGIN CALL: equity is {pct(account.equityBps)} of the position, below the {pct(account.maintenanceBps)} maintenance level. Add cash or reduce the position.</div>}
      {!account.marginCall && hasLoan && <div className="banner ok">Margin account healthy: equity {pct(account.equityBps)}, maintenance {pct(account.maintenanceBps)}.</div>}
      <div className="grid2">
        <section className="card center-card"><h2>Equity against maintenance</h2><Gauge valueBps={account.equityBps} maintenanceBps={account.maintenanceBps} />
          <div className="muted small">Needle: equity ÷ market value. Red zone: below the maintenance line.</div></section>
        <section className="card"><h2>Margin position</h2>
          <dl className="kv"><dt>Margin loan</dt><dd>{money(account.loanMills)}</dd><dt>Market value</dt><dd>{money(account.marketValueMills)}</dd><dt>Equity</dt><dd>{money(account.equityMills)}</dd>
            <dt>Buying power</dt><dd>{money(account.buyingPowerMills)}</dd><dt>Maintenance</dt><dd>{pct(account.maintenanceBps)}</dd></dl>
        </section>
      </div>
    </div>
  )
}

export function Statement({ lines }: { lines: StatementLine[] | null }) {
  return (
    <section className="card"><h2>Trade statement</h2>
      <table data-testid="statement"><thead><tr><th>Time</th><th>Symbol</th><th>Side</th><th className="r">Shares</th><th className="r">Price</th><th className="r">Value</th></tr></thead>
        <tbody>{(lines ?? []).map(l => <tr key={l.tradeId}><td>{timeOf(l.executedAt)}</td><td><b>{l.symbol}</b></td><td className={l.side === 'BUY' ? 'up' : 'down'}>{l.side}</td><td className="r num">{qty(l.quantity)}</td><td className="r num">{px(l.priceMills)}</td><td className="r num">{money(l.valueMills)}</td></tr>)}
          {!lines?.length && <tr><td colSpan={6} className="empty">No executions yet</td></tr>}</tbody></table>
    </section>
  )
}

export function Risk({ calls }: { calls: Report[] | null }) {
  return (
    <section className="card"><h2>Margin calls</h2>
      <table data-testid="calls"><thead><tr><th>Account</th><th className="r">Loan</th><th className="r">Market value</th><th className="r">Equity</th><th className="r">Equity %</th><th className="r">Maintenance</th></tr></thead>
        <tbody>{(calls ?? []).map(c => <tr key={c.accountId}><td><b>{c.owner}</b></td><td className="r num">{money(c.loanMills)}</td><td className="r num">{money(c.marketValueMills)}</td><td className="r num">{money(c.equityMills)}</td><td className="r num down">{pct(c.equityBps)}</td><td className="r num">{pct(c.maintenanceBps)}</td></tr>)}
          {calls && !calls.length && <tr><td colSpan={6} className="empty ok">No account is in margin call</td></tr>}</tbody></table>
    </section>
  )
}

export function Audit({ lines }: { lines: AuditLine[] | null }) {
  const [q, setQ] = useState('')
  const view = (lines ?? []).filter(l => (l.actor + l.action + l.detail).toLowerCase().includes(q.toLowerCase()))
  return (
    <section className="card"><div className="card-head"><h2>Audit trail</h2><input className="search" placeholder="Filter actor, action, detail" value={q} onChange={e => setQ(e.target.value)} /></div>
      <div className="scroll tall"><table data-testid="audit"><thead><tr><th>Time</th><th>Actor</th><th>Action</th><th>Detail</th><th>Correlation id</th></tr></thead>
        <tbody>{view.map((l, i) => <tr key={i}><td>{timeOf(l.at)}</td><td>{l.actor}</td><td><span className={`pill A-${l.action}`}>{l.action}</span></td><td>{l.detail}</td><td className="muted small">{l.correlationId?.slice(0, 8)}</td></tr>)}
          {!view.length && <tr><td colSpan={5} className="empty">Nothing to show</td></tr>}</tbody></table></div>
    </section>
  )
}
