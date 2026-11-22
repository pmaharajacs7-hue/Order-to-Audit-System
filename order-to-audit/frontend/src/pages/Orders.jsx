import { useState } from 'react'
import { api, download, money } from '../api.js'
import { Badge, Card, Field, Notice, Table, statusTone, useLoad } from '../ui.jsx'

function NewOrder({ onCreated }) {
  const customers = useLoad(() => api('/api/customers'))
  const products = useLoad(() => api('/api/products'))
  const [customerId, setCustomerId] = useState('')
  const [notes, setNotes] = useState('')
  const [lines, setLines] = useState([{ productId: '', quantity: 1 }])
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const priceOf = (id) => products.data?.find((p) => String(p.id) === String(id))?.unitPrice
  const total = lines.reduce((sum, l) => sum + (Number(priceOf(l.productId)) || 0) * (Number(l.quantity) || 0), 0)

  const setLine = (i, patch) => setLines(lines.map((l, idx) => (idx === i ? { ...l, ...patch } : l)))

  async function submit(e) {
    e.preventDefault()
    setError('')
    setBusy(true)
    try {
      await api('/api/orders', {
        method: 'POST',
        body: {
          customerId: Number(customerId), notes: notes || null,
          items: lines.map((l) => ({ productId: Number(l.productId), quantity: Number(l.quantity) })),
        },
      })
      setCustomerId(''); setNotes(''); setLines([{ productId: '', quantity: 1 }])
      onCreated()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Card title="New order">
      <form onSubmit={submit} className="line-items">
        <Notice error={error || customers.error || products.error} />
        <div className="form-grid" style={{ marginBottom: 12 }}>
          <Field label="Customer">
            <select value={customerId} onChange={(e) => setCustomerId(e.target.value)} required>
              <option value="">Select customer…</option>
              {customers.data?.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
            </select>
          </Field>
          <Field label="Notes (optional)">
            <input value={notes} onChange={(e) => setNotes(e.target.value)} maxLength={255} />
          </Field>
        </div>
        {lines.map((l, i) => (
          <div className="row" key={i}>
            <Field label="Product">
              <select value={l.productId} onChange={(e) => setLine(i, { productId: e.target.value })} required>
                <option value="">Select product…</option>
                {products.data?.map((p) => <option key={p.id} value={p.id}>{p.name} ({money(p.unitPrice)})</option>)}
              </select>
            </Field>
            <Field label="Packets">
              <input type="number" min="1" step="1" value={l.quantity} onChange={(e) => setLine(i, { quantity: e.target.value })} required />
            </Field>
            <button type="button" className="btn danger small" disabled={lines.length === 1}
                    onClick={() => setLines(lines.filter((_, idx) => idx !== i))}>Remove</button>
          </div>
        ))}
        <div className="row" style={{ justifyContent: 'space-between', marginTop: 8 }}>
          <button type="button" className="btn secondary small" onClick={() => setLines([...lines, { productId: '', quantity: 1 }])}>
            + Add product
          </button>
          <span>Estimated value (before GST): <strong>{money(total)}</strong></span>
          <button className="btn" disabled={busy}>{busy ? 'Submitting…' : 'Submit order'}</button>
        </div>
      </form>
    </Card>
  )
}

export default function Orders({ role }) {
  const orders = useLoad(() => api('/api/orders'))
  const invoices = useLoad(() => (role === 'ADMIN' ? api('/api/invoices') : Promise.resolve([])), [role])
  const [msg, setMsg] = useState({ error: '', ok: '' })
  const isAdmin = role === 'ADMIN'

  const invoiceFor = (orderId) => invoices.data?.find((i) => i.orderId === orderId)

  async function act(label, fn) {
    setMsg({ error: '', ok: '' })
    try {
      await fn()
      setMsg({ error: '', ok: label })
      orders.reload()
      invoices.reload()
    } catch (err) {
      setMsg({ error: err.message, ok: '' })
    }
  }

  const reject = (o) => {
    const reason = window.prompt('Reason for rejecting this order (optional):', '')
    if (reason === null) return
    act(`Order #${o.id} rejected`, () => api(`/api/orders/${o.id}/reject`, { method: 'POST', body: { reason } }))
  }

  const editOrder = (o) => {
    const items = []
    for (const line of o.items) {
      const quantity = window.prompt(`Quantity for ${line.productName}:`, String(line.quantity))
      if (quantity === null) return
      const unitPrice = window.prompt(`Unit price (before GST) for ${line.productName}:`, String(line.unitPrice))
      if (unitPrice === null) return
      items.push({ productId: line.productId, quantity: Number(quantity), unitPrice: Number(unitPrice) })
    }
    act(`Order #${o.id} updated`, () => api(`/api/orders/${o.id}`, { method: 'PUT', body: { items } }))
  }

  return (
    <>
      <h1 className="page-title">{role === 'SALESMAN' ? 'My orders' : 'Orders'}</h1>
      {(role === 'SALESMAN' || isAdmin) && <NewOrder onCreated={() => { orders.reload(); setMsg({ error: '', ok: 'Order submitted' }) }} />}
      <Notice error={msg.error || orders.error} ok={msg.ok} />
      <Card title="Order list">
        <Table rows={orders.data} empty="No orders yet."
          columns={[
            { key: 'id', label: '#' },
            { key: 'orderDate', label: 'Date' },
            { key: 'customerName', label: 'Customer' },
            ...(role !== 'SALESMAN' ? [{ key: 'salesman', label: 'Salesman' }] : []),
            { key: 'items', label: 'Items / stock check', render: (o) => o.items.map((i) => `${i.quantity} × ${i.productName} (available: ${i.availableStock}${i.stockSufficient ? '' : ' — SHORTAGE'})`).join(', ') },
            { key: 'subtotal', label: 'Value', align: 'right', render: (o) => money(o.subtotal) },
            { key: 'status', label: 'Status', render: (o) => <Badge tone={statusTone(o.status)}>{o.status}</Badge> },
            ...(isAdmin ? [{
              key: 'actions', label: 'Actions', render: (o) => {
                const inv = invoiceFor(o.id)
                return (
                  <div className="row">
                    {o.status === 'SUBMITTED' && <>
                      <button className="btn secondary small" onClick={() => editOrder(o)}>Edit items</button>
                      <button className="btn small" onClick={() => act(`Order #${o.id} approved`, () => api(`/api/orders/${o.id}/approve`, { method: 'POST' }))}>Approve</button>
                      <button className="btn danger small" onClick={() => reject(o)}>Reject</button>
                    </>}
                    {o.status === 'APPROVED' && (
                      <button className="btn small" onClick={() => act(`Invoice created for order #${o.id}`, () => api(`/api/orders/${o.id}/invoice`, { method: 'POST' }))}>Create invoice</button>
                    )}
                    {o.status === 'INVOICED' && inv && (
                      <button className="btn small" onClick={() => act(`${inv.invoiceNo} dispatched`, () => api(`/api/invoices/${inv.id}/dispatch`, { method: 'POST' }))}>Dispatch</button>
                    )}
                    {inv && (
                      <button className="btn secondary small" onClick={() => download(`/api/invoices/${inv.id}/pdf`, `${inv.invoiceNo}.pdf`).catch((e) => setMsg({ error: e.message, ok: '' }))}>
                        PDF {inv.invoiceNo}
                      </button>
                    )}
                  </div>
                )
              },
            }] : []),
          ]} />
      </Card>
    </>
  )
}
