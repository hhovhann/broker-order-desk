let credentials: string | null = sessionStorage.getItem('cred')
let onUnauthorized: () => void = () => {}

export const setUnauthorizedHandler = (f: () => void) => { onUnauthorized = f }
export const hasCredentials = () => credentials !== null
export function signIn(user: string, pass: string) { credentials = btoa(`${user}:${pass}`); sessionStorage.setItem('cred', credentials) }
export function signOut() { credentials = null; sessionStorage.removeItem('cred') }

export interface Result<T> { status: number; data: T }

export async function call<T>(method: string, url: string, body?: unknown): Promise<Result<T>> {
  const res = await fetch(url, {
    method,
    headers: { ...(credentials ? { Authorization: `Basic ${credentials}` } : {}), ...(body ? { 'Content-Type': 'application/json' } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  })
  if (res.status === 401) onUnauthorized()
  let data: unknown = null
  try { data = await res.json() } catch { /* empty body */ }
  return { status: res.status, data: data as T }
}

export const get = async <T,>(url: string): Promise<T | null> => { const r = await call<T>('GET', url); return r.status === 200 ? r.data : null }
