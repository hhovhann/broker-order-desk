import { useState } from 'react'
import { call, signIn, signOut } from '../api'
import type { Me } from '../types'

const DEMO = [['trader1', 'Investor: margin account'], ['trader2', 'Investor: holds shares'], ['lp1', 'Liquidity provider'], ['risk1', 'Risk officer'], ['admin', 'Administrator']]

export function Login({ onSignedIn }: { onSignedIn: (me: Me) => void }) {
  const [user, setUser] = useState(''), [pass, setPass] = useState(''), [error, setError] = useState(''), [busy, setBusy] = useState(false)
  const submit = async (u: string, p: string) => {
    setBusy(true); setError('')
    signIn(u, p)
    const r = await call<Me>('GET', '/api/v1/me')
    setBusy(false)
    if (r.status === 200) onSignedIn(r.data)
    else { signOut(); setError('Wrong user name or password') }
  }
  return (
    <div className="login-wrap">
      <form className="login card" onSubmit={e => { e.preventDefault(); void submit(user, pass) }}>
        <div className="brand big"><span className="logo">BD</span> Broker Order Desk</div>
        <p className="muted">Sign in to trade, manage risk or review the audit trail.</p>
        <label>User name<input data-testid="login-user" value={user} onChange={e => setUser(e.target.value)} autoComplete="username" autoFocus /></label>
        <label>Password<input data-testid="login-pass" type="password" value={pass} onChange={e => setPass(e.target.value)} autoComplete="current-password" /></label>
        {error && <div className="error" role="alert">{error}</div>}
        <button className="primary" data-testid="login-submit" disabled={busy || !user || !pass}>Sign in</button>
        <div className="demo-users">
          <div className="muted small">Synthetic demo users (password <code>demo-pass</code>)</div>
          {DEMO.map(([u, d]) => <button type="button" key={u} className="chip" data-testid={`demo-${u}`} onClick={() => void submit(u, 'demo-pass')}><b>{u}</b> {d}</button>)}
        </div>
      </form>
    </div>
  )
}
