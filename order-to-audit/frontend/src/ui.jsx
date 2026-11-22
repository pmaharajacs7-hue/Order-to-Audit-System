import { useCallback, useEffect, useState } from 'react'

/** Loads data from an async function; returns { data, error, loading, reload }. */
export function useLoad(fn, deps = []) {
  const [state, setState] = useState({ data: null, error: '', loading: true })
  const run = useCallback(async () => {
    setState((s) => ({ ...s, loading: true, error: '' }))
    try {
      setState({ data: await fn(), error: '', loading: false })
    } catch (e) {
      setState({ data: null, error: e.message, loading: false })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps)
  useEffect(() => { run() }, [run])
  return { ...state, reload: run }
}

export function Notice({ error, ok }) {
  if (error) return <div className="notice error" role="alert">{error}</div>
  if (ok) return <div className="notice ok" role="status">{ok}</div>
  return null
}

export function Card({ title, actions, children }) {
  return (
    <section className="card">
      {(title || actions) && (
        <header className="card-head">
          <h2>{title}</h2>
          <div className="row">{actions}</div>
        </header>
      )}
      {children}
    </section>
  )
}

/** columns: [{ key, label, render?, align? }] */
export function Table({ columns, rows, empty = 'Nothing here yet.', rowKey = 'id', onRowClick }) {
  if (!rows || rows.length === 0) return <p className="muted">{empty}</p>
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>{columns.map((c) => <th key={c.key} className={c.align === 'right' ? 'right' : ''}>{c.label}</th>)}</tr>
        </thead>
        <tbody>
          {rows.map((r, i) => (
            <tr key={r[rowKey] ?? i} onClick={onRowClick ? () => onRowClick(r) : undefined}
                className={onRowClick ? 'clickable' : ''}>
              {columns.map((c) => (
                <td key={c.key} className={c.align === 'right' ? 'right' : ''}>
                  {c.render ? c.render(r) : r[c.key]}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

export function Field({ label, children, hint }) {
  return (
    <label className="field">
      <span>{label}</span>
      {children}
      {hint && <small className="muted">{hint}</small>}
    </label>
  )
}

export function Badge({ children, tone = 'neutral' }) {
  return <span className={`badge ${tone}`}>{children}</span>
}

export function statusTone(status) {
  return { SUBMITTED: 'warn', APPROVED: 'info', INVOICED: 'info', DISPATCHED: 'good', REJECTED: 'bad',
           ISSUED: 'info' }[status] || 'neutral'
}

export function Tile({ label, value, tone }) {
  return (
    <div className={`tile ${tone || ''}`}>
      <div className="tile-label">{label}</div>
      <div className="tile-value">{value}</div>
    </div>
  )
}
