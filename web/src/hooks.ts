import { useEffect, useRef, useState } from 'react'
import { useLive } from './live'

/** Polls an async loader; keeps the last good value while a refresh is in flight or fails. */
export function usePoll<T>(load: () => Promise<T | null>, deps: unknown[], ms = 1000, enabled = true): [T | null, () => void] {
  const [data, setData] = useState<T | null>(null)
  const [tick, setTick] = useState(0)
  const live = useLive()
  const every = live.connected ? Math.max(ms, 5000) : ms   // the stream pushes changes; polling is only a safety net
  useEffect(() => {
    if (!enabled) return
    let alive = true
    const run = async () => { try { const v = await load(); if (alive && v !== null) setData(v) } catch { /* keep last value */ } }
    run()
    const id = setInterval(run, every)
    return () => { alive = false; clearInterval(id) }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, every, enabled, tick, live.version])
  return [data, () => setTick(t => t + 1)]
}

/** True for a moment after the value changes: drives the price flash. */
export function useFlash(value: number): 'up' | 'down' | '' {
  const prev = useRef(value)
  const [dir, setDir] = useState<'up' | 'down' | ''>('')
  useEffect(() => {
    if (value === prev.current) return
    setDir(value > prev.current ? 'up' : 'down'); prev.current = value
    const t = setTimeout(() => setDir(''), 900)
    return () => clearTimeout(t)
  }, [value])
  return dir
}
