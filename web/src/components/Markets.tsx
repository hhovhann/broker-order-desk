import { useState } from 'react'
import { get } from '../api'
import type { AnalyticsRow, BoardRow, Candle, NewsItem, Series } from '../types'
import { money, pct, px, qty } from '../format'
import { useI18n } from '../i18n'
import { usePoll } from '../hooks'
import { Candles } from './Candles'
import { LineChart } from './LineChart'
import { Spark } from './Spark'

const RANGES = ['1M', '2M', '3M', '1Y', '2Y', '3Y', 'ALL']
const Ranges = ({ value, onChange }: { value: string; onChange: (r: string) => void }) => <div className="ranges">{RANGES.map(r => <button key={r} className={r === value ? 'on' : ''} data-testid={`range-${r}`} onClick={() => onChange(r)}>{r}</button>)}</div>

function SymbolList({ rows, selected, onPick, withIndex, multi }: { rows: BoardRow[]; selected: string[]; onPick: (s: string) => void; withIndex?: boolean; multi?: boolean }) {
  return (
    <div className="symlist" role="listbox" aria-multiselectable={multi}>
      {withIndex && <button className={selected.includes('INDEX') ? 'on' : ''} onClick={() => onPick('INDEX')}><b>INDEX</b><span>Index</span></button>}
      {rows.map(r => <button key={r.symbol} data-testid={`pick-${r.symbol}`} className={selected.includes(r.symbol) ? 'on' : ''} onClick={() => onPick(r.symbol)}><b>{r.symbol}</b><span>{r.name.replace(' (synthetic)', '')}</span></button>)}
    </div>
  )
}

export function ChartsPage({ rows }: { rows: BoardRow[] }) {
  const { t } = useI18n()
  const [mode, setMode] = useState<'candles' | 'compare'>('candles'), [range, setRange] = useState('1Y'), [sym, setSym] = useState('INDEX'), [picked, setPicked] = useState<string[]>(['INDEX', 'SBNK'])
  const [candles] = usePoll<Candle[]>(() => get(`/api/v1/market/${sym}/candles?range=${range}`), [sym, range], 30000, mode === 'candles')
  const [series] = usePoll<Series[]>(() => get(`/api/v1/market/compare?symbols=${picked.join(',')}&range=${range}`), [picked.join(','), range], 30000, mode === 'compare' && picked.length > 0)
  const togglePick = (s: string) => setPicked(p => (p.includes(s) ? p.filter(x => x !== s) : p.length < 6 ? [...p, s] : p))
  const last = candles?.[candles.length - 1], first = candles?.[0]
  return (
    <div className="split">
      <aside className="card"><SymbolList rows={rows} withIndex multi={mode === 'compare'} selected={mode === 'candles' ? [sym] : picked} onPick={mode === 'candles' ? setSym : togglePick} /></aside>
      <section className="card">
        <div className="card-head">
          <div className="tabs"><button className={mode === 'candles' ? 'on' : ''} data-testid="mode-candles" onClick={() => setMode('candles')}>{t('Candles')}</button><button className={mode === 'compare' ? 'on' : ''} data-testid="mode-compare" onClick={() => setMode('compare')}>{t('Compare')}</button></div>
          <Ranges value={range} onChange={setRange} />
        </div>
        {mode === 'candles' && last && first && <div className="chart-head"><b>{sym}</b> <span className="num">{sym === 'INDEX' ? last.close.toLocaleString('en-US') : px(last.close)}</span> <em className={last.close >= first.close ? 'up' : 'down'}>{pct(((last.close - first.close) * 10000) / first.close)}</em> <span className="muted small">{range}</span></div>}
        {mode === 'candles' ? <Candles data={candles ?? []} integer={sym === 'INDEX'} /> : <LineChart series={series ?? []} />}
      </section>
    </div>
  )
}

const num = (v: number | null, d = 3) => (v === null ? 'N/A' : (v / 1000).toFixed(d))
const cell = (v: number | null, bad = (x: number) => x < 0) => (v !== null && bad(v) ? 'r num badcell' : 'r num')

export function AnalyticsPage({ rows }: { rows: BoardRow[] }) {
  const { t } = useI18n()
  const [tab, setTab] = useState<'companies' | 'statements' | 'ratios'>('ratios'), [sym, setSym] = useState('SBNK')
  const [data] = usePoll<AnalyticsRow[]>(() => get('/api/v1/analytics'), [], 20000)
  const [candles] = usePoll<Candle[]>(() => get(`/api/v1/market/${sym}/candles?range=1Y`), [sym], 60000, tab === 'companies')
  const byRow = new Map(rows.map(r => [r.symbol, r]))
  const exportCsv = () => {
    const head = ['Symbol', 'Period', 'Assets', 'Liabilities', 'Equity', 'Profit', 'EPS', 'Dividend yield %', 'Book value', 'P/E', 'P/B']
    const body = (data ?? []).map(d => [d.symbol, d.period, d.assetsMills / 1000, d.liabilitiesMills / 1000, d.equityMills / 1000, d.profitMills / 1000, num(d.epsMills), d.dividendYieldBps === null ? '' : d.dividendYieldBps / 100, num(d.bookValueMills), num(d.peX1000), num(d.pbX1000)].join(','))
    const a = document.createElement('a'); a.href = URL.createObjectURL(new Blob([[head.join(','), ...body].join('\n')], { type: 'text/csv' })); a.download = 'analytics.csv'; a.click()
  }
  const sel = data?.find(d => d.symbol === sym)
  return (
    <section className="card">
      <div className="card-head"><div className="tabs">{(['companies', 'statements', 'ratios'] as const).map(k => <button key={k} data-testid={`an-${k}`} className={tab === k ? 'on' : ''} onClick={() => setTab(k)}>{k === 'companies' ? t('Company') : k === 'statements' ? t('Statements') : t('Ratios')}</button>)}</div>
        {tab !== 'companies' && <button className="link" onClick={exportCsv}>{t('Export CSV')}</button>}</div>
      {tab === 'companies' ? (
        <div className="split inner">
          <SymbolList rows={rows} selected={[sym]} onPick={setSym} />
          <div>{sel ? <>
            <h3 className="h3">{sel.symbol} <span className="muted">{sel.name}</span></h3>
            <div className="tiles small5"><div className="tile"><div className="muted small">{t('Last')}</div><div className="tile-v">{px(sel.lastPriceMills)}</div></div><div className="tile"><div className="muted small">EPS</div><div className="tile-v">{num(sel.epsMills)}</div></div>
              <div className="tile"><div className="muted small">P/E</div><div className="tile-v">{num(sel.peX1000)}</div></div><div className="tile"><div className="muted small">P/B</div><div className="tile-v">{num(sel.pbX1000)}</div></div>
              <div className="tile"><div className="muted small">{t('Dividend Yield')}</div><div className="tile-v">{sel.dividendYieldBps === null ? 'N/A' : pct(sel.dividendYieldBps)}</div></div></div>
            <Candles data={(candles ?? []).slice(-120)} height={300} /></> : <div className="empty">…</div>}</div>
        </div>
      ) : (
        <div className="scroll tall"><table data-testid={tab === 'ratios' ? 'ratios' : 'statements'}>
          <thead><tr><th>{t('Company')}</th><th className="r">{t('Last')}</th><th className="r">{t('Change %')}</th><th>{t('Trend')}</th><th>Period</th>
            {tab === 'statements' ? <><th className="r">{t('Assets')}</th><th className="r">{t('Liabilities')}</th><th className="r">{t('Equity')}</th><th className="r">{t('Profit')}</th><th className="r">{t('Profit / Equity')}</th></>
              : <><th className="r">Up days / year</th><th className="r">EPS</th><th className="r">{t('Dividend Yield')}</th><th className="r">{t('Book Value')}</th><th className="r">{t('P/E')}</th><th className="r">{t('P/B')}</th></>}</tr></thead>
          <tbody>{(data ?? []).map(d => { const b = byRow.get(d.symbol); return (
            <tr key={d.symbol}><td><b>{d.symbol}</b> <span className="muted small">{d.name.replace(' (synthetic)', '')}</span></td><td className="r num">{px(d.lastPriceMills)}</td>
              <td className={`r num ${b && b.changeBps >= 0 ? 'up' : 'down'}`}>{b ? pct(b.changeBps) : ''}</td><td>{b && <Spark values={b.spark} width={80} height={20} up={b.changeBps >= 0} />}</td><td>{d.period}</td>
              {tab === 'statements' ? <><td className="r num">{money(d.assetsMills)}</td><td className="r num">{money(d.liabilitiesMills)}</td><td className="r num">{money(d.equityMills)}</td><td className={cell(d.profitMills)}>{money(d.profitMills)}</td><td className={cell(d.profitOverEquityBps)}>{d.profitOverEquityBps === null ? 'N/A' : pct(d.profitOverEquityBps)}</td></>
                : <><td className="r num"><span className="upbar"><i style={{ width: `${Math.min(100, (d.upDaysYear / 130) * 100)}%` }} /></span> {d.upDaysYear}</td><td className={cell(d.epsMills)}>{num(d.epsMills)}</td><td className="r num">{d.dividendYieldBps === null ? 'N/A' : pct(d.dividendYieldBps)}</td><td className={cell(d.bookValueMills)}>{num(d.bookValueMills)}</td><td className={cell(d.peX1000)}>{num(d.peX1000)}</td><td className={cell(d.pbX1000)}>{num(d.pbX1000)}</td></>}
            </tr>) })}</tbody></table></div>
      )}
    </section>
  )
}

export function NewsPage({ rows }: { rows: BoardRow[] }) {
  const { t } = useI18n()
  const [sym, setSym] = useState('MARKET'), [range, setRange] = useState('1Y')
  const [news] = usePoll<NewsItem[]>(() => get(`/api/v1/news?kind=NEWS&symbol=${sym}`), [sym], 30000)
  const [disc] = usePoll<NewsItem[]>(() => get(`/api/v1/news?kind=DISCLOSURE&symbol=${sym}`), [sym], 30000)
  const [candles] = usePoll<Candle[]>(() => get(`/api/v1/market/${sym === 'MARKET' ? 'INDEX' : sym}/candles?range=${range}`), [sym, range], 60000)
  const list = (items: NewsItem[] | null, testid: string) => <div className="feed" data-testid={testid}>{(items ?? []).map(n => <article key={n.id}><span className="date">{n.date}</span>{n.symbol && <span className="pill">{n.symbol}</span>}<div><b>{n.title}</b><div className="muted small">{n.body}</div></div></article>)}{items && !items.length && <div className="empty">—</div>}</div>
  return (
    <div className="split">
      <aside className="card"><div className="symlist"><button className={sym === 'MARKET' ? 'on' : ''} data-testid="news-MARKET" onClick={() => setSym('MARKET')}><b>{t('Market')}</b></button>
        {rows.map(r => <button key={r.symbol} data-testid={`news-${r.symbol}`} className={sym === r.symbol ? 'on' : ''} onClick={() => setSym(r.symbol)}><b>{r.symbol}</b><span>{r.name.replace(' (synthetic)', '')}</span></button>)}</div></aside>
      <div className="stack">
        <div className="grid2"><section className="card"><h2>{t('News')}</h2>{list(news, 'news-feed')}</section><section className="card"><h2>{t('Disclosures')}</h2>{list(disc, 'disc-feed')}</section></div>
        <section className="card"><div className="card-head"><h2>{sym === 'MARKET' ? t('Index') : sym}</h2><Ranges value={range} onChange={setRange} /></div><Candles data={candles ?? []} height={280} integer={sym === 'MARKET'} /></section>
      </div>
    </div>
  )
}

export const _unused = { qty }
