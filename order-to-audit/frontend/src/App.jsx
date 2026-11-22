import { useEffect, useState } from 'react'
import { api, clearSession, loadSession, saveSession, setUnauthorizedHandler } from './api.js'
import { Notice } from './ui.jsx'
import Dashboard from './pages/Dashboard.jsx'
import Orders from './pages/Orders.jsx'
import Production from './pages/Production.jsx'
import Purchases from './pages/Purchases.jsx'
import Inventory from './pages/Inventory.jsx'
import Masters from './pages/Masters.jsx'
import Payments from './pages/Payments.jsx'
import Reports from './pages/Reports.jsx'

const PAGES = {
  dashboard: { label: 'Dashboard', view: Dashboard },
  orders: { label: 'Orders', view: Orders },
  production: { label: 'Production', view: Production },
  purchases: { label: 'Purchases', view: Purchases },
  inventory: { label: 'Inventory', view: Inventory },
  masters: { label: 'Master data', view: Masters },
  payments: { label: 'Payments', view: Payments },
  reports: { label: 'Reports', view: Reports },
}

// What each role sees. The API enforces the same rules; this only hides screens that would be refused.
const NAV = {
  ADMIN: ['dashboard', 'orders', 'production', 'purchases', 'inventory', 'masters', 'payments', 'reports'],
  SALESMAN: ['orders'],
  PRODUCTION_SUPERVISOR: ['production', 'inventory'],
  ACCOUNTANT: ['reports', 'payments', 'inventory'],
  OWNER: ['dashboard', 'inventory', 'reports'],
}

function Login({ onLogin }) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function submit(e) {
    e.preventDefault()
    setBusy(true)
    setError('')
    try {
      const s = await api('/api/auth/login', { method: 'POST', body: { username, password } })
      onLogin(s)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="login-wrap">
      <form className="card login" onSubmit={submit}>
        <h1>Order-to-Audit</h1>
        <p className="muted">Sign in to manage orders, production, stock and billing.</p>
        <Notice error={error} />
        <label className="field"><span>Username</span>
          <input value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" autoFocus required />
        </label>
        <label className="field"><span>Password</span>
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" required />
        </label>
        <button className="btn" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'}</button>
        <p className="hint muted">Demo logins: admin, salesman, supervisor, accountant, owner</p>
      </form>
    </div>
  )
}

export default function App() {
  const [session, setSession] = useState(() => loadSession())
  const [page, setPage] = useState(null)

  useEffect(() => {
    setUnauthorizedHandler(() => { clearSession(); setSession(null) })
  }, [])

  function login(s) {
    saveSession(s)
    setSession(s)
    setPage(null)
  }

  function logout() {
    clearSession()
    setSession(null)
  }

  if (!session) return <Login onLogin={login} />

  const allowed = NAV[session.role] || []
  const current = allowed.includes(page) ? page : allowed[0]
  const View = current ? PAGES[current].view : null

  return (
    <div className="shell">
      <nav className="sidebar" aria-label="Main">
        <div className="brand">Order-to-Audit</div>
        {allowed.map((key) => (
          <button key={key} className={`nav-btn ${key === current ? 'active' : ''}`} onClick={() => setPage(key)}
                  aria-current={key === current ? 'page' : undefined}>
            {PAGES[key].label}
          </button>
        ))}
        <div className="who">
          <div>{session.fullName}</div>
          <div className="muted">{session.role.replaceAll('_', ' ').toLowerCase()}</div>
          <button className="btn secondary small" style={{ marginTop: 8 }} onClick={logout}>Sign out</button>
        </div>
      </nav>
      <main className="content">
        {View ? <View role={session.role} /> : <p>No screens available for this role.</p>}
      </main>
    </div>
  )
}
