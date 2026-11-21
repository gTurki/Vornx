import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import type { Card, Pass } from '../api/types'
import { drawCode, isLinear } from '../lib/barcode'
import { NEAR_METERS, useAdaptiveSettings, type Nearby } from '../lib/nearby'
import { tr } from '../i18n'
import { PlaceCard } from './PlaceCard'
import { VCard } from './VCard'

/**
 * The ONE card on the home page. At a place with a key / membership it becomes that place's card
 * (the gym's card at the gym...); everywhere else it is the VORNX card.
 */
export function AdaptiveCard({ card, name, nearby }: { card: Card | null; name: string; nearby: Nearby }) {
  const [showMain, setShowMain] = useState(false)
  const here = nearby.here
  const label = here && (here.demo ? tr('وضع العرض: {0}', { 0: here.name }) : tr('قريب منك: {0} · {1} م', { 0: here.name, 1: here.meters ?? 0 }))

  if (here?.kind === 'permit' && !showMain) {
    const p = here.permit
    return (
      <>
        <div className="near-row">
          <span className="near-chip on" role="status"><i className="live" aria-hidden="true" />{label}</span>
          <button className="link" onClick={() => setShowMain(true)}>{tr('بطاقة VORNX')}</button>
        </div>
        {/* keyed by the place: a new place plays the swap animation */}
        <Link key={p.id} to="/permits" className="swapin" style={{ display: 'block' }} aria-label={p.siteName}><PlaceCard permit={p} /></Link>
      </>
    )
  }
  return (
    <>
      {here?.kind === 'permit' && showMain && (
        <button className="near-chip" onClick={() => setShowMain(false)}>📍 {tr('عرض بطاقة {0}', { 0: here.name })}</button>
      )}
      {here?.kind === 'pass' && <div className="near-row"><span className="near-chip on" role="status"><i className="live" aria-hidden="true" />{label}</span></div>}
      <Link to="/card" style={{ display: 'block', textDecoration: 'none' }}><VCard card={card} name={name} /></Link>
    </>
  )
}

/** A stored code drawn again (QR, barcode, PDF417, Aztec...). */
export function CodeImage({ pass }: { pass: Pass }) {
  const [svg, setSvg] = useState<string | null>(null)
  const [failed, setFailed] = useState(false)
  useEffect(() => {
    let alive = true
    setSvg(null); setFailed(false)
    drawCode(pass.code, pass.format).then((s) => { if (alive) setSvg(s) }).catch(() => { if (alive) setFailed(true) })
    return () => { alive = false }
  }, [pass.code, pass.format])
  return (
    <div className={`code-img${isLinear(pass.format) ? ' linear' : ''}`}>
      {svg ? <span dangerouslySetInnerHTML={{ __html: svg }} /> : failed ? <code>{pass.code}</code> : <span className="code-wait" />}
    </div>
  )
}

/**
 * Under the card: the barcode of the place you're at (a card you added by scanning). Not near any? You pick.
 * Tap "+" to scan a new card.
 */
export function CodePanel({ passes, nearby, onAdd }: { passes: Pass[]; nearby: Nearby; onAdd: () => void }) {
  const [picked, setPicked] = useState<number | null>(null)
  const auto = nearby.here?.kind === 'pass' ? nearby.here.pass : null
  // arriving at a new place brings its code up; tapping a chip still lets you show another one
  useEffect(() => { setPicked(null) }, [auto?.id])
  const shown = passes.find((p) => p.id === picked) ?? auto ?? passes[0] ?? null

  if (!shown) {
    return (
      <button className="code-panel empty" onClick={onAdd}>
        <span className="plus" aria-hidden="true">+</span>
        <span className="ent"><b>{tr('أضف بطاقاتك إلى VORNX')}</b>
          <small>{tr('امسح بطاقة ولاء أو عضوية أو تذكرة، ويظهر رمزها هنا تلقائيًا عندما تكون في المكان.')}</small></span>
      </button>
    )
  }
  return (
    <div className="code-panel" style={{ ['--pc' as string]: shown.color }}>
      <div className="code-head">
        <span className="dot" aria-hidden="true" />
        <b>{shown.name}</b>
        {auto?.id === shown.id && <span className="tag ok">{tr('هنا الآن')}</span>}
      </div>
      {/* keyed: switching cards plays the swap */}
      <div key={shown.id} className="swapin"><CodeImage pass={shown} /></div>
      {shown.shownNumber && <div className="code-num">{shown.shownNumber}</div>}
      <div className="code-pick" role="tablist" aria-label={tr('بطاقاتك المضافة')}>
        {passes.map((p) => (
          <button key={p.id} role="tab" aria-selected={p.id === shown.id} className={p.id === shown.id ? 'on' : ''}
                  style={{ ['--pc' as string]: p.color }} onClick={() => setPicked(p.id)}>
            <i aria-hidden="true" />{p.name}
          </button>
        ))}
        <button className="add" onClick={onAdd} aria-label={tr('إضافة بطاقة')}>+</button>
      </div>
    </div>
  )
}

/** "البطاقة التكيفية": the switch, what it found, and the demo mode for presentations. */
export function AdaptivePanel({ nearby }: { nearby: Nearby }) {
  const { on, setOn, demo, setDemo } = useAdaptiveSettings()
  const { gps, here, places } = nearby

  let status: string
  if (!on) status = tr('مطفأة: تظهر بطاقة VORNX دائمًا.')
  else if (here?.demo) status = tr('وضع العرض: كأنك الآن في {0}.', { 0: here.name })
  else if (gps === 'unsupported') status = tr('هذا الجهاز لا يدعم تحديد الموقع.')
  else if (gps === 'denied') status = tr('لم تسمح بالوصول إلى موقعك. فعّله من إعدادات المتصفح ثم أعد المحاولة.')
  else if (gps === 'asking') status = tr('جارٍ تحديد موقعك…')
  else if (here) status = tr('قريب منك: {0} ({1} م).', { 0: here.name, 1: here.meters ?? 0 })
  else status = tr('لا توجد جهة من جهاتك قريبة الآن (ضمن {0} م).', { 0: NEAR_METERS })

  return (
    <div className="panel adapt">
      <div className="adapt-head">
        <span className="ent">
          <b>{tr('البطاقة التكيفية')}</b>
          <small>{tr('بطاقة واحدة تتبعك: في النادي تصير بطاقة النادي، وعند المحل يظهر رمزه تحت البطاقة.')}</small>
        </span>
        <button type="button" role="switch" aria-checked={on} aria-label={tr('البطاقة التكيفية')}
                className={`sw${on ? ' on' : ''}`} onClick={() => setOn(!on)}><i /></button>
      </div>
      <p className={`adapt-status${on && (here || gps === 'on') ? ' ok' : ''}`}>{status}</p>
      {on && places.length > 0 && (
        <label className="field">
          <span>{tr('وضع العرض (لتجربتها بدون الذهاب للمكان)')}</span>
          <select className="input" value={demo ?? ''} onChange={(e) => setDemo(e.target.value || null)}>
            <option value="">{tr('موقعي الحقيقي')}</option>
            {places.map((h) => <option key={h.key} value={h.key}>{h.name}</option>)}
          </select>
        </label>
      )}
      <small className="mu">🔒 {tr('موقعك يبقى على جوالك ولا يُرسل إلى VORNX.')}</small>
    </div>
  )
}
