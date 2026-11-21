import { useState } from 'react'
import { Link } from 'react-router-dom'
import type { Card, Permit } from '../api/types'
import { useAdaptiveSettings, useNearestPlace, NEAR_METERS } from '../lib/nearby'
import { tr } from '../i18n'
import { hasPlaceArt, PlaceCard } from './PlaceCard'
import { VCard } from './VCard'

/** Keys and memberships that work right now and have their own card art. */
function candidatesOf(permits: Permit[]) {
  const now = Date.now()
  return permits.filter((p) => p.status === 'ACTIVE' && hasPlaceArt(p.type) && new Date(p.validFrom).getTime() <= now)
}

/**
 * The home card: one VORNX card that turns into the card of the place you're at (the gym's card at the gym,
 * the hotel's card at the hotel...). Falls back to the main VORNX card when nothing is near.
 */
export function AdaptiveCard({ card, name, permits }: { card: Card | null; name: string; permits: Permit[] }) {
  const candidates = candidatesOf(permits)
  const { nearest } = useNearestPlace(candidates)
  const [showMain, setShowMain] = useState(false)

  if (!nearest || showMain) {
    return (
      <>
        {nearest && showMain && (
          <button className="near-chip" onClick={() => setShowMain(false)}>📍 {tr('عرض بطاقة {0}', { 0: nearest.permit.siteName })}</button>
        )}
        <Link to="/card" style={{ display: 'block', textDecoration: 'none' }}><VCard card={card} name={name} /></Link>
      </>
    )
  }
  const p = nearest.permit
  return (
    <>
      <div className="near-row">
        <span className="near-chip on" role="status">
          <i className="live" aria-hidden="true" />
          {nearest.demo ? tr('وضع العرض: {0}', { 0: p.siteName }) : tr('قريب منك: {0} · {1} م', { 0: p.siteName, 1: nearest.meters ?? 0 })}
        </span>
        <button className="link" onClick={() => setShowMain(true)}>{tr('بطاقة VORNX')}</button>
      </div>
      {/* keyed by the place: a new place plays the swap animation */}
      <Link key={p.id} to="/permits" className="swapin" style={{ display: 'block' }} aria-label={p.siteName}>
        <PlaceCard permit={p} />
      </Link>
    </>
  )
}

/** "البطاقة التكيفية": the switch, what it found, and the demo mode for presentations. */
export function AdaptivePanel({ permits }: { permits: Permit[] }) {
  const candidates = candidatesOf(permits)
  const { on, setOn, demoPermitId, setDemo } = useAdaptiveSettings()
  const { gps, nearest } = useNearestPlace(candidates)

  let status: string
  if (!on) status = tr('مطفأة: تظهر بطاقة VORNX دائمًا.')
  else if (nearest?.demo) status = tr('وضع العرض: كأنك الآن في {0}.', { 0: nearest.permit.siteName })
  else if (gps === 'unsupported') status = tr('هذا الجهاز لا يدعم تحديد الموقع.')
  else if (gps === 'denied') status = tr('لم تسمح بالوصول إلى موقعك. فعّله من إعدادات المتصفح ثم أعد المحاولة.')
  else if (gps === 'asking') status = tr('جارٍ تحديد موقعك…')
  else if (nearest) status = tr('قريب منك: {0} ({1} م)، فظهرت بطاقته أولًا.', { 0: nearest.permit.siteName, 1: nearest.meters ?? 0 })
  else status = tr('لا توجد جهة من جهاتك قريبة الآن (ضمن {0} م).', { 0: NEAR_METERS })

  return (
    <div className="panel adapt">
      <div className="adapt-head">
        <span className="ent">
          <b>{tr('البطاقة التكيفية')}</b>
          <small>{tr('تتحول بطاقتك تلقائيًا إلى بطاقة المكان الأقرب لك: النادي، الفندق، الموقف…')}</small>
        </span>
        <button type="button" role="switch" aria-checked={on} aria-label={tr('البطاقة التكيفية')}
                className={`sw${on ? ' on' : ''}`} onClick={() => setOn(!on)}><i /></button>
      </div>
      <p className={`adapt-status${on && (nearest || gps === 'on') ? ' ok' : ''}`}>{status}</p>
      {on && candidates.length > 0 && (
        <label className="field">
          <span>{tr('وضع العرض (لتجربتها بدون الذهاب للمكان)')}</span>
          <select className="input" value={demoPermitId ?? ''} onChange={(e) => setDemo(e.target.value ? Number(e.target.value) : null)}>
            <option value="">{tr('موقعي الحقيقي')}</option>
            {candidates.map((p) => <option key={p.id} value={p.id}>{p.siteName}</option>)}
          </select>
        </label>
      )}
      <small className="mu">🔒 {tr('موقعك يبقى على جوالك ولا يُرسل إلى VORNX.')}</small>
    </div>
  )
}
