import { useState } from 'react'
import { api, money } from '../api.js'
import { Badge, Card, Field, Notice, Table, useLoad } from '../ui.jsx'

export default function Payments({ role }) {
  const invoices = useLoad(() => api('/api/invoices'))
  const payments = useLoad(() => api('/api/payments'))
  const [form, setForm] = useState({ invoiceId: '', amount: '', mode: 'UPI', reference: '' })
  const [msg, setMsg] = useState({ error: '', ok: '' })
  const canRecord = role === 'ADMIN' || role === 'ACCOUNTANT'

  const open = invoices.data?.filter((i) => Number(i.balance) > 0) || []
  const chosen = open.find((i) => String(i.id) === String(form.invoiceId))
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })

  async function submit(e) {
    e.preventDefault()
    setMsg({ error: '', ok: '' })
    try {
      const p = await api('/api/payments', {
        method: 'POST',
        body: { invoiceId: Number(form.invoiceId), amount: Number(form.amount), mode: form.mode, reference: form.reference || null },
      })
      setMsg({ error: '', ok: `Payment recorded. Balance on ${p.invoiceNo}: ${money(p.invoiceBalanceAfter)}` })
      setForm({ invoiceId: '', amount: '', mode: 'UPI', reference: '' })
      invoices.reload()
      payments.reload()
    } catch (err) {
      setMsg({ error: err.message, ok: '' })
    }
  }

  return (
    <>
      <h1 className="page-title">Payments</h1>
      {canRecord && (
        <Card title="Record a payment">
          <form onSubmit={submit}>
            <Notice error={msg.error || invoices.error} ok={msg.ok} />
            <div className="form-grid">
              <Field label="Invoice">
                <select value={form.invoiceId} onChange={set('invoiceId')} required>
                  <option value="">Select invoice…</option>
                  {open.map((i) => <option key={i.id} value={i.id}>{i.invoiceNo} · {i.customerName} · due {money(i.balance)}</option>)}
                </select>
              </Field>
              <Field label="Amount (₹)" hint={chosen ? `Outstanding: ${money(chosen.balance)}` : undefined}>
                <input type="number" min="0.01" step="0.01" max={chosen?.balance} value={form.amount} onChange={set('amount')} required />
              </Field>
              <Field label="Mode">
                <select value={form.mode} onChange={set('mode')}>
                  <option value="UPI">UPI</option><option value="CASH">Cash</option>
                  <option value="BANK_TRANSFER">Bank transfer</option><option value="CHEQUE">Cheque</option>
                </select>
              </Field>
              <Field label="Reference"><input value={form.reference} onChange={set('reference')} maxLength={60} /></Field>
              <button className="btn">Record payment</button>
            </div>
          </form>
        </Card>
      )}

      <Card title="Invoices">
        <Notice error={invoices.error} />
        <Table rows={invoices.data} empty="No invoices yet."
          columns={[
            { key: 'invoiceNo', label: 'Invoice' }, { key: 'invoiceDate', label: 'Date' }, { key: 'customerName', label: 'Customer' },
            { key: 'total', label: 'Total', align: 'right', render: (i) => money(i.total) },
            { key: 'paid', label: 'Paid', align: 'right', render: (i) => money(i.paid) },
            { key: 'balance', label: 'Balance', align: 'right', render: (i) => <strong>{money(i.balance)}</strong> },
            { key: 'state', label: '', render: (i) => (Number(i.balance) <= 0 ? <Badge tone="good">Paid</Badge> : Number(i.paid) > 0 ? <Badge tone="warn">Part paid</Badge> : <Badge tone="bad">Unpaid</Badge>) },
          ]} />
      </Card>

      <Card title="Payment history">
        <Notice error={payments.error} />
        <Table rows={payments.data} empty="No payments yet."
          columns={[
            { key: 'paymentDate', label: 'Date' }, { key: 'invoiceNo', label: 'Invoice' }, { key: 'customerName', label: 'Customer' },
            { key: 'amount', label: 'Amount', align: 'right', render: (p) => money(p.amount) },
            { key: 'mode', label: 'Mode' }, { key: 'reference', label: 'Reference' },
          ]} />
      </Card>
    </>
  )
}
