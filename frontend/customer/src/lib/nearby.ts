import { useEffect, useMemo, useState, useSyncExternalStore } from 'react'
import type { Permit } from '../api/types'

/**
 * "البطاقة التكيفية" (the adaptive card): one card that becomes the card of the place you are at.
 * When it is on, the phone watches its own location and finds the nearest place you have a key or membership for.
 * Everything happens ON THE PHONE: the location is never sent to VORNX.
 *
 * Demo mode (for presentations): pick a place and the app behaves as if you were standing there.
 */

/** Within this distance the card switches to that place. */
export const NEAR_METERS = 500

const ON_KEY = 'vornx.customer.adaptive'
const DEMO_KEY = 'vornx.customer.adaptiveDemo'
const EVENT = 'vornx-adaptive'

const get = (k: string) => { try { return localStorage.getItem(k) } catch { return null } }
const put = (k: string, v: string | null) => {
  try { if (v === null) localStorage.removeItem(k); else localStorage.setItem(k, v) } catch { /* not remembered */ }
  window.dispatchEvent(new Event(EVENT))
}
const subscribe = (cb: () => void) => {
  window.addEventListener(EVENT, cb)
  window.addEventListener('storage', cb)
  return () => { window.removeEventListener(EVENT, cb); window.removeEventListener('storage', cb) }
}

export function useAdaptiveSettings() {
  const on = useSyncExternalStore(subscribe, () => get(ON_KEY) === '1', () => false)
  const demoPermitId = useSyncExternalStore(subscribe, () => get(DEMO_KEY), () => null)
  return {
    on,
    demoPermitId: demoPermitId ? Number(demoPermitId) : null,
    setOn: (v: boolean) => { put(ON_KEY, v ? '1' : null); if (!v) put(DEMO_KEY, null) },
    setDemo: (id: number | null) => put(DEMO_KEY, id === null ? null : String(id)),
  }
}

/** Great-circle distance in meters. */
function meters(lat1: number, lng1: number, lat2: number, lng2: number) {
  const R = 6371000, r = Math.PI / 180
  const a = Math.sin(((lat2 - lat1) * r) / 2) ** 2 + Math.cos(lat1 * r) * Math.cos(lat2 * r) * Math.sin(((lng2 - lng1) * r) / 2) ** 2
  return 2 * R * Math.asin(Math.sqrt(a))
}

export type GpsState = 'off' | 'asking' | 'on' | 'denied' | 'unsupported'
export type Nearest = { permit: Permit; meters: number | null; demo: boolean }

/**
 * The place to show now: the demo choice, or the nearest usable place within NEAR_METERS.
 * `candidates` = the permits that have their own card art and work now.
 */
export function useNearestPlace(candidates: Permit[]) {
  const { on, demoPermitId } = useAdaptiveSettings()
  const [pos, setPos] = useState<{ lat: number; lng: number } | null>(null)
  const [gps, setGps] = useState<GpsState>('off')

  useEffect(() => {
    if (!on || demoPermitId !== null) { setGps('off'); return }
    if (!('geolocation' in navigator)) { setGps('unsupported'); return }
    setGps('asking')
    const id = navigator.geolocation.watchPosition(
      (p) => { setPos({ lat: p.coords.latitude, lng: p.coords.longitude }); setGps('on') },
      (e) => setGps(e.code === e.PERMISSION_DENIED ? 'denied' : 'asking'),
      { enableHighAccuracy: false, maximumAge: 60_000, timeout: 20_000 },
    )
    return () => navigator.geolocation.clearWatch(id)
  }, [on, demoPermitId])

  const nearest = useMemo<Nearest | null>(() => {
    if (!on) return null
    if (demoPermitId !== null) {
      const p = candidates.find((c) => c.id === demoPermitId)
      return p ? { permit: p, meters: null, demo: true } : null
    }
    if (!pos) return null
    let best: Nearest | null = null
    for (const p of candidates) {
      if (p.siteLatitude == null || p.siteLongitude == null) continue
      const d = meters(pos.lat, pos.lng, p.siteLatitude, p.siteLongitude)
      if (d <= NEAR_METERS && (!best || d < (best.meters ?? Infinity))) best = { permit: p, meters: Math.round(d), demo: false }
    }
    return best
  }, [on, demoPermitId, pos, candidates])

  return { on, gps, nearest }
}
