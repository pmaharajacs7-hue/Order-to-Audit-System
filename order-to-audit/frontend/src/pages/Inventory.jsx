import { useState } from 'react'
import { api, money, num } from '../api.js'
import { Badge, Card, Field, Notice, Table, useLoad } from '../ui.jsx'

const LABELS = { RAW: 'Raw material', PACKAGING: 'Packaging', FINISHED_GOOD: 'Finished goods' }

function Adjust({ stock, onDone }) {
  const [f, setF] = useState({ item: '', movementType: 'ADJUSTMENT', quantity: '', unitCost: '', notes: '' })
  const [msg, setMsg] = useState({ error: '', ok: '' })
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value })

  async function submit(e) {
    e.preventDefault()
    setMsg({ error: '', ok: '' })
    const [itemType, itemId] = f.item.split(':')
    try {
      await api('/api/inventory/adjustments', {
        method: 'POST',
        body: {
          itemType, itemId: Number(itemId), movementType: f.movementType, quantity: Number(f.quantity),
          unitCost: f.unitCost === '' ? null : Number(f.unitCost), notes: f.notes || null,
        },
      })
      setMsg({ error: '', ok: 'Recorded in the stock ledger.' })
      setF({ ...f, quantity: '', unitCost: '', notes: '' })
      onDone()
    } catch (err) {
      setMsg({ error: err.message, ok: '' })
    }
  }

  return (
    <Card title="Opening stock / correction (admin)">
      <form onSubmit={submit}>
        <Notice error={msg.error} ok={msg.ok} />
        <div className="form-grid">
          <Field label="Item">
            <select value={f.item} onChange={set('item')} required>
              <option value="">Select item…</option>
              {stock.map((s) => <option key={`${s.itemType}:${s.itemId}`} value={`${s.itemType}:${s.itemId}`}>{s.name}</option>)}
            </select>
          </Field>
          <Field label="Type">
            <select value={f.movementType} onChange={set('movementType')}>
              <option value="ADJUSTMENT">Correction (+ or −)</option>
              <option value="OPENING">Opening stock</option>
            </select>
          </Field>
          <Field label="Quantity" hint="Use a minus sign to reduce stock"><input type="number" step="0.001" value={f.quantity} onChange={set('quantity')} required /></Field>
          <Field label="Unit cost (₹)" hint="Required for opening stock"><input type="number" step="0.01" min="0" value={f.unitCost} onChange={set('unitCost')} required={f.movementType === 'OPENING'} /></Field>
          <Field label="Reason"><input value={f.notes} onChange={set('notes')} maxLength={255} /></Field>
          <button className="btn">Record</button>
        </div>
      </form>
    </Card>
  )
}

export default function Inventory({ role }) {
  const stock = useLoad(() => api('/api/inventory/stock'))
  const [selected, setSelected] = useState(null)
  const ledger = useLoad(
    () => (selected ? api(`/api/inventory/ledger?itemType=${selected.itemType}&itemId=${selected.itemId}`) : Promise.resolve(null)),
    [selected?.itemType, selected?.itemId])

  const groups = ['RAW', 'PACKAGING', 'FINISHED_GOOD']

  return (
    <>
      <h1 className="page-title">Inventory</h1>
      <Notice error={stock.error} />
      {groups.map((g) => {
        const rows = stock.data?.filter((s) => s.category === g)
        if (!rows || rows.length === 0) return null
        return (
          <Card key={g} title={LABELS[g]}>
            <Table rowKey="itemId" rows={rows} onRowClick={setSelected}
              columns={[
                { key: 'name', label: 'Item' },
                { key: 'balance', label: 'In stock', align: 'right', render: (s) => `${num(s.balance)} ${s.unit}` },
                ...(g !== 'FINISHED_GOOD' ? [{ key: 'reorderLevel', label: 'Reorder level', align: 'right', render: (s) => num(s.reorderLevel) }] : []),
                { key: 'flag', label: '', render: (s) => (s.belowReorder ? <Badge tone="bad">Low stock</Badge> : null) },
              ]} />
          </Card>
        )
      })}
      <p className="muted">Click any item to see its full stock ledger.</p>

      {selected && (
        <Card title={`Stock ledger: ${selected.name}`} actions={<button className="btn secondary small" onClick={() => setSelected(null)}>Close</button>}>
          <Notice error={ledger.error} />
          <Table rows={ledger.data} empty="No movements yet."
            columns={[
              { key: 'createdAt', label: 'When', render: (l) => (l.createdAt || '').replace('T', ' ').slice(0, 16) },
              { key: 'movementType', label: 'Type' },
              { key: 'quantity', label: 'Change', align: 'right', render: (l) => <span className={Number(l.quantity) < 0 ? 'flag' : ''}>{Number(l.quantity) > 0 ? '+' : ''}{num(l.quantity)}</span> },
              { key: 'balanceAfter', label: 'Balance', align: 'right', render: (l) => num(l.balanceAfter) },
              { key: 'unitCost', label: 'Unit cost', align: 'right', render: (l) => (l.unitCost == null ? '' : money(l.unitCost)) },
              { key: 'ref', label: 'Reference', render: (l) => (l.referenceType ? `${l.referenceType} #${l.referenceId ?? ''}` : l.notes || '') },
            ]} />
        </Card>
      )}

      {role === 'ADMIN' && stock.data && <Adjust stock={stock.data} onDone={() => { stock.reload(); if (selected) ledger.reload() }} />}
    </>
  )
}
