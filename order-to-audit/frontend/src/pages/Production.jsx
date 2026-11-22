import { useEffect, useState } from 'react'
import { api, money, num } from '../api.js'
import { Badge, Card, Field, Notice, Table, useLoad } from '../ui.jsx'

const pct = (v) => `${Number(v) > 0 ? '+' : ''}${Number(v).toFixed(2)}%`

function RunResult({ run }) {
  return (
    <Card title={`Result: ${run.productName} (run #${run.id})`}
          actions={run.anyFlag ? <Badge tone="bad">Deviation flagged</Badge> : <Badge tone="good">Within tolerance</Badge>}>
      <div className="tiles" style={{ marginBottom: 12 }}>
        <div className="tile"><div className="tile-label">Expected packets</div><div className="tile-value">{run.expectedOutput}</div></div>
        <div className="tile"><div className="tile-label">Good packets</div><div className="tile-value">{run.goodOutput}</div></div>
        <div className={`tile ${run.yieldFlagged ? 'bad' : ''}`}><div className="tile-label">Yield variance</div><div className="tile-value">{pct(run.yieldVariancePct)}</div></div>
        <div className="tile"><div className="tile-label">Total cost</div><div className="tile-value">{money(run.totalCost)}</div></div>
        <div className="tile good"><div className="tile-label">Cost per packet</div><div className="tile-value">{money(run.costPerPacket)}</div></div>
      </div>
      <Table rowKey="materialId" rows={run.materials}
        columns={[
          { key: 'materialName', label: 'Material' },
          { key: 'standardQty', label: 'Standard', align: 'right', render: (m) => `${num(m.standardQty)} ${m.unit}` },
          { key: 'actualQty', label: 'Actual', align: 'right', render: (m) => `${num(m.actualQty)} ${m.unit}` },
          { key: 'variancePct', label: 'Variance', align: 'right', render: (m) => <span className={m.flagged ? 'flag' : ''}>{pct(m.variancePct)}</span> },
          { key: 'flagged', label: '', render: (m) => (m.flagged ? <Badge tone="bad">Flag</Badge> : null) },
        ]} />
    </Card>
  )
}

export default function Production() {
  const products = useLoad(() => api('/api/products'))
  const runs = useLoad(() => api('/api/production'))

  const [productId, setProductId] = useState('')
  const [batches, setBatches] = useState('1')
  const [plan, setPlan] = useState(null)
  const [actualQty, setActualQty] = useState({})
  const [form, setForm] = useState({ actualOutput: '', goodOutput: '', damagedUnits: '0', labourCost: '0', utilitiesCost: '0' })
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const product = products.data?.find((p) => String(p.id) === String(productId))

  // Whenever the product or batch count changes, ask the API what the recipe says.
  useEffect(() => {
    setPlan(null)
    if (!product || !product.bomId || !(Number(batches) > 0)) return
    let cancelled = false
    api(`/api/boms/${product.bomId}/plan?productId=${product.id}&batches=${batches}`)
      .then((p) => {
        if (cancelled) return
        setPlan(p)
        setActualQty(Object.fromEntries(p.materials.map((m) => [m.materialId, String(m.standardQty)])))
        setForm((f) => ({ ...f, actualOutput: String(p.expectedPackets), goodOutput: String(p.expectedPackets) }))
      })
      .catch((e) => !cancelled && setError(e.message))
    return () => { cancelled = true }
  }, [product?.id, product?.bomId, batches]) // eslint-disable-line react-hooks/exhaustive-deps

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })

  async function submit(e) {
    e.preventDefault()
    setError('')
    setBusy(true)
    try {
      const run = await api('/api/production', {
        method: 'POST',
        body: {
          productId: Number(productId), batches: Number(batches),
          actualOutput: Number(form.actualOutput), goodOutput: Number(form.goodOutput),
          damagedUnits: Number(form.damagedUnits || 0),
          labourCost: Number(form.labourCost || 0), utilitiesCost: Number(form.utilitiesCost || 0),
          materials: plan.materials.map((m) => ({ materialId: m.materialId, actualQty: Number(actualQty[m.materialId]) })),
        },
      })
      setResult(run)
      runs.reload()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <h1 className="page-title">Production</h1>
      <Card title="Record a production run">
        <form onSubmit={submit}>
          <Notice error={error || products.error} />
          <div className="form-grid">
            <Field label="Product">
              <select value={productId} onChange={(e) => { setProductId(e.target.value); setResult(null) }} required>
                <option value="">Select product…</option>
                {products.data?.filter((p) => p.bomId).map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
              </select>
            </Field>
            <Field label="Batches">
              <input type="number" min="0.01" step="0.01" value={batches} onChange={(e) => setBatches(e.target.value)} required />
            </Field>
          </div>

          {plan && (
            <>
              <p className="muted" style={{ margin: '12px 0 6px' }}>
                The recipe expects <strong>{plan.expectedPackets}</strong> packets of {plan.packSizeG} g.
                Enter what was really used (pre-filled with the standard).
              </p>
              <Table rowKey="materialId" rows={plan.materials}
                columns={[
                  { key: 'materialName', label: 'Material' },
                  { key: 'standardQty', label: 'Standard', align: 'right', render: (m) => `${num(m.standardQty)} ${m.unit}` },
                  { key: 'actual', label: 'Actual used', render: (m) => (
                    <input type="number" min="0" step="0.001" style={{ maxWidth: 140 }} value={actualQty[m.materialId] ?? ''}
                           onChange={(e) => setActualQty({ ...actualQty, [m.materialId]: e.target.value })} required />
                  ) },
                ]} />
              <div className="form-grid" style={{ marginTop: 12 }}>
                <Field label="Actual output (packets)"><input type="number" min="0" value={form.actualOutput} onChange={set('actualOutput')} required /></Field>
                <Field label="Good packets"><input type="number" min="0" value={form.goodOutput} onChange={set('goodOutput')} required /></Field>
                <Field label="Damaged units"><input type="number" min="0" value={form.damagedUnits} onChange={set('damagedUnits')} /></Field>
                <Field label="Labour cost (₹)"><input type="number" min="0" step="0.01" value={form.labourCost} onChange={set('labourCost')} /></Field>
                <Field label="Gas / power cost (₹)"><input type="number" min="0" step="0.01" value={form.utilitiesCost} onChange={set('utilitiesCost')} /></Field>
                <button className="btn" disabled={busy}>{busy ? 'Saving…' : 'Record run'}</button>
              </div>
            </>
          )}
          {product && !product.bomId && <p className="muted">This product has no recipe (BOM) linked yet.</p>}
        </form>
      </Card>

      {result && <RunResult run={result} />}

      <Card title="Recent runs">
        <Notice error={runs.error} />
        <Table rows={runs.data}
          columns={[
            { key: 'id', label: '#' },
            { key: 'runDate', label: 'Date' },
            { key: 'productName', label: 'Product' },
            { key: 'expectedOutput', label: 'Expected', align: 'right' },
            { key: 'goodOutput', label: 'Good', align: 'right' },
            { key: 'yieldVariancePct', label: 'Yield var.', align: 'right', render: (r) => <span className={r.yieldFlagged ? 'flag' : ''}>{pct(r.yieldVariancePct)}</span> },
            { key: 'costPerPacket', label: 'Cost / packet', align: 'right', render: (r) => money(r.costPerPacket) },
            { key: 'anyFlag', label: '', render: (r) => (r.anyFlag ? <Badge tone="bad">Flagged</Badge> : <Badge tone="good">OK</Badge>) },
          ]} />
      </Card>
    </>
  )
}
