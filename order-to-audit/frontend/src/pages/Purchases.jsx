import { useState } from 'react'
import { api, money, num } from '../api.js'
import { Card, Field, Notice, Table, useLoad } from '../ui.jsx'

const emptyLine = () => ({ materialId: '', quantity: '', unitPrice: '' })

export default function Purchases() {
  const suppliers = useLoad(() => api('/api/suppliers'))
  const materials = useLoad(() => api('/api/raw-materials'))
  const purchases = useLoad(() => api('/api/purchases'))

  const [supplierId, setSupplierId] = useState('')
  const [invoiceNo, setInvoiceNo] = useState('')
  const [date, setDate] = useState(() => new Date().toISOString().slice(0, 10))
  const [lines, setLines] = useState([emptyLine()])
  const [msg, setMsg] = useState({ error: '', ok: '' })
  const [busy, setBusy] = useState(false)

  const setLine = (i, patch) => setLines(lines.map((l, idx) => (idx === i ? { ...l, ...patch } : l)))
  const unitOf = (id) => materials.data?.find((m) => String(m.id) === String(id))?.unit
  const total = lines.reduce((s, l) => s + (Number(l.quantity) || 0) * (Number(l.unitPrice) || 0), 0)

  async function submit(e) {
    e.preventDefault()
    setBusy(true)
    setMsg({ error: '', ok: '' })
    try {
      await api('/api/purchases', {
        method: 'POST',
        body: {
          supplierId: Number(supplierId), invoiceNo: invoiceNo || null, purchaseDate: date,
          items: lines.map((l) => ({ materialId: Number(l.materialId), quantity: Number(l.quantity), unitPrice: Number(l.unitPrice) })),
        },
      })
      setSupplierId(''); setInvoiceNo(''); setLines([emptyLine()])
      setMsg({ error: '', ok: 'Purchase saved and stock updated.' })
      purchases.reload()
    } catch (err) {
      setMsg({ error: err.message, ok: '' })
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <h1 className="page-title">Purchases</h1>
      <Card title="Record a purchase">
        <form onSubmit={submit} className="line-items">
          <Notice error={msg.error || suppliers.error || materials.error} ok={msg.ok} />
          <div className="form-grid" style={{ marginBottom: 12 }}>
            <Field label="Supplier">
              <select value={supplierId} onChange={(e) => setSupplierId(e.target.value)} required>
                <option value="">Select supplier…</option>
                {suppliers.data?.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
              </select>
            </Field>
            <Field label="Supplier invoice no."><input value={invoiceNo} onChange={(e) => setInvoiceNo(e.target.value)} maxLength={40} /></Field>
            <Field label="Date"><input type="date" value={date} onChange={(e) => setDate(e.target.value)} required /></Field>
          </div>
          {lines.map((l, i) => (
            <div className="row" key={i}>
              <Field label="Material">
                <select value={l.materialId} onChange={(e) => setLine(i, { materialId: e.target.value })} required>
                  <option value="">Select material…</option>
                  {materials.data?.map((m) => <option key={m.id} value={m.id}>{m.name} ({m.unit})</option>)}
                </select>
              </Field>
              <Field label={`Quantity${unitOf(l.materialId) ? ` (${unitOf(l.materialId)})` : ''}`}>
                <input type="number" min="0.001" step="0.001" value={l.quantity} onChange={(e) => setLine(i, { quantity: e.target.value })} required />
              </Field>
              <Field label="Price per unit (₹)">
                <input type="number" min="0" step="0.01" value={l.unitPrice} onChange={(e) => setLine(i, { unitPrice: e.target.value })} required />
              </Field>
              <button type="button" className="btn danger small" disabled={lines.length === 1}
                      onClick={() => setLines(lines.filter((_, idx) => idx !== i))}>Remove</button>
            </div>
          ))}
          <div className="row" style={{ justifyContent: 'space-between', marginTop: 8 }}>
            <button type="button" className="btn secondary small" onClick={() => setLines([...lines, emptyLine()])}>+ Add material</button>
            <span>Total: <strong>{money(total)}</strong></span>
            <button className="btn" disabled={busy}>{busy ? 'Saving…' : 'Save purchase'}</button>
          </div>
        </form>
      </Card>

      <Card title="Purchase history">
        <Notice error={purchases.error} />
        <Table rows={purchases.data}
          columns={[
            { key: 'id', label: '#' },
            { key: 'purchaseDate', label: 'Date' },
            { key: 'supplierName', label: 'Supplier' },
            { key: 'invoiceNo', label: 'Invoice' },
            { key: 'items', label: 'Items', render: (p) => p.items.map((i) => `${num(i.quantity)} ${i.unit} ${i.materialName}`).join(', ') },
            { key: 'totalAmount', label: 'Total', align: 'right', render: (p) => money(p.totalAmount) },
          ]} />
      </Card>
    </>
  )
}
