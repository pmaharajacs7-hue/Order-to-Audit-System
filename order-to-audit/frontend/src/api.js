// Thin wrapper around fetch: adds the JWT, turns {"error": "..."} responses into Errors.

const KEY = 'ota.session'
let session = null

function safeStorage(fn) {
  try { return fn() } catch { return null }
}

export function loadSession() {
  const raw = safeStorage(() => window.sessionStorage.getItem(KEY))
  session = raw ? JSON.parse(raw) : null
  return session
}

export function saveSession(s) {
  session = s
  safeStorage(() => window.sessionStorage.setItem(KEY, JSON.stringify(s)))
}

export function clearSession() {
  session = null
  safeStorage(() => window.sessionStorage.removeItem(KEY))
}

let onUnauthorized = () => {}
export function setUnauthorizedHandler(fn) { onUnauthorized = fn }

async function request(path, { method = 'GET', body } = {}) {
  const headers = {}
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (session?.token) headers['Authorization'] = `Bearer ${session.token}`
  const res = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) })
  if (res.status === 401 && !path.endsWith('/auth/login')) {
    onUnauthorized()
    throw new Error('Session expired. Please log in again.')
  }
  if (!res.ok) {
    let message = `Request failed (${res.status})`
    if (res.status === 403) message = 'You do not have permission to do that.'
    try {
      const data = await res.clone().json()
      if (data?.error) message = data.error
    } catch { /* body was not JSON */ }
    throw new Error(message)
  }
  return res
}

export async function api(path, options) {
  const res = await request(path, options)
  if (res.status === 204) return null
  const text = await res.text()
  return text ? JSON.parse(text) : null
}

/** Downloads a PDF/CSV with the auth header and saves it through the browser. */
export async function download(path, filename) {
  const res = await request(path)
  const blob = await res.blob()
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}

export const money = (v) =>
  v === null || v === undefined || v === '' ? '' :
  new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' }).format(Number(v))

export const num = (v, digits = 3) =>
  v === null || v === undefined || v === '' ? '' :
  new Intl.NumberFormat('en-IN', { maximumFractionDigits: digits }).format(Number(v))
