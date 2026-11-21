import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { endpoints } from '../api/endpoints'
import type { Permit } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { dateTime, money } from '../lib/format'
import { PERMIT_ICON, PERMIT_STATUS, PERMIT_TYPE, REQUEST_STATUS, TICKET_STATE } from '../lib/labels'
import { Empty, Num, Tag } from '../ui/bits'
import { Icon } from '../ui/Icon'
import { mainCard, useCards } from '../ui/VCard'
import { AdaptiveCard, AdaptivePanel, CodePanel } from '../ui/AdaptiveCard'
import { CodeWheel, type WheelItem } from '../ui/CodeWheel'
import { AddPassSheet } from '../ui/AddPassSheet'
import { hasPlaceArt } from '../ui/PlaceCard'
import { useNearby, usablePermits } from '../lib/nearby'
import { tr } from '../i18n'

/** "الرئيسية": the card, quick access to every active permit, and what's coming up. */
export function HomePage() {
  const { me } = useAuth()
  const cards = useCards()
  const permits = useQuery({ queryKey: ['permits'], queryFn: endpoints.permits })
  const wallet = useQuery({ queryKey: ['wallet'], queryFn: endpoints.wallet })
  const tickets = useQuery({ queryKey: ['tickets'], queryFn: endpoints.tickets })
  const requests = useQuery({ queryKey: ['requests'], queryFn: endpoints.requests })
  const passes = useQuery({ queryKey: ['passes'], queryFn: endpoints.passes })
  const [adding, setAdding] = useState(false)
  const [wheel, setWheel] = useState(false)
  const [picked, setPicked] = useState<number | null>(null) // a code chosen from the wheel
  // where am I? (only while the adaptive card is on; computed on the phone)
  const nearby = useNearby(usablePermits(permits.data ?? [], (p) => hasPlaceArt(p.type)), passes.data ?? [])
  // arriving at a place brings its own code up (instead of the one picked earlier)
  const hereKey = nearby.here?.key
  useEffect(() => { setPicked(null) }, [hereKey])
  const wheelItems: WheelItem[] = [
    ...(passes.data ?? []).map((p): WheelItem => ({ key: 'pass:' + p.id, name: p.name, sub: p.placeName ?? p.shownNumber ?? '', color: p.color, kind: 'pass' })),
    { key: 'add', name: tr('إضافة بطاقة'), sub: tr('امسح بطاقة جديدة'), color: '#2250F5', kind: 'add' },
  ]

  const usable = (permits.data ?? []).filter((p) => p.status === 'ACTIVE' || p.status === 'PENDING')
  const expiringSoon = usable.filter((p) => new Date(p.validUntil).getTime() - Date.now() < 3 * 86400_000)
  const upcoming = (tickets.data ?? []).filter((t) => t.upcoming && (t.status === 'CONFIRMED' || t.status === 'UNDER_REVIEW'))
  const pending = (requests.data ?? []).filter((r) => r.status === 'PENDING')

  return (
    <div className="cols">
      <section>
        <p className="mu" style={{ marginTop: 0 }}>
          <Num>{usable.length}</Num>{' '}{tr('خدمات فعّالة')}{expiringSoon.length > 0 && <> · <Num>{expiringSoon.length}</Num>{' '}{tr('تنتهي قريبًا')}</>}
        </p>
        {/* ONE card: it becomes the card of the place you're at, and the code under it is that place's */}
        <AdaptiveCard card={mainCard(cards.data)} name={`${me?.firstName} ${me?.lastName}`} nearby={nearby} onHold={() => setWheel(true)} />
        <CodePanel passes={passes.data ?? []} nearby={nearby} picked={picked} onClear={() => setPicked(null)} />
        <p className="hold-hint">{tr('اضغط مطولًا على البطاقة لاختيار رمز')}</p>
        {wheel && (
          <CodeWheel startDrag items={wheelItems} onClose={() => setWheel(false)}
                     onPick={(k) => { setWheel(false); if (k === 'add') setAdding(true); else setPicked(Number(k.split(':')[1])) }} />
        )}
        <AdaptivePanel nearby={nearby} />
        <AddPassSheet open={adding} onClose={() => setAdding(false)} />
        {cards.data && cards.data.length === 0 && (
          <p className="mu">{tr('لم تُصدر بطاقتك بعد. يمكنك فتح الأبواب من جوالك في صفحة التصاريح.')}</p>
        )}

        <Link to="/wallet" className="panel" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 16, color: 'inherit', textDecoration: 'none' }}>
          <span className="ent"><small>{tr('رصيد المحفظة')}</small><b><Num>{money(wallet.data?.balance)}</Num>{' '}{tr('ر.س')}</b></span>
          <span className="btn soft sm"><Icon name="wallet" />{tr('المحفظة')}</span>
        </Link>
      </section>

      <section>
        <div className="toolbar">
          <h2 className="grow" style={{ margin: 0 }}>{tr('وصول سريع')}</h2>
          <Link to="/permits" className="btn gh sm"><Icon name="grid" />{tr('كل التصاريح')}</Link>
        </div>
        {permits.data && usable.length === 0 && (
          <div className="notice">
            <b>{tr('لا توجد تصاريح فعّالة بعد')}</b>
            <p className="mu" style={{ margin: '4px 0 10px' }}>{tr('اشترك في نادٍ أو موقف، أو احصل على مفتاح غرفتك من الفندق، ويظهر هنا مباشرة.')}</p>
            <Link className="btn sm" to="/subscriptions">{tr('تصفّح الباقات')}</Link>
          </div>
        )}
        <div className="tiles">{usable.slice(0, 6).map((p) => <PermitTile key={p.id} p={p} />)}</div>

        <h2 className="section">{tr('القادم')}</h2>
        <div className="panel">
          {upcoming.length === 0 && pending.length === 0 && <Empty>{tr('لا شيء قادم.')}{' '}<Link to="/events">{tr('تصفّح الفعاليات')}</Link></Empty>}
          {upcoming.map((t) => (
            <Link to="/events" key={t.id} className="li" style={{ color: 'inherit', textDecoration: 'none' }}>
              <span className="ent"><b>{t.eventTitle}</b><small>{dateTime(t.startsAt)} · {t.venue}</small></span>
              <Tag label={TICKET_STATE[t.status]} />
            </Link>
          ))}
          {pending.map((r) => (
            <Link to="/subscriptions" key={r.id} className="li" style={{ color: 'inherit', textDecoration: 'none' }}>
              <span className="ent"><b>{r.planName}</b><small>{tr('طلب اشتراك ·')}{' '}<Num>{money(r.amount)}</Num>{' '}{tr('ر.س محجوزة')}</small></span>
              <Tag label={REQUEST_STATUS[r.status]} />
            </Link>
          ))}
        </div>
      </section>
    </div>
  )
}

function PermitTile({ p }: { p: Permit }) {
  return (
    <Link to="/permits" className="tile">
      <span className="ic"><Icon name={PERMIT_ICON[p.type]} /></span>
      <b>{p.siteName}</b>
      <small>{PERMIT_TYPE[p.type]} · {p.wholeSite ? tr('كل الموقع') : p.accessPoints.map((a) => a.name).join(tr('، '))}</small>
      {/* a pulsing dot on keys that work right now */}
      <span style={{ display: 'flex', alignItems: 'center' }}>
        {p.status === 'ACTIVE' && new Date(p.validFrom).getTime() <= Date.now() && <i className="live" aria-hidden="true" />}
        <Tag label={PERMIT_STATUS[p.status]} />
      </span>
    </Link>
  )
}
