import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from 'react'
import { authHeader } from './api'

interface Live { version: number; connected: boolean }
const C = createContext<Live>({ version: 0, connected: false })
export const useLive = () => useContext(C)

/**
 * Server-sent events over fetch (EventSource cannot send an Authorization header). Each "market" event bumps `version`, so every poller refetches at once.
 * If the stream drops, pollers fall back to their normal interval and the stream reconnects with backoff.
 */
export function LiveProvider({ children, onAlert }: { children: ReactNode; onAlert: (text: string) => void }) {
  const [version, setVersion] = useState(0), [connected, setConnected] = useState(false)
  const alertRef = useRef(onAlert); alertRef.current = onAlert
  useEffect(() => {
    let stop = false, delay = 1000, ctl: AbortController | null = null
    const run = async () => {
      while (!stop) {
        ctl = new AbortController()
        try {
          const res = await fetch('/api/v1/stream', { headers: { ...authHeader(), Accept: 'text/event-stream' }, signal: ctl.signal })
          if (!res.ok || !res.body) throw new Error('stream refused')
          setConnected(true); delay = 1000
          const reader = res.body.getReader(), dec = new TextDecoder()
          let buf = '', event = ''
          for (;;) {
            const { done, value } = await reader.read()
            if (done) break
            buf += dec.decode(value, { stream: true })
            let i: number
            while ((i = buf.indexOf('\n')) >= 0) {
              const line = buf.slice(0, i).replace(/\r$/, ''); buf = buf.slice(i + 1)
              if (line.startsWith('event:')) event = line.slice(6).trim()
              else if (line.startsWith('data:')) { if (event === 'market') setVersion(v => v + 1); else if (event === 'alert') alertRef.current(line.slice(5).trim()) }
            }
          }
        } catch { /* fall through to retry */ }
        setConnected(false)
        if (stop) return
        await new Promise(r => setTimeout(r, delay)); delay = Math.min(delay * 2, 15000)
      }
    }
    void run()
    return () => { stop = true; ctl?.abort() }
  }, [])
  return <C.Provider value={{ version, connected }}>{children}</C.Provider>
}
