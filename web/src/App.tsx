import { useCallback, useEffect, useState } from 'react'
import { call, get, hasCredentials, setUnauthorizedHandler, signOut } from './api'
import type { AuditLine, BoardRow, Me, Report, Rules, StatementLine } from './types'
import { money, pct, px, qty } from './format'
import { usePoll } from './hooks'
import { Login } from './components/Login'
import { PriceBoard } from './components/PriceBoard'
import { Trading } from './components/Trading'
import { Audit, Liquidity, Margin, Portfolio, Risk, Statement } from './components/Account'

type Page = 'prices' | 'trading' | 'portfolio' | 'margin' | 'statement' | 'liquidity' | 'risk' | 'audit'
interface Toast { id: number; kind: 'ok' | 'bad' | 'info'; text: string }

export default function App() {
  const [me, setMe] = useState<Me | null>(null), [checking, setChecking] = useState(hasCredentials())
  useEffect(() => { setUnauthorizedHandler(() => { signOut(); setMe(null) }) }, [])
  useEffect(() => { if (hasCredentials()) call<Me>('GET', '/api/v1/me').then(r => { if (r.status === 200) setMe(r.data); setChecking(false) }); else setChecking(false) }, [])
  if (checking) return null
  if (!me) return <Login onSignedIn={setMe} />
  return <Desk me={me} onSignOut={() => { signOut(); setMe(null) }} />
}

function Desk({ me, onSignOut }: { me: Me; onSignOut: () => void }) {
  const trades = me.roles.includes('TRADER') || me.roles.includes('LIQUIDITY_PROVIDER')
  const pages: [Page, string, boolean][] = [['prices', 'Prices', true], ['trading', 'Trading', trades], ['portfolio', 'Portfolio', trades], ['margin', 'Margin', trades], ['statement', 'Statement', trades],
    ['liquidity', 'Liquidity', me.roles.includes('LIQUIDITY_PROVIDER')], ['risk', 'Risk', me.roles.includes('RISK')], ['audit', 'Audit', me.roles.includes('ADMIN')]]
  const allowed = pages.filter(p => p[2])
  const [page, setPage] = useState<Page>('prices'), [selected, setSelected] = useState('SBNK'), [toasts, setToasts] = useState<Toast[]>([])
  const toast = useCallback((kind: Toast['kind'], text: string) => { const id = Date.now() + Math.random(); setToasts(t => [...t, { id, kind, text }]); setTimeout(() => setToasts(t => t.filter(x => x.id !== id)), 5000) }, [])

  const [board] = usePoll<BoardRow[]>(() => get('/api/v1/market/board'), [], 1000)
  const [rules] = usePoll<Rules>(() => get('/api/v1/market/rules'), [], 60000)
  const [account, refreshAccount] = usePoll<Report>(() => get('/api/v1/accounts/me'), [], 1000, trades)
  const [statement] = usePoll<StatementLine[]>(() => get('/api/v1/accounts/me/trades'), [], 1500, trades && page === 'statement')
  const [calls] = usePoll<Report[]>(() => get('/api/v1/risk/margin-calls'), [], 1000, me.roles.includes('RISK'))
  const [audit] = usePoll<AuditLine[]>(() => get('/api/v1/admin/audit'), [], 1500, me.roles.includes('ADMIN') && page === 'audit')
  const rows = board ?? []
  const tot = rows.reduce((a, r) => ({ trades: a.trades + r.trades, shares: a.shares + r.shares, value: a.value + r.valueMills }), { trades: 0, shares: 0, value: 0 })
  const pick = (s: string) => { setSelected(s); if (trades) setPage('trading') }

  return (
    <div className="app">
      <header className="top">
        <div className="brand"><span className="logo">BD</span> Broker Order Desk</div>
        <nav>{allowed.map(([k, label]) => <button key={k} data-testid={`nav-${k}`} className={page === k ? 'on' : ''} onClick={() => setPage(k)}>{label}</button>)}</nav>
        <div className="who" data-testid="who"><span className="dot" />{me.username}<small>{me.roles.join(', ')}</small><button className="link light" data-testid="signout" onClick={onSignOut}>Sign out</button></div>
      </header>
      <div className="strip">
        <span className="sess">Simulated session</span>
        <Metric label="Trades" value={qty(tot.trades)} /><Metric label="Shares" value={qty(tot.shares)} /><Metric label="Riyal" value={money(tot.value)} />
        {trades && account && <><span className="sep" /><Metric label="Balance" value={money(account.cashMills)} tone={account.cashMills < 0 ? 'down' : ''} testid="strip-balance" /><Metric label="Portfolio value" value={money(account.marketValueMills)} />
          <Metric label="Holdings" value={String(account.positions.length)} /><Metric label="Buy orders value" value={money(account.reservedMills)} />
          {account.loanMills > 0 && <span className={`flag ${account.marginCall ? 'bad' : 'ok'}`} data-testid="margin-flag">{account.marginCall ? 'MARGIN CALL' : 'Margin OK'} {pct(account.equityBps)}</span>}</>}
      </div>
      <div className="ticker" aria-hidden><div className="ticker-in">{[...rows, ...rows].map((r, i) => <span key={i}><b>{r.symbol}</b> {px(r.lastPriceMills)} <em className={r.changeBps >= 0 ? 'up' : 'down'}>{pct(r.changeBps)}</em></span>)}</div></div>
      <main>
        {page === 'prices' && <PriceBoard rows={rows} selected={selected} onPick={pick} />}
        {page === 'trading' && <Trading rows={rows} selected={selected} onPick={setSelected} account={account} rules={rules} toast={toast} refreshAccount={refreshAccount} />}
        {page === 'portfolio' && <Portfolio account={account} />}
        {page === 'margin' && <Margin account={account} />}
        {page === 'statement' && <Statement lines={statement} />}
        {page === 'liquidity' && <Liquidity rows={rows} selected={selected} toast={toast} />}
        {page === 'risk' && <Risk calls={calls} />}
        {page === 'audit' && <Audit lines={audit} />}
      </main>
      <div className="toasts" aria-live="polite">{toasts.map(t => <div key={t.id} className={`toast ${t.kind}`} data-testid="toast">{t.text}</div>)}</div>
    </div>
  )
}

const Metric = ({ label, value, tone, testid }: { label: string; value: string; tone?: string; testid?: string }) => <span className="metric"><small>{label}</small><b className={tone} data-testid={testid}>{value}</b></span>
