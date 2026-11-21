import { useEffect, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { endpoints } from '../api/endpoints'
import type { Card } from '../api/types'
import { tr } from '../i18n'
import { useCardSkin } from './cardSkin'
import { CodeSvg } from './CodeSvg'

/** The customer's cards. The newest active one is "the" card. */
export function useCards() {
  return useQuery({ queryKey: ['cards'], queryFn: endpoints.cards })
}

export function mainCard(cards: Card[] | undefined): Card | null {
  if (!cards?.length) return null
  return cards.find((c) => c.status === 'ACTIVE') ?? cards[0]
}

/** "2027-09-30" -> "09/27" */
function expiry(d: string) {
  return `${d.slice(5, 7)}/${d.slice(2, 4)}`
}

const reducedMotion = () => typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches

/**
 * The card leans toward the pointer (mouse or finger) and a soft light follows it, like a real card in the hand.
 * CSS variables only (no re-render): --rx/--ry = the tilt, --mx/--my = where the light is, --so = light on/off.
 */
export function useTilt<T extends HTMLElement>() {
  const ref = useRef<T>(null)
  useEffect(() => {
    const el = ref.current
    if (!el || reducedMotion()) return
    let raf = 0
    let p: { x: number; y: number } | null = null
    const paint = () => {
      raf = 0
      if (!p) return
      const r = el.getBoundingClientRect()
      const x = Math.min(1, Math.max(0, (p.x - r.left) / r.width))
      const y = Math.min(1, Math.max(0, (p.y - r.top) / r.height))
      el.style.setProperty('--ry', ((x - 0.5) * 18).toFixed(1) + 'deg')
      el.style.setProperty('--rx', ((0.5 - y) * 14).toFixed(1) + 'deg')
      el.style.setProperty('--mx', (x * 100).toFixed(0) + '%')
      el.style.setProperty('--my', (y * 100).toFixed(0) + '%')
      el.style.setProperty('--so', '1')
    }
    const move = (e: PointerEvent) => {
      p = { x: e.clientX, y: e.clientY }
      el.classList.add('tl') // follow the pointer instantly; ease back when it leaves
      if (!raf) raf = requestAnimationFrame(paint)
    }
    const end = () => {
      p = null
      el.classList.remove('tl')
      ;['--rx', '--ry', '--so'].forEach((k) => el.style.removeProperty(k))
    }
    el.addEventListener('pointermove', move)
    el.addEventListener('pointerleave', end)
    el.addEventListener('pointercancel', end)
    const up = (e: PointerEvent) => { if (e.pointerType !== 'mouse') end() } // a finger lifts: settle back
    el.addEventListener('pointerup', up)
    return () => {
      cancelAnimationFrame(raf)
      el.removeEventListener('pointermove', move)
      el.removeEventListener('pointerleave', end)
      el.removeEventListener('pointercancel', end)
      el.removeEventListener('pointerup', up)
    }
  }, [])
  return ref
}

/**
 * The VORNX card, drawn like the physical one: the Riyadh skyline art and the logo from the design.
 * `reveal` shows the full number (card page); elsewhere only the last 4 digits.
 * `flippable` (card page): tap to turn it over and see the back (VORNX ID, signature strip).
 */
export function VCard({ card, name, reveal, vornxId, flippable }: {
  card: Card | null
  name: string
  reveal?: boolean
  vornxId?: string
  flippable?: boolean
}) {
  const tilt = useTilt<HTMLDivElement>()
  const skin = useCardSkin() // the look chosen on "بطاقتي"
  const [flipped, setFlipped] = useState(false)
  const pan = card
    ? reveal ? card.cardNumber.replace(/(\d{4})(?=\d)/g, '$1 ') : `•••• •••• •••• ${card.last4}`
    : '•••• •••• •••• ••••'
  const label = card ? tr('بطاقة VORNX تنتهي بالرقم {0}', { 0: card.last4 }) : tr('لا توجد بطاقة بعد')

  const front = (
    <div ref={flippable ? undefined : tilt} className={`vcard skin-${skin.id} ink-${skin.ink}${card && card.status !== 'ACTIVE' ? ' off' : ''}`}
         role={flippable ? undefined : 'img'} aria-label={flippable ? undefined : label}>
      <div>
        <div className="vc-head"><span className="vc-logo" aria-hidden="true" /><small>ONE</small></div>
        <div className="tag-line">PAY · CAMPUS · WORK · TRANSIT · ACCESS</div>
      </div>
      {/* the design's frosted plate, so the number and name stay readable over the skyline */}
      <div className="vc-plate">
        <div className="pan">{pan}</div>
        <div className="foot">
          <span dir="auto">{name}</span>
          <span className="num">{card ? expiry(card.expiresAt) : '--/--'}</span>
        </div>
      </div>
      <span className="vc-shine" aria-hidden="true" />
    </div>
  )

  if (!flippable) {
    return <div className="vwrap vfloat">{front}</div>
  }
  const turn = () => setFlipped((f) => !f)
  return (
    <div className="vwrap vfloat">
      <div ref={tilt} className="vtilt">
        <div className={`vflip${flipped ? ' fl' : ''}`} role="button" tabIndex={0} aria-pressed={flipped}
             aria-label={flipped ? tr('ظهر البطاقة. اضغط لعرض الوجه') : label + '. ' + tr('اضغط لقلب البطاقة')}
             onClick={turn} onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); turn() } }}>
          {front}
          <div className="vback" aria-hidden={!flipped} style={{ background: `linear-gradient(145deg,${skin.back[0]},${skin.back[1]})` }}>
            <div className="stripe" />
            <div className="sig"><i /><b>{card?.last4 ?? '••••'}</b></div>
            <div>
              <small>VORNX ID</small>
              <div className="vid">{vornxId ?? '—'}</div>
            </div>
            {vornxId && <span className="vb-bar"><CodeSvg code={vornxId} format="CODE_128" /></span>}
            <small dir="rtl" style={{ textAlign: 'right' }}>
              {tr('هذه البطاقة شخصية. إذا وجدتها، سلّمها لأقرب جهة شريكة أو تواصل مع VORNX.')}
            </small>
          </div>
        </div>
      </div>
    </div>
  )
}
