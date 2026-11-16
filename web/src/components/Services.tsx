import { useState } from 'react'
import { get } from '../api'
import type { Bank, Rules } from '../types'
import { money, px } from '../format'
import { useI18n } from '../i18n'
import { usePoll } from '../hooks'

type Tab = 'tadawul' | 'margin' | 'maker' | 'banks' | 'institutions' | 'account' | 'tariff' | 'purification'
const TABS: [Tab, string][] = [['tadawul', 'Trading'], ['margin', 'Margin'], ['maker', 'Liquidity'], ['banks', 'Wallet'], ['institutions', 'Institutions'], ['account', 'Open Account'], ['tariff', 'Tariff'], ['purification', 'Profit Purification']]

export function ServicesPage({ rules, go }: { rules: Rules | null; go: (page: string) => void }) {
  const { t } = useI18n()
  const [tab, setTab] = useState<Tab>('tadawul')
  return (
    <section className="card">
      <div className="tabs wrap">{TABS.map(([k, label]) => <button key={k} data-testid={`svc-${k}`} className={tab === k ? 'on' : ''} onClick={() => setTab(k)}>{t(label)}</button>)}</div>
      {tab === 'tadawul' && <div className="cards4">{[['prices', 'Prices', 'Live offer, bid, depth and trend for every symbol'], ['trading', 'Orders', 'Limit orders with pre-trade checks'], ['statement', 'Statement', 'Every execution on your account'], ['wallet', 'Wallet', 'Deposit and withdraw through bank rails']].map(([k, a, b]) =>
        <button key={k} className="svc-card" onClick={() => go(k)}><b>{t(a)}</b><span className="muted small">{b}</span></button>)}</div>}
      {tab === 'margin' && <div className="cards4" data-testid="margin-terms">
        <div className="svc-card static"><b>{rules ? `${rules.maintenanceMarginPercent}%` : '-'}</b><span className="muted small">Maintenance level: a margin call is raised when equity falls below it</span></div>
        <div className="svc-card static"><b>{rules ? `±${rules.bandPercent}%` : '-'}</b><span className="muted small">Daily price band around the previous close</span></div>
        <div className="svc-card static"><b>{rules ? money(rules.maxOrderValueMills) : '-'}</b><span className="muted small">Maximum value of a single order</span></div>
        <div className="svc-card static"><b>Reserved up front</b><span className="muted small">Buys reserve buying power at the limit price; sells reserve the shares</span></div></div>}
      {tab === 'maker' && <div className="muted pad">Liquidity providers post two-sided quotes through the same risk, matching and settlement path as any order. <button className="link" onClick={() => go('liquidity')}>Open the liquidity view</button></div>}
      {tab === 'banks' && <Banks />}
      {tab === 'institutions' && <Institutions />}
      {tab === 'account' && <OpenAccount />}
      {tab === 'tariff' && <Tariff />}
      {tab === 'purification' && <Purification />}
    </section>
  )
}

function Banks() {
  const [banks] = usePoll<Bank[]>(() => get('/api/v1/wallet/banks'), [], 600000)
  const rails = ['SAHL_PLUS', 'SAHL', 'FAST']
  return <div className="stack pad" data-testid="banks">{rails.map(r => <div key={r} className="rail"><b>{r.replace('_', '+')}</b><div>{(banks ?? []).filter(b => b.rail === r).map(b => <span className="chip static" key={b.name}>{b.name}</span>)}</div></div>)}
    <div className="muted small">Synthetic banks on simulated rails. The three rails mirror the shape of a national transfer scheme: instant, standard and bank-to-bank transfer.</div></div>
}

function Institutions() {
  return (
    <div className="pad stack" data-testid="institutions">
      <div className="cards4">
        <div className="svc-card static"><b>REST order API</b><span className="muted small">Implemented. Versioned <code>/api/v1</code>, idempotent client order ids, role-based access. See the README.</span></div>
        <div className="svc-card static"><b>Live stream</b><span className="muted small">Implemented. Server-sent events for market changes and alerts.</span></div>
        <div className="svc-card static dim"><b>FIX gateway</b><span className="muted small">Not built. Planned as a QuickFIX/J adapter in front of the same order service.</span></div>
        <div className="svc-card static dim"><b>Direct market access</b><span className="muted small">Not built. Needs exchange connectivity, which a sample cannot have.</span></div>
      </div>
      <div className="muted small">Marked clearly so nothing here is mistaken for a working exchange link.</div>
    </div>
  )
}

function OpenAccount() {
  const { t } = useI18n()
  const [type, setType] = useState('INSIDE_QATAR'), [name, setName] = useState(''), [email, setEmail] = useState(''), [note, setNote] = useState(''), [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null)
  const submit = async () => {
    const r = await fetch('/api/v1/public/applications', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ fullName: name, email, type, note }) })
    const j = await r.json().catch(() => ({}))
    setMsg(r.status === 201 ? { ok: true, text: `Application ${j.applicationId} received. Status: ${j.status}.` } : { ok: false, text: j.detail ?? `Could not submit (${r.status})` })
    if (r.status === 201) { setName(''); setEmail(''); setNote('') }
  }
  const types: [string, string][] = [['INSIDE_QATAR', 'Individual inside Qatar'], ['OUTSIDE_QATAR', 'Individual outside Qatar'], ['INSTITUTION', 'Institution'], ['AGENT', 'Agent']]
  return (
    <div className="form pad" data-testid="open-account">
      <div className="seg wrapseg">{types.map(([k, l]) => <button key={k} data-testid={`type-${k}`} className={type === k ? 'on buy' : ''} onClick={() => setType(k)}>{l}</button>)}</div>
      <label>{t('Full name')}<input data-testid="app-name" value={name} onChange={e => setName(e.target.value)} maxLength={80} /></label>
      <label>{t('Email')}<input data-testid="app-email" type="email" value={email} onChange={e => setEmail(e.target.value)} maxLength={120} /></label>
      <label>Note<input data-testid="app-note" value={note} onChange={e => setNote(e.target.value)} maxLength={300} /></label>
      <button className="primary" data-testid="app-submit" disabled={!name || !email} onClick={() => void submit()}>{t('Submit')}</button>
      {msg && <div className={msg.ok ? 'hint ok' : 'hint'} data-testid="app-msg">{msg.text}</div>}
      <div className="muted small">Use invented details only. This demo stores the request for an administrator to review.</div>
    </div>
  )
}

const TARIFF: [string, string, string][] = [['Brokerage commission', '0.275% of value', 'Synthetic'], ['Minimum commission per order', 'QAR 25.00', 'Synthetic'], ['Margin financing profit rate', 'Per agreement', 'Synthetic'], ['Account statement (paper)', 'QAR 10.00', 'Synthetic'], ['Inactive account fee', 'None', 'Synthetic']]
function Tariff() {
  return <div className="pad"><table data-testid="tariff"><thead><tr><th>Service</th><th>Fee</th><th>Note</th></tr></thead><tbody>{TARIFF.map(r => <tr key={r[0]}><td>{r[0]}</td><td>{r[1]}</td><td className="muted">{r[2]}</td></tr>)}</tbody></table></div>
}

/** Illustrative calculator only: the real ratio comes from the shariah board of the company concerned. */
function Purification() {
  const [dividend, setDividend] = useState('1000'), [ratio, setRatio] = useState('3.5')
  const d = parseFloat(dividend || '0'), r = parseFloat(ratio || '0'), out = Number.isFinite(d * r) ? (d * r) / 100 : 0
  return (
    <div className="form pad" data-testid="purification">
      <label>Dividends received (QAR)<input data-testid="pur-div" inputMode="decimal" value={dividend} onChange={e => setDividend(e.target.value)} /></label>
      <label>Non-permissible income ratio (%)<input data-testid="pur-ratio" inputMode="decimal" value={ratio} onChange={e => setRatio(e.target.value)} /></label>
      <div className="est"><span className="muted">Amount to purify</span><b data-testid="pur-out">QAR {out.toFixed(2)}</b></div>
      <div className="muted small">Illustration only, with an invented ratio. Not religious or financial advice. {px(0) && ''}</div>
    </div>
  )
}
