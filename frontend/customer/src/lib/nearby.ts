import { useEffect, useMemo, useState, useSyncExternalStore } from 'react'
import type { Pass, Permit } from '../api/types'

/**
 * "البطاقة التكيفية" (the adaptive card): ONE card that follows you. When it is on, the phone watches its own
 * location and finds the nearest place you can use: a key / membership (then the card becomes that place's card)
 * or a card you added by scanning (then its barcode shows under the card).
 * Everything happens ON THE PHONE: the location is never sent to VORNX.
 *
 * Demo mode (for presentations): pick a place and the app behaves as if you were standing there.
 */

/** Within this distance a place takes over. */
export const NEAR_METERS = 500

const ON_KEY = 'vornx.customer.adaptive'
const DEMO_KEY = 'vornx.customer.adaptiveDemo2'
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

/** demo = "permit:12" or "pass:5" (or null = real location). */
export function useAdaptiveSettings() {
  const on = useSyncExternalStore(subscribe, () => get(ON_KEY) === '1', () => false)
  const demo = useSyncExternalStore(subscribe, () => get(DEMO_KEY), () => null)
  return {
    on,
    demo,
    setOn: (v: boolean) => { put(ON_KEY, v ? '1' : null); if (!v) put(DEMO_KEY, null) },
    setDemo: (key: string | null) => put(DEMO_KEY, key),
  }
}

/** Great-circle distance in meters. */
function meters(lat1: number, lng1: number, lat2: number, lng2: number) {
  const R = 6371000, r = Math.PI / 180
  const a = Math.sin(((lat2 - lat1) * r) / 2) ** 2 + Math.cos(lat1 * r) * Math.cos(lat2 * r) * Math.sin(((lng2 - lng1) * r) / 2) ** 2
  return 2 * R * Math.asin(Math.sqrt(a))
}

export type GpsState = 'off' | 'asking' | 'on' | 'denied' | 'unsupported'

/** The place you are at: a key / membership, or a scanned card. */
export type Here =
  | { kind: 'permit'; key: string; name: string; permit: Permit; meters: number | null; demo: boolean }
  | { kind: 'pass'; key: string; name: string; pass: Pass; meters: number | null; demo: boolean }

export type Nearby = { on: boolean; gps: GpsState; here: Here | null; places: Here[] }

/** Keys and memberships that work right now and have their own card art (see PlaceCard). */
export function usablePermits(permits: Permit[], hasArt: (p: Permit) => boolean) {
  const now = Date.now()
  return permits.filter((p) => p.status === 'ACTIVE' && hasArt(p) && new Date(p.validFrom).getTime() <= now)
}

/** Call ONCE per page (it watches the location while the adaptive card is on) and pass the result down. */
export function useNearby(permits: Permit[], passes: Pass[]): Nearby {
  const { on, demo } = useAdaptiveSettings()
  const [pos, setPos] = useState<{ lat: number; lng: number } | null>(null)
  const [gps, setGps] = useState<GpsState>('off')

  useEffect(() => {
    if (!on || demo !== null) { setGps('off'); return }
    if (!('geolocation' in navigator)) { setGps('unsupported'); return }
    setGps('asking')
    const id = navigator.geolocation.watchPosition(
      (p) => { setPos({ lat: p.coords.latitude, lng: p.coords.longitude }); setGps('on') },
      (e) => setGps(e.code === e.PERMISSION_DENIED ? 'denied' : 'asking'),
      { enableHighAccuracy: false, maximumAge: 60_000, timeout: 20_000 },
    )
    return () => navigator.geolocation.clearWatch(id)
  }, [on, demo])

  return useMemo(() => {
    const places: Here[] = [
      ...permits.map((p): Here => ({ kind: 'permit', key: 'permit:' + p.id, name: p.siteName, permit: p, meters: null, demo: false })),
      ...passes.map((p): Here => ({ kind: 'pass', key: 'pass:' + p.id, name: p.placeName || p.name, pass: p, meters: null, demo: false })),
    ]
    const at = (h: Here): [number, number] | null => {
      const [lat, lng] = h.kind === 'permit' ? [h.permit.siteLatitude, h.permit.siteLongitude] : [h.pass.latitude, h.pass.longitude]
      return lat == null || lng == null ? null : [lat, lng]
    }
    let here: Here | null = null
    if (on && demo !== null) {
      const d = places.find((h) => h.key === demo)
      here = d ? { ...d, demo: true } : null
    } else if (on && pos) {
      for (const h of places) {
        const c = at(h)
        if (!c) continue
        const m = Math.round(meters(pos.lat, pos.lng, c[0], c[1]))
        if (m <= NEAR_METERS && (!here || m < (here.meters ?? Infinity))) here = { ...h, meters: m }
      }
    }
    return { on, gps, here, places }
  }, [on, demo, pos, gps, permits, passes])
}
