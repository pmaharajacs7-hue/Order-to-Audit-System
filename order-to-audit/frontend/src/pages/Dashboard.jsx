import { api, money } from '../api.js'
import { Card, Notice, Table, Tile, useLoad } from '../ui.jsx'

export default function Dashboard() {
  const dash = useLoad(() => api('/api/reports/dashboard'))
  const owing = useLoad(() => api('/api/reports/outstanding'))
  const d = dash.data

  return (
    <>
      <h1 className="page-title">Business overview</h1>
      <Notice error={dash.error || owing.error} />
      {d && (
        <div className="tiles">
          <Tile label="Invoiced" value={money(d.salesInvoiced)} />
          <Tile label="Collected" value={money(d.collected)} tone="good" />
          <Tile label="Outstanding" value={money(d.outstanding)} tone={Number(d.outstanding) > 0 ? 'warn' : ''} />
          <Tile label="Purchases" value={money(d.purchasesTotal)} />
          <Tile label="Low-stock materials" value={d.lowStockItems} tone={d.lowStockItems > 0 ? 'bad' : ''} />
          <Tile label="Flagged production runs" value={d.flaggedProductionRuns} tone={d.flaggedProductionRuns > 0 ? 'warn' : ''} />
          <Tile label="Orders awaiting approval" value={d.ordersAwaitingApproval} tone={d.ordersAwaitingApproval > 0 ? 'warn' : ''} />
        </div>
      )}
      <Card title="Customers who still owe money">
        <Table rowKey="customerId" rows={owing.data} empty="No pending payments."
          columns={[
            { key: 'customerName', label: 'Customer' },
            { key: 'invoiced', label: 'Invoiced', align: 'right', render: (r) => money(r.invoiced) },
            { key: 'paid', label: 'Paid', align: 'right', render: (r) => money(r.paid) },
            { key: 'outstanding', label: 'Outstanding', align: 'right', render: (r) => <strong>{money(r.outstanding)}</strong> },
            { key: 'openInvoices', label: 'Open invoices', align: 'right' },
          ]} />
      </Card>
    </>
  )
}
