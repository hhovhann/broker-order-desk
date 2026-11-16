import { useCallback, useEffect, useState } from 'react'
import { call, get, hasCredentials, setUnauthorizedHandler, signOut } from './api'
import type { ApplicationRow, AuditLine, BoardRow, Me, Report, Rules, StatementLine, Summary, Candle } from './types'
import { money, pct, px, qty } from './format'
import { usePoll } from './hooks'
import { I18nProvider, useI18n } from './i18n'
import { LiveProvider } from './live'
import { Login } from './components/Login'
import { PriceBoard } from './components/PriceBoard'
import { Trading } from './components/Trading'
import { Audit, Margin, Portfolio, Risk, Statement } from './components/Account'
import { AnalyticsPage, ChartsPage, NewsPage } from './components/Markets'
import { AlertsPage, LiquidityPage, WalletPage } from './components/Money'
import { ServicesPage } from './components/Services'
import { Spark } from './components/Spark'

type Page = 'prices' | 'trading' | 'charts' | 'analytics' | 'news' | 'portfolio' | 'margin' | 'statement' | 'wallet' | 'alerts' | 'liquidity' | 'services' | 'risk' | 'audit'
interface Toast { id: number; kind: 'ok' | 'bad' | 'info'; text: string }

export default function App() {
  return <I18nProvider><Root /></I18nProvider>
}

function Root() {
  const [me, setMe] = useState<Me | null>(null), [checking, setChecking] = useState(hasCredentials())
  useEffect(() => { setUnauthorizedHandler(() => { signOut(); setMe(null) }) }, [])
  useEffect(() => { if (hasCredentials()) call<Me>('GET', '/api/v1/me').then(r => { if (r.status === 200) setMe(r.data); setChecking(false) }); else setChecking(false) }, [])
  if (checking) return null
  if (!me) return <Login onSignedIn={setMe} />
  return <Desk me={me} onSignOut={() => { signOut(); setMe(null) }} />
}

function Desk({ me, onSignOut }: { me: Me; onSignOut: () => void }) {
  const { t, lang, setLang } = useI18n()
  const trades = me.roles.includes('TRADER') || me.roles.includes('LIQUIDITY_PROVIDER')
  const lp = me.roles.includes('LIQUIDITY_PROVIDER')
  const pages: [Page, string, boolean][] = [['prices', 'Prices', true], ['trading', 'Trading', trades], ['charts', 'Charts', true], ['analytics', 'Analytics', true], ['news', 'News', true], ['portfolio', 'Portfolio', trades], ['margin', 'Margin', trades],
    ['statement', 'Statement', trades], ['wallet', 'Wallet', trades], ['alerts', 'Alerts', true], ['liquidity', 'Liquidity', true], ['services', 'Services', true], ['risk', 'Risk', me.roles.includes('RISK')], ['audit', 'Audit', me.roles.includes('ADMIN')]]
  const allowed = pages.filter(p => p[2])
  const [page, setPage] = useState<Page>('prices'), [selected, setSelected] = useState('SBNK'), [toasts, setToasts] = useState<Toast[]>([])
  const toast = useCallback((kind: Toast['kind'], text: string) => { const id = Date.now() + Math.random(); setToasts(x => [...x, { id, kind, text }]); setTimeout(() => setToasts(x => x.filter(y => y.id !== id)), 6000) }, [])

  return (
    <LiveProvider onAlert={text => toast('info', `🔔 Alert: ${text}`)}>
      <DeskBody me={me} onSignOut={onSignOut} trades={trades} lp={lp} allowed={allowed} page={page} setPage={setPage} selected={selected} setSelected={setSelected} toast={toast} toasts={toasts} t={t} lang={lang} setLang={setLang} />
    </LiveProvider>
  )
}

interface BodyProps { me: Me; onSignOut: () => void; trades: boolean; lp: boolean; allowed: [Page, string, boolean][]; page: Page; setPage: (p: Page) => void; selected: string; setSelected: (s: string) => void
  toast: (k: Toast['kind'], t: string) => void; toasts: Toast[]; t: (s: string) => string; lang: string; setLang: (l: 'en' | 'ar') => void }

function DeskBody({ me, onSignOut, trades, lp, allowed, page, setPage, selected, setSelected, toast, toasts, t, lang, setLang }: BodyProps) {
  const [board] = usePoll<BoardRow[]>(() => get('/api/v1/market/board'), [], 1000)
  const [summary] = usePoll<Summary>(() => get('/api/v1/market/summary'), [], 2000)
  const [indexSpark] = usePoll<Candle[]>(() => get('/api/v1/market/INDEX/candles?range=2M'), [], 120000)
  const [rules] = usePoll<Rules>(() => get('/api/v1/market/rules'), [], 60000)
  const [account, refreshAccount] = usePoll<Report>(() => get('/api/v1/accounts/me'), [], 1000, trades)
  const [statement] = usePoll<StatementLine[]>(() => get('/api/v1/accounts/me/trades'), [], 1500, trades && page === 'statement')
  const [calls] = usePoll<Report[]>(() => get('/api/v1/risk/margin-calls'), [], 1000, me.roles.includes('RISK'))
  const [audit] = usePoll<AuditLine[]>(() => get('/api/v1/admin/audit'), [], 1500, me.roles.includes('ADMIN') && page === 'audit')
  const [apps] = usePoll<ApplicationRow[]>(() => get('/api/v1/admin/applications'), [], 3000, me.roles.includes('ADMIN') && page === 'audit')
  const [favs, reloadFavs] = usePoll<string[]>(() => get('/api/v1/me/favorites'), [], 10000)
  const rows = board ?? []
  const toggleFav = async (s: string) => { await call(favs?.includes(s) ? 'DELETE' : 'PUT', `/api/v1/me/favorites/${s}`); reloadFavs() }
  const pick = (s: string) => { setSelected(s); if (trades) setPage('trading') }
  const open = summary?.session === 'OPEN'

  return (
    <div className="app">
      <header className="top">
        <div className="brand"><span className="logo">BD</span> Broker Order Desk</div>
        <nav>{allowed.map(([k, label]) => <button key={k} data-testid={`nav-${k}`} className={page === k ? 'on' : ''} onClick={() => setPage(k)}>{t(label)}</button>)}</nav>
        <div className="who" data-testid="who"><button className="langbtn" data-testid="lang" onClick={() => setLang(lang === 'ar' ? 'en' : 'ar')}>{lang === 'ar' ? 'EN' : 'العربية'}</button><span className="dot" />{me.username}<small>{me.roles.join(', ')}</small><button className="link light" data-testid="signout" onClick={onSignOut}>{t('Sign out')}</button></div>
      </header>
      <div className="strip" data-testid="market-header">
        <span className={`state ${open ? 'open' : 'closed'}`} data-testid="market-state">{open ? t('Market Open') : t('Market Closed')}</span><span className="sess">{t('Simulated session')}</span>
        {summary && <div className="indexbox" data-testid="index-box"><div><small>{t('Index')}</small><b>{(summary.indexPoints).toLocaleString()}</b></div><em className={summary.indexChangeBps >= 0 ? 'up' : 'down'}>{pct(summary.indexChangeBps)}</em><Spark values={(indexSpark ?? []).map(c => c.close)} width={90} height={26} /></div>}
        {summary && <><Metric label={t('Trades')} value={qty(summary.trades)} /><Metric label={t('Shares')} value={qty(summary.shares)} /><Metric label={t('Riyal')} value={money(summary.valueMills)} />
          <span className="breadth" title="advancers / decliners / unchanged"><i className="up">▲ {summary.advancers}</i><i className="down">▼ {summary.decliners}</i><i>● {summary.unchanged}</i></span></>}
        {trades && account && <><span className="sep" /><Metric label={t('Balance')} value={money(account.cashMills)} tone={account.cashMills < 0 ? 'down' : ''} testid="strip-balance" /><Metric label={t('Portfolio value')} value={money(account.marketValueMills)} />
          <Metric label={t('Holdings')} value={String(account.positions.length)} /><Metric label={t('Buy orders value')} value={money(account.reservedMills)} />
          {account.loanMills > 0 && <span className={`flag ${account.marginCall ? 'bad' : 'ok'}`} data-testid="margin-flag">{account.marginCall ? t('MARGIN CALL') : 'Margin OK'} {pct(account.equityBps)}</span>}</>}
      </div>
      <div className="ticker" aria-hidden><div className="ticker-in">{[...rows, ...rows].map((r, i) => <span key={i}><b>{r.symbol}</b> {px(r.lastPriceMills)} <em className={r.changeBps >= 0 ? 'up' : 'down'}>{pct(r.changeBps)}</em></span>)}</div></div>
      <main>
        {page === 'prices' && <PriceBoard rows={rows} selected={selected} onPick={pick} favorites={favs ?? []} onToggleFavorite={s => void toggleFav(s)} bandPercent={rules?.bandPercent ?? 10} />}
        {page === 'trading' && <Trading rows={rows} selected={selected} onPick={setSelected} account={account} rules={rules} toast={toast} refreshAccount={refreshAccount} />}
        {page === 'charts' && <ChartsPage rows={rows} />}
        {page === 'analytics' && <AnalyticsPage rows={rows} />}
        {page === 'news' && <NewsPage rows={rows} />}
        {page === 'portfolio' && <Portfolio account={account} />}
        {page === 'margin' && <Margin account={account} />}
        {page === 'statement' && <Statement lines={statement} />}
        {page === 'wallet' && <WalletPage toast={toast} refreshAccount={refreshAccount} />}
        {page === 'alerts' && <AlertsPage rows={rows} toast={toast} />}
        {page === 'liquidity' && <LiquidityPage rows={rows} canQuote={lp} toast={toast} selected={selected} />}
        {page === 'services' && <ServicesPage rules={rules} go={p => setPage(p as Page)} />}
        {page === 'risk' && <Risk calls={calls} />}
        {page === 'audit' && <><Audit lines={audit} /><section className="card" style={{ marginTop: 14 }}><h2>Account applications</h2><table data-testid="applications"><thead><tr><th>#</th><th>Name</th><th>Email</th><th>Type</th><th>Status</th></tr></thead>
          <tbody>{(apps ?? []).map(a => <tr key={a.id}><td>{a.id}</td><td>{a.fullName}</td><td>{a.email}</td><td>{a.type}</td><td><span className="pill NEW">{a.status}</span></td></tr>)}{apps && !apps.length && <tr><td colSpan={5} className="empty">—</td></tr>}</tbody></table></section></>}
      </main>
      <div className="toasts" aria-live="polite">{toasts.map(x => <div key={x.id} className={`toast ${x.kind}`} data-testid="toast">{x.text}</div>)}</div>
    </div>
  )
}

const Metric = ({ label, value, tone, testid }: { label: string; value: string; tone?: string; testid?: string }) => <span className="metric"><small>{label}</small><b className={tone} data-testid={testid}>{value}</b></span>
