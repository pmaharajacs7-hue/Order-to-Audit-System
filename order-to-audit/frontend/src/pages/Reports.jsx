import { useState } from 'react'
import { api, download, money, num } from '../api.js'
import { Badge, Card, Field, Notice, Table, useLoad } from '../ui.jsx'

const M = (k) => (r) => money(r[k])

// Each report: API path, whether it takes a date range, and how to show its columns.
const REPORTS = {
  outstanding: {
    label: 'Outstanding payments', path: '/api/reports/outstanding', dates: false, rowKey: 'customerId',
    columns: [
      { key: 'customerName', label: 'Customer' },
      { key: 'invoiced', label: 'Invoiced', align: 'right', render: M('invoiced') },
      { key: 'paid', label: 'Paid', align: 'right', render: M('paid') },
      { key: 'outstanding', label: 'Outstanding', align: 'right', render: M('outstanding') },
      { key: 'openInvoices', label: 'Open invoices', align: 'right' },
    ],
  },
  gst: {
    label: 'GST summary', path: '/api/reports/gst', dates: true, rowKey: 'period',
    columns: [
      { key: 'period', label: 'Month' },
      { key: 'taxableValue', label: 'Taxable value', align: 'right', render: M('taxableValue') },
      { key: 'cgst', label: 'CGST', align: 'right', render: M('cgst') },
      { key: 'sgst', label: 'SGST', align: 'right', render: M('sgst') },
      { key: 'igst', label: 'IGST', align: 'right', render: M('igst') },
      { key: 'totalTax', label: 'Total tax', align: 'right', render: M('totalTax') },
      { key: 'invoiceTotal', label: 'Invoice total', align: 'right', render: M('invoiceTotal') },
    ],
  },
  sales: {
    label: 'Sales by product', path: '/api/reports/sales', dates: true, rowKey: 'productId',
    columns: [
      { key: 'productName', label: 'Product' },
      { key: 'packets', label: 'Packets', align: 'right' },
      { key: 'taxableValue', label: 'Taxable value', align: 'right', render: M('taxableValue') },
      { key: 'invoiceValue', label: 'Invoice value', align: 'right', render: M('invoiceValue') },
    ],
  },
  purchases: {
    label: 'Purchases by material', path: '/api/reports/purchases', dates: true, rowKey: 'materialId',
    columns: [
      { key: 'materialName', label: 'Material' },
      { key: 'quantity', label: 'Quantity', align: 'right', render: (r) => `${num(r.quantity)} ${r.unit}` },
      { key: 'amount', label: 'Amount', align: 'right', render: M('amount') },
      { key: 'avgPrice', label: 'Avg price', align: 'right', render: M('avgPrice') },
    ],
  },
  production: {
    label: 'Production and wastage', path: '/api/reports/production', dates: true, rowKey: 'runId',
    columns: [
      { key: 'runDate', label: 'Date' }, { key: 'productName', label: 'Product' },
      { key: 'expected', label: 'Expected', align: 'right' }, { key: 'good', label: 'Good', align: 'right' },
      { key: 'yieldVariancePct', label: 'Yield var. %', align: 'right' },
      { key: 'totalCost', label: 'Total cost', align: 'right', render: M('totalCost') },
      { key: 'costPerPacket', label: 'Cost / packet', align: 'right', render: M('costPerPacket') },
      { key: 'flagged', label: '', render: (r) => (r.flagged ? <Badge tone="bad">Flagged</Badge> : null) },
    ],
  },
  audit: {
    label: 'Audit trail', path: '/api/audit', dates: false, csv: false, rowKey: 'id',
    columns: [
      { key: 'createdAt', label: 'When', render: (r) => (r.createdAt || '').replace('T', ' ').slice(0, 19) },
      { key: 'username', label: 'User' },
      { key: 'action', label: 'Action' },
      { key: 'entityType', label: 'Record', render: (r) => `${r.entityType} #${r.entityId ?? ''}` },
      { key: 'details', label: 'Details' },
    ],
  },
  profitability: {
    label: 'Cost vs selling price', path: '/api/reports/profitability', dates: false, rowKey: 'productId',
    columns: [
      { key: 'productName', label: 'Product' },
      { key: 'sellingPrice', label: 'Selling price', align: 'right', render: M('sellingPrice') },
      { key: 'costPerPacket', label: 'Cost / packet', align: 'right', render: M('costPerPacket') },
      { key: 'marginPerPacket', label: 'Margin / packet', align: 'right', render: M('marginPerPacket') },
      { key: 'marginPct', label: 'Margin %', align: 'right' },
    ],
  },
}

export default function Reports() {
  const [key, setKey] = useState('outstanding')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [error, setError] = useState('')
  const report = REPORTS[key]

  const query = (extra = '') => {
    const p = new URLSearchParams()
    if (report.dates && from) p.set('from', from)
    if (report.dates && to) p.set('to', to)
    const s = p.toString()
    return `${report.path}?${s}${s && extra ? '&' : ''}${extra}`
  }

  const data = useLoad(() => api(query()), [key, from, to])

  return (
    <>
      <h1 className="page-title">Reports</h1>
      <div className="tabs">
        {Object.entries(REPORTS).map(([k, r]) => (
          <button key={k} className={`btn ${k === key ? '' : 'secondary'} small`} onClick={() => setKey(k)}>{r.label}</button>
        ))}
      </div>
      <Card title={report.label}
            actions={report.csv === false ? null : (
              <button className="btn secondary small"
                      onClick={() => download(query('format=csv'), `${key}.csv`).catch((e) => setError(e.message))}>
                Download CSV
              </button>)}>
        {report.dates && (
          <div className="form-grid" style={{ marginBottom: 12, maxWidth: 480 }}>
            <Field label="From"><input type="date" value={from} onChange={(e) => setFrom(e.target.value)} /></Field>
            <Field label="To"><input type="date" value={to} onChange={(e) => setTo(e.target.value)} /></Field>
          </div>
        )}
        <Notice error={error || data.error} />
        <Table rows={data.data} rowKey={report.rowKey} columns={report.columns} empty="No data for this report yet." />
      </Card>
    </>
  )
}
