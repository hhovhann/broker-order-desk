import { useMemo, useState } from 'react'
import type { BoardRow } from '../types'
import { money, pct, px, qty } from '../format'
import { useFlash } from '../hooks'
import { Spark } from './Spark'
import { useI18n } from '../i18n'

type SortKey = 'symbol' | 'changeBps' | 'valueMills' | 'trades'

function Last({ v, cls }: { v: number; cls: string }) { const f = useFlash(v); return <td className={`r num ${cls} ${f ? 'flash-' + f : ''}`}>{px(v)}</td> }

export function PriceBoard({ rows, selected, onPick, favorites, onToggleFavorite, bandPercent }: { rows: BoardRow[]; selected: string; onPick: (s: string) => void; favorites: string[]; onToggleFavorite: (s: string) => void; bandPercent: number }) {
  const { t } = useI18n()
  const [onlyFav, setOnlyFav] = useState(false)
  const [key, setKey] = useState<SortKey>('symbol'), [asc, setAsc] = useState(true), [filter, setFilter] = useState('')
  const view = useMemo(() => {
    const f = rows.filter(r => (r.symbol + r.name).toLowerCase().includes(filter.toLowerCase()) && (!onlyFav || favorites.includes(r.symbol)))
    return [...f].sort((a, b) => (key === 'symbol' ? a.symbol.localeCompare(b.symbol) : a[key] - b[key]) * (asc ? 1 : -1))
  }, [rows, key, asc, filter, onlyFav, favorites])
  const th = (k: SortKey, label: string, cls = '') => <th className={`${cls} sortable`} onClick={() => { if (k === key) setAsc(!asc); else { setKey(k); setAsc(true) } }}>{label}{k === key ? (asc ? ' ▲' : ' ▼') : ''}</th>
  const exportCsv = () => {
    const head = ['Symbol', 'Company', 'Offer shares', 'Offer', 'Last', 'Bid', 'Bid shares', 'Trades', 'Average', 'Shares', 'Riyal', 'Prev close', 'High', 'Low', 'Change', 'Change %']
    const body = view.map(r => [r.symbol, r.name, r.offerShares, px(r.offerPriceMills), px(r.lastPriceMills), px(r.bidPriceMills), r.bidShares, r.trades, px(r.averageMills), r.shares, money(r.valueMills).replace(/,/g, ''), px(r.prevCloseMills), px(r.highMills), px(r.lowMills), px(r.changeMills), pct(r.changeBps)].join(','))
    const a = document.createElement('a'); a.href = URL.createObjectURL(new Blob([[head.join(','), ...body].join('\n')], { type: 'text/csv' })); a.download = 'prices.csv'; a.click()
  }
  const heat = (bps: number) => (bps === 0 ? '' : bps > 0 ? `up h${Math.min(4, Math.ceil(bps / 100))}` : `down h${Math.min(4, Math.ceil(-bps / 100))}`)
  return (
    <section className="card">
      <div className="card-head"><h2>{t('Prices')}</h2><div className="row-gap"><div className="tabs"><button className={!onlyFav ? 'on' : ''} data-testid="fav-all" onClick={() => setOnlyFav(false)}>{t('All')}</button><button className={onlyFav ? 'on' : ''} data-testid="fav-only" onClick={() => setOnlyFav(true)}>★ {t('Favorites')}</button></div>
        <button className="link" data-testid="export-csv" onClick={exportCsv}>{t('Export CSV')}</button><input className="search" placeholder={t('Filter symbol or name')} value={filter} onChange={e => setFilter(e.target.value)} /></div></div>
      <div className="scroll">
        <table className="board" data-testid="board">
          <thead><tr>
            <th></th>{th('symbol', t('Symbol'))}<th>{t('Company')}</th><th className="r offer">{t('Offer shares')}</th><th className="r offer">{t('Offer')}</th><th className="r">{t('Last')}</th><th className="r bidh">{t('Bid')}</th><th className="r bidh">{t('Bid shares')}</th>
            {th('trades', t('Trades'), 'r')}<th className="r">{t('Average')}</th><th className="r">{t('Shares')}</th>{th('valueMills', t('Riyal'), 'r')}<th className="r">{t('Prev close')}</th><th className="r">{t('High')}</th><th className="r">{t('Low')}</th><th>{t('Trend')}</th><th className="r">{t('Change')}</th>{th('changeBps', t('Change %'), 'r')}
          </tr></thead>
          <tbody>
            {view.map(r => (
              <tr key={r.symbol} className={r.symbol === selected ? 'sel' : ''} onClick={() => onPick(r.symbol)} data-testid={`row-${r.symbol}`}>
                <td><button className={`star ${favorites.includes(r.symbol) ? 'on' : ''}`} data-testid={`star-${r.symbol}`} aria-label="favorite" onClick={e => { e.stopPropagation(); onToggleFavorite(r.symbol) }}>★</button></td>
                <td title={`Limit up ${px(Math.floor(r.prevCloseMills * (100 + bandPercent) / 100))} · Limit down ${px(Math.floor(r.prevCloseMills * (100 - bandPercent) / 100))}`}><b>{r.symbol}</b></td><td>{r.name}</td>
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
