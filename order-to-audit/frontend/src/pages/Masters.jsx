import { useState } from 'react'
import { api, money, num } from '../api.js'
import { Card, Field, Notice, Table, useLoad } from '../ui.jsx'

const blankToNull = (v) => (v === '' || v === undefined ? null : v)

/** Generic create + list + deactivate screen driven by a small config object. */
function Crud({ cfg }) {
  const list = useLoad(() => api(cfg.path), [cfg.path])
  const options = useLoad(() => (cfg.optionsPath ? api(cfg.optionsPath) : Promise.resolve(null)), [cfg.optionsPath])
  const initial = Object.fromEntries(cfg.fields.map((f) => [f.key, f.default ?? '']))
  const [form, setForm] = useState(initial)
  const [msg, setMsg] = useState({ error: '', ok: '' })

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })

  async function submit(e) {
    e.preventDefault()
    setMsg({ error: '', ok: '' })
    try {
      await api(cfg.path, { method: 'POST', body: cfg.body(form) })
      setForm(initial)
      setMsg({ error: '', ok: `${cfg.singular} added.` })
      list.reload()
    } catch (err) {
      setMsg({ error: err.message, ok: '' })
    }
  }

  async function deactivate(row) {
    if (!window.confirm(`Deactivate "${row.name}"? Past records keep pointing at it.`)) return
    setMsg({ error: '', ok: '' })
    try {
      await api(`${cfg.path}/${row.id}`, { method: 'DELETE' })
      list.reload()
    } catch (err) {
      setMsg({ error: err.message, ok: '' })
    }
  }

  async function editPrice(row) {
    const price = window.prompt(`New price per packet for ${row.name}:`, row.unitPrice)
    if (price === null) return
    const gst = window.prompt('GST rate in total % (e.g. 5 = 2.5% CGST + 2.5% SGST):', row.gstRatePct)
    if (gst === null) return
    setMsg({ error: '', ok: '' })
    try {
      await api(`${cfg.path}/${row.id}`, {
        method: 'PUT',
        body: { name: row.name, sku: row.sku, packSizeG: row.packSizeG, unitPrice: Number(price), hsnCode: row.hsnCode,
                gstRatePct: Number(gst), bomId: row.bomId },
      })
      setMsg({ error: '', ok: 'Product updated (the change is recorded in the audit trail).' })
      list.reload()
    } catch (err) {
      setMsg({ error: err.message, ok: '' })
    }
  }

  return (
    <>
      <Card title={`Add ${cfg.singular.toLowerCase()}`}>
        <form onSubmit={submit}>
          <Notice error={msg.error || options.error} ok={msg.ok} />
          <div className="form-grid">
            {cfg.fields.map((f) => (
              <Field key={f.key} label={f.label} hint={f.hint}>
                {f.type === 'select' ? (
                  <select value={form[f.key]} onChange={set(f.key)} required={f.required}>
                    {!f.required && <option value="">None</option>}
                    {(f.choices || (options.data || []).map((o) => ({ value: o.id, label: o.name }))).map((c) => (
                      <option key={c.value} value={c.value}>{c.label}</option>
                    ))}
                  </select>
                ) : (
                  <input type={f.type || 'text'} step={f.step} min={f.min} value={form[f.key]} onChange={set(f.key)} required={f.required} />
                )}
              </Field>
            ))}
            <button className="btn">Add</button>
          </div>
        </form>
      </Card>
      <Card title={cfg.title}>
        <Table rows={list.data}
          columns={[...cfg.columns, {
            key: 'actions', label: '', render: (r) => (
              <div className="row">
                {cfg.editPrice && <button className="btn secondary small" onClick={() => editPrice(r)}>Edit price / GST</button>}
                <button className="btn danger small" onClick={() => deactivate(r)}>Deactivate</button>
              </div>
            ),
          }]} />
      </Card>
    </>
  )
}

const CONFIGS = {
  customers: {
    title: 'Customers', singular: 'Customer', path: '/api/customers',
    fields: [
      { key: 'name', label: 'Name', required: true },
      { key: 'gstin', label: 'GSTIN', hint: '15 characters, optional' },
      { key: 'phone', label: 'Phone' },
      { key: 'address', label: 'Address' },
      { key: 'stateCode', label: 'GST state code', default: '33', required: true, hint: '33 = Tamil Nadu. A different state means IGST.' },
    ],
    body: (f) => ({ name: f.name, gstin: blankToNull(f.gstin), phone: blankToNull(f.phone), address: blankToNull(f.address), stateCode: f.stateCode }),
    columns: [
      { key: 'name', label: 'Name' }, { key: 'gstin', label: 'GSTIN' }, { key: 'phone', label: 'Phone' },
      { key: 'stateCode', label: 'State' },
    ],
  },
  suppliers: {
    title: 'Suppliers', singular: 'Supplier', path: '/api/suppliers',
    fields: [
      { key: 'name', label: 'Name', required: true },
      { key: 'gstin', label: 'GSTIN' },
      { key: 'phone', label: 'Phone' },
      { key: 'address', label: 'Address' },
    ],
    body: (f) => ({ name: f.name, gstin: blankToNull(f.gstin), phone: blankToNull(f.phone), address: blankToNull(f.address) }),
    columns: [{ key: 'name', label: 'Name' }, { key: 'gstin', label: 'GSTIN' }, { key: 'phone', label: 'Phone' }],
  },
  materials: {
    title: 'Raw materials and packaging', singular: 'Material', path: '/api/raw-materials',
    fields: [
      { key: 'name', label: 'Name', required: true },
      { key: 'category', label: 'Category', type: 'select', required: true, default: 'RAW',
        choices: [{ value: 'RAW', label: 'Raw material' }, { value: 'PACKAGING', label: 'Packaging' }] },
      { key: 'unit', label: 'Unit', required: true, hint: 'kg, litre, piece' },
      { key: 'reorderLevel', label: 'Reorder level', type: 'number', step: '0.001', min: '0', default: '0' },
    ],
    body: (f) => ({ name: f.name, category: f.category, unit: f.unit, reorderLevel: Number(f.reorderLevel || 0) }),
    columns: [
      { key: 'name', label: 'Name' }, { key: 'category', label: 'Category' }, { key: 'unit', label: 'Unit' },
      { key: 'reorderLevel', label: 'Reorder level', align: 'right', render: (m) => num(m.reorderLevel) },
    ],
  },
  products: {
    title: 'Products', singular: 'Product', path: '/api/products', optionsPath: '/api/boms', editPrice: true,
    fields: [
      { key: 'name', label: 'Name', required: true },
      { key: 'sku', label: 'SKU', required: true },
      { key: 'packSizeG', label: 'Pack size (g)', type: 'number', min: '1', required: true },
      { key: 'unitPrice', label: 'Price per packet (₹)', type: 'number', step: '0.01', min: '0', required: true },
      { key: 'hsnCode', label: 'HSN code', required: true, default: '19059090' },
      { key: 'gstRatePct', label: 'GST rate (total %)', type: 'number', step: '0.01', min: '0', default: '5', required: true, hint: '5 = 2.5% CGST + 2.5% SGST' },
      { key: 'bomId', label: 'Recipe (BOM)', type: 'select', hint: 'Several pack sizes can share one recipe' },
    ],
    body: (f) => ({
      name: f.name, sku: f.sku, packSizeG: Number(f.packSizeG), unitPrice: Number(f.unitPrice), hsnCode: f.hsnCode,
      gstRatePct: Number(f.gstRatePct), bomId: f.bomId === '' ? null : Number(f.bomId),
    }),
    columns: [
      { key: 'name', label: 'Name' }, { key: 'sku', label: 'SKU' },
      { key: 'packSizeG', label: 'Pack', align: 'right', render: (p) => `${p.packSizeG} g` },
      { key: 'unitPrice', label: 'Price', align: 'right', render: (p) => money(p.unitPrice) },
      { key: 'gstRatePct', label: 'GST %', align: 'right' }, { key: 'hsnCode', label: 'HSN' },
      { key: 'bomId', label: 'BOM #' },
    ],
  },
}

const blankItem = () => ({ materialId: '', quantity: '', basis: 'PER_BATCH', tolerancePct: '3' })

function Recipes() {
  const boms = useLoad(() => api('/api/boms'))
  const materials = useLoad(() => api('/api/raw-materials'))
  const [name, setName] = useState('')
  const [batchOutputKg, setBatchOutputKg] = useState('')
  const [items, setItems] = useState([blankItem()])
  const [msg, setMsg] = useState({ error: '', ok: '' })

  const setItem = (i, patch) => setItems(items.map((it, idx) => (idx === i ? { ...it, ...patch } : it)))

  async function submit(e) {
    e.preventDefault()
    setMsg({ error: '', ok: '' })
    try {
      await api('/api/boms', {
        method: 'POST',
        body: {
          name, batchOutputKg: Number(batchOutputKg),
          items: items.map((it) => ({ materialId: Number(it.materialId), quantity: Number(it.quantity), basis: it.basis, tolerancePct: Number(it.tolerancePct) })),
        },
      })
      setName(''); setBatchOutputKg(''); setItems([blankItem()])
      setMsg({ error: '', ok: 'Recipe saved.' })
      boms.reload()
    } catch (err) {
      setMsg({ error: err.message, ok: '' })
    }
  }

  return (
    <>
      <Card title="Add recipe (BOM)">
        <form onSubmit={submit} className="line-items">
          <Notice error={msg.error || materials.error} ok={msg.ok} />
          <div className="form-grid" style={{ marginBottom: 12 }}>
            <Field label="Recipe name"><input value={name} onChange={(e) => setName(e.target.value)} required /></Field>
            <Field label="Finished snack per batch (kg)" hint="Output is measured by weight, so any pack size can share this recipe">
              <input type="number" min="0.001" step="0.001" value={batchOutputKg} onChange={(e) => setBatchOutputKg(e.target.value)} required />
            </Field>
          </div>
          {items.map((it, i) => (
            <div className="row" key={i}>
              <Field label="Material">
                <select value={it.materialId} onChange={(e) => setItem(i, { materialId: e.target.value })} required>
                  <option value="">Select…</option>
                  {materials.data?.map((m) => <option key={m.id} value={m.id}>{m.name} ({m.unit})</option>)}
                </select>
              </Field>
              <Field label="Quantity"><input type="number" min="0.0001" step="0.0001" value={it.quantity} onChange={(e) => setItem(i, { quantity: e.target.value })} required /></Field>
              <Field label="Basis">
                <select value={it.basis} onChange={(e) => setItem(i, { basis: e.target.value })}>
                  <option value="PER_BATCH">Per batch</option>
                  <option value="PER_PACKET">Per packet</option>
                </select>
              </Field>
              <Field label="Tolerance %"><input type="number" min="0" max="100" step="0.01" value={it.tolerancePct} onChange={(e) => setItem(i, { tolerancePct: e.target.value })} /></Field>
              <button type="button" className="btn danger small" disabled={items.length === 1} onClick={() => setItems(items.filter((_, idx) => idx !== i))}>Remove</button>
            </div>
          ))}
          <div className="row" style={{ justifyContent: 'space-between', marginTop: 8 }}>
            <button type="button" className="btn secondary small" onClick={() => setItems([...items, blankItem()])}>+ Add material</button>
            <button className="btn">Save recipe</button>
          </div>
        </form>
      </Card>
      <Card title="Recipes">
        <Notice error={boms.error} />
        <Table rows={boms.data}
          columns={[
            { key: 'id', label: '#' }, { key: 'name', label: 'Recipe' },
            { key: 'batchOutputKg', label: 'Output / batch', align: 'right', render: (b) => `${num(b.batchOutputKg)} kg` },
            { key: 'items', label: 'Materials', render: (b) => b.items.map((i) => `${i.materialName} ${num(i.quantity)} ${i.unit}${i.basis === 'PER_PACKET' ? '/pkt' : ''}`).join(', ') },
          ]} />
      </Card>
    </>
  )
}

const TABS = [['customers', 'Customers'], ['suppliers', 'Suppliers'], ['materials', 'Materials'], ['recipes', 'Recipes (BOM)'], ['products', 'Products']]

export default function Masters() {
  const [tab, setTab] = useState('customers')
  return (
    <>
      <h1 className="page-title">Master data</h1>
      <div className="tabs">
        {TABS.map(([key, label]) => (
          <button key={key} className={`btn ${tab === key ? '' : 'secondary'} small`} onClick={() => setTab(key)}>{label}</button>
        ))}
      </div>
      {tab === 'recipes' ? <Recipes /> : <Crud key={tab} cfg={CONFIGS[tab]} />}
    </>
  )
}
