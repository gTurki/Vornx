import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { tr } from '../i18n'

/**
 * The code wheel (like a game's weapon wheel): press and hold the VORNX card, a glass wheel opens with your added
 * cards around it. Keep the finger down and slide toward one (it lights up in its color), lift to pick it.
 * Or lift right away and tap. Lift in the middle / tap outside = cancel. More than 8: pages, with arrows in the middle.
 */
export type WheelItem = { key: string; name: string; sub?: string; color: string; kind: 'pass' | 'add' }

const SIZE = 360, R_OUT = 168, R_IN = 74, GAP = 1.4 // degrees between segments
const PER_PAGE = 8

const rad = (d: number) => (d * Math.PI) / 180
const pt = (r: number, deg: number) => [r * Math.cos(rad(deg)), r * Math.sin(rad(deg))] as const

/** One ring segment from a0 to a1 degrees (0 = right, clockwise; -90 = top). */
function sector(a0: number, a1: number, rIn: number, rOut: number) {
  const large = a1 - a0 > 180 ? 1 : 0
  const [x0, y0] = pt(rOut, a0), [x1, y1] = pt(rOut, a1), [x2, y2] = pt(rIn, a1), [x3, y3] = pt(rIn, a0)
  return `M${x0} ${y0}A${rOut} ${rOut} 0 ${large} 1 ${x1} ${y1}L${x2} ${y2}A${rIn} ${rIn} 0 ${large} 0 ${x3} ${y3}Z`
}
const arc = (a0: number, a1: number, r: number) => {
  const [x0, y0] = pt(r, a0), [x1, y1] = pt(r, a1)
  return `M${x0} ${y0}A${r} ${r} 0 ${a1 - a0 > 180 ? 1 : 0} 1 ${x1} ${y1}`
}

export function CodeWheel({ items, onPick, onClose, startDrag }: {
  items: WheelItem[]
  onPick: (key: string) => void
  onClose: () => void
  /** the finger is still down from the long press: picking happens on release */
  startDrag: boolean
}) {
  const pages = Math.max(1, Math.ceil(items.length / PER_PAGE))
  const [page, setPage] = useState(0)
  const shown = useMemo(() => items.slice(page * PER_PAGE, page * PER_PAGE + PER_PAGE), [items, page])
  const n = shown.length
  const step = 360 / Math.max(n, 1)
  const [hot, setHot] = useState<number | null>(null)
  const svg = useRef<SVGSVGElement>(null)
  const dragging = useRef(startDrag)
  const moved = useRef(false)

  /** which segment is under this screen point (null = the middle or outside) */
  const hit = useCallback((x: number, y: number) => {
    const b = svg.current?.getBoundingClientRect()
    if (!b) return null
    const scale = b.width / SIZE
    const dx = x - (b.left + b.width / 2), dy = y - (b.top + b.height / 2)
    const d = Math.hypot(dx, dy) / scale
    if (d < R_IN || d > R_OUT + 40) return null
    const deg = ((Math.atan2(dy, dx) * 180) / Math.PI + 90 + 360) % 360
    return Math.min(n - 1, Math.floor(deg / step))
  }, [n, step])

  // haptic tick when the highlighted segment changes (Android; iPhones ignore it)
  const last = useRef<number | null>(null)
  useEffect(() => {
    if (hot !== null && hot !== last.current) navigator.vibrate?.(8)
    last.current = hot
  }, [hot])

  useEffect(() => {
    const move = (e: PointerEvent) => {
      if (!dragging.current) return
      moved.current = true
      setHot(hit(e.clientX, e.clientY))
    }
    const up = (e: PointerEvent) => {
      if (!dragging.current) return
      dragging.current = false
      const i = moved.current ? hit(e.clientX, e.clientY) : null
      if (i !== null && shown[i]) onPick(shown[i].key)
      else if (moved.current) onClose() // released in the middle: cancel
      // not moved: the wheel stays open for tapping
    }
    const key = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose() }
    // the phone took the gesture over (e.g. as a scroll): keep the wheel open and let the user tap
    const cancel = () => { dragging.current = false; setHot(null) }
    // while the wheel is open, sliding the finger must not scroll the page behind
    const noScroll = (e: TouchEvent) => e.preventDefault()
    window.addEventListener('pointermove', move)
    window.addEventListener('pointerup', up)
    window.addEventListener('pointercancel', cancel)
    window.addEventListener('keydown', key)
    document.addEventListener('touchmove', noScroll, { passive: false })
    return () => {
      window.removeEventListener('pointermove', move); window.removeEventListener('pointerup', up)
      window.removeEventListener('pointercancel', cancel); window.removeEventListener('keydown', key)
      document.removeEventListener('touchmove', noScroll)
    }
  }, [hit, onPick, onClose, shown])

  const hotItem = hot !== null ? shown[hot] : null

  return createPortal(
    <div className="wheel-scrim" onPointerDown={(e) => { if (e.target === e.currentTarget) onClose() }}>
      <div className="wheel" role="dialog" aria-modal="true" aria-label={tr('اختر الرمز')}>
        <svg ref={svg} viewBox={`${-SIZE / 2} ${-SIZE / 2} ${SIZE} ${SIZE}`} className="wheel-svg">
          <defs>
            <radialGradient id="wg-glass" r="1">
              <stop offset="0.35" stopColor="rgba(255,255,255,.10)" />
              <stop offset="1" stopColor="rgba(255,255,255,.03)" />
            </radialGradient>
            <filter id="wg-glow" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="5" /></filter>
          </defs>
          {/* the ring */}
          <circle r={R_OUT + 6} fill="rgba(8,12,30,.55)" stroke="rgba(255,255,255,.14)" strokeWidth="1" />
          <circle r={R_IN - 6} fill="rgba(8,12,30,.72)" stroke="rgba(255,255,255,.12)" strokeWidth="1" />
          {shown.map((it, i) => {
            const a0 = -90 + i * step + GAP / 2, a1 = -90 + (i + 1) * step - GAP / 2, mid = (a0 + a1) / 2
            const on = hot === i
            const [ox, oy] = pt(on ? 7 : 0, mid) // the lit segment slides outward
            const [ix, iy] = pt((R_IN + R_OUT) / 2 - 8, mid)
            return (
              <g key={it.key} className={`wseg${on ? ' on' : ''}`} style={{ transform: `translate(${ox}px,${oy}px)`, ['--i' as string]: i }}
                 onPointerUp={(e) => { if (!dragging.current) { e.stopPropagation(); onPick(it.key) } }}
                 onPointerEnter={() => { if (!dragging.current) setHot(i) }} onPointerLeave={() => { if (!dragging.current) setHot(null) }}>
                <path d={sector(a0, a1, R_IN, R_OUT)} fill={on ? it.color : 'url(#wg-glass)'} fillOpacity={on ? 0.34 : 1}
                      stroke={on ? it.color : 'rgba(255,255,255,.10)'} strokeWidth={on ? 1.5 : 1} />
                {/* the bright outer edge of the chosen segment (the blue arc in the picture) */}
                {on && <path d={arc(a0 + 2, a1 - 2, R_OUT - 4)} stroke={it.color} strokeWidth="7" strokeLinecap="round" fill="none" filter="url(#wg-glow)" />}
                {on && <path d={arc(a0 + 2, a1 - 2, R_OUT - 4)} stroke="#fff" strokeOpacity=".9" strokeWidth="2.5" strokeLinecap="round" fill="none" />}
                <g transform={`translate(${ix} ${iy})`}>
                  {it.kind === 'add'
                    ? <><circle r="17" fill="rgba(255,255,255,.14)" stroke="rgba(255,255,255,.55)" strokeDasharray="3 3" /><path d="M-7 0H7M0 -7V7" stroke="#fff" strokeWidth="2.4" strokeLinecap="round" /></>
                    : <><circle r="17" fill={it.color} stroke="rgba(255,255,255,.85)" strokeWidth="1.5" />
                        {/* a tiny QR mark */}
                        <path d="M-8-8h6v6h-6zM2-8h6v6h-6zM-8 2h6v6h-6zM3 3h2v2h-2zM6 6h2v2h-2zM3 6h2v2h-2z" fill="#fff" /></>}
                  <text y="31" textAnchor="middle" className="wseg-t">{it.name.length > 12 ? it.name.slice(0, 11) + '…' : it.name}</text>
                </g>
              </g>
            )
          })}
        </svg>
        {/* the middle: what's highlighted, and the pages */}
        <div className="wheel-mid">
          <b dir="auto">{hotItem ? hotItem.name : tr('اختر الرمز')}</b>
          <small dir="auto">{hotItem ? (hotItem.sub ?? '') : tr('اسحب إلى بطاقة ثم ارفع إصبعك')}</small>
          {pages > 1 && (
            <span className="wheel-pages">
              <button aria-label={tr('السابق')} onPointerUp={(e) => { e.stopPropagation(); setPage((p) => (p + pages - 1) % pages) }}>‹</button>
              <span className="num">{page + 1} / {pages}</span>
              <button aria-label={tr('التالي')} onPointerUp={(e) => { e.stopPropagation(); setPage((p) => (p + 1) % pages) }}>›</button>
            </span>
          )}
        </div>
      </div>
    </div>,
    document.body,
  )
}

/**
 * Press and hold (default 450 ms) without moving to fire `onLong`. A normal tap or a scroll / tilt still works.
 * Returns props to spread on the element, and `fired()` to ignore the click that follows a long press.
 */
export function useLongPress(onLong: (e: PointerEvent | React.PointerEvent) => void, ms = 450) {
  const timer = useRef(0)
  const start = useRef<{ x: number; y: number } | null>(null)
  const didFire = useRef(false)
  const cancel = () => { clearTimeout(timer.current); start.current = null }
  return {
    fired: () => { const f = didFire.current; didFire.current = false; return f },
    props: {
      onPointerDown: (e: React.PointerEvent) => {
        didFire.current = false
        start.current = { x: e.clientX, y: e.clientY }
        const ev = e
        timer.current = window.setTimeout(() => { didFire.current = true; navigator.vibrate?.(15); onLong(ev) }, ms)
      },
      onPointerMove: (e: React.PointerEvent) => {
        if (start.current && Math.hypot(e.clientX - start.current.x, e.clientY - start.current.y) > 10) cancel()
      },
      onPointerUp: cancel,
      onPointerCancel: cancel,
      onContextMenu: (e: React.MouseEvent) => e.preventDefault(), // no "save image / copy" menu on a long press
    },
  }
}
