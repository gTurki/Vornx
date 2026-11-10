import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../api/client'
import { endpoints } from '../api/endpoints'
import type { AgendaItem, EventTicket, VEvent } from '../api/types'
import { useCan } from '../auth/AuthContext'
import { count, dateTime, fromRiyadhInput, hhmm, money, riyadhInputIn, toRiyadhInput } from '../lib/format'
import { ATTENDEE, EVENT_STATUS, TICKET_STATE } from '../lib/labels'
import { ConfirmButton, Empty, ErrorBox, Kv, Loading, Num, Tag } from '../ui/bits'
import { Drawer } from '../ui/Drawer'
import { Icon } from '../ui/Icon'
import { AccessPointChecks, SiteSelect } from '../ui/pickers'
import { useToast } from '../ui/Toast'

/** "الفعاليات": conferences, exhibitions, dinners. Customers register in the app; the ticket opens the hall doors. */
export function EventsPage() {
  const can = useCan()
  const events = useQuery({ queryKey: ['events'], queryFn: endpoints.events })
  const [openId, setOpenId] = useState<number | null>(null)
  const [editing, setEditing] = useState<VEvent | 'new' | null>(null)
  const open = events.data?.find((e) => e.id === openId) ?? null

  return (
    <>
      <p className="lead">أنشئ الفعالية كمسودة، ثم انشرها لتظهر للعملاء. كل تذكرة مؤكدة تصبح تصريحًا يفتح أبواب القاعة يوم الفعالية، ويُسجَّل الحضور تلقائيًا عند الدخول.</p>
      <div className="toolbar">
        <span className="grow" />
        {can('edit') && <button className="btn" onClick={() => setEditing('new')}><Icon name="plus" />فعالية جديدة</button>}
      </div>
      {events.error && <ErrorBox error={events.error} />}
      {events.isLoading ? <Loading /> : (
        <div className="tw">
          <table>
            <thead><tr><th>الفعالية</th><th>الموعد</th><th>المقاعد</th><th>الرسوم</th><th>الحالة</th></tr></thead>
            <tbody>
              {events.data?.map((e) => (
                <tr key={e.id} className="click" onClick={() => setOpenId(e.id)}>
                  <td className="ent"><b>{e.title}</b><small>{e.venue}{e.category ? ` · ${e.category}` : ''}</small></td>
                  <td>{dateTime(e.startsAt)}</td>
                  <td>{e.capacity ? <><Num>{count(e.capacity - (e.spotsLeft ?? 0))}</Num> / <Num>{count(e.capacity)}</Num></> : 'غير محدود'}</td>
                  <td>{e.fee > 0 ? <><Num>{money(e.fee)}</Num> ر.س</> : 'مجانية'}</td>
                  <td><Tag label={EVENT_STATUS[e.status]} /> {!e.upcoming && e.status !== 'CANCELLED' && <span className="mu">انتهت</span>}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {events.data?.length === 0 && <Empty>لا توجد فعاليات بعد.</Empty>}
        </div>
      )}

      <Drawer open={open !== null} onClose={() => setOpenId(null)} title={open?.title ?? ''}>
        {open && <EventDetails ev={open} onEdit={() => setEditing(open)} />}
      </Drawer>
      <Drawer open={editing !== null} onClose={() => setEditing(null)} title={editing === 'new' ? 'فعالية جديدة' : 'تعديل الفعالية'}>
        {editing !== null && <EventForm ev={editing === 'new' ? null : editing} onDone={(e) => { setEditing(null); setOpenId(e.id) }} />}
      </Drawer>
    </>
  )
}

function EventDetails({ ev, onEdit }: { ev: VEvent; onEdit: () => void }) {
  const can = useCan()
  const qc = useQueryClient()
  const toast = useToast()
  const tickets = useQuery({ queryKey: ['event-tickets', ev.id], queryFn: () => endpoints.eventTickets(ev.id) })
  const onError = (e: unknown) => toast(e instanceof ApiError ? e.message : 'تعذّر الحفظ', true)
  const publish = useMutation({
    mutationFn: () => endpoints.publishEvent(ev.id),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['events'] }); toast('نُشرت الفعالية وأصبحت تظهر للعملاء') },
    onError,
  })
  const cancel = useMutation({
    mutationFn: () => endpoints.cancelEvent(ev.id),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['events'] }); qc.invalidateQueries({ queryKey: ['event-tickets', ev.id] }); toast('أُلغيت الفعالية وأُبلغ المسجلون') },
    onError,
  })
  const active = tickets.data?.filter((t) => t.status === 'CONFIRMED' || t.status === 'CHECKED_IN').length ?? 0
  const attended = tickets.data?.filter((t) => t.status === 'CHECKED_IN').length ?? 0

  return (
    <>
      <div className="li"><span className="mu">{ev.venue}</span><Tag label={EVENT_STATUS[ev.status]} /></div>
      <Kv k="يبدأ">{dateTime(ev.startsAt)}</Kv>
      <Kv k="ينتهي">{dateTime(ev.endsAt)}</Kv>
      {ev.doorsOpenAt && <Kv k="فتح الأبواب">{dateTime(ev.doorsOpenAt)}</Kv>}
      <Kv k="الرسوم">{ev.fee > 0 ? <><Num>{money(ev.fee)}</Num> ر.س</> : 'مجانية'}</Kv>
      <Kv k="السعة">{ev.capacity ? <><Num>{count(ev.spotsLeft)}</Num> مقعد متبقٍ من <Num>{count(ev.capacity)}</Num></> : 'غير محدودة'}</Kv>
      <Kv k="التسجيل">{ev.requiresApproval ? 'يحتاج موافقة' : 'تأكيد فوري'}</Kv>
      {ev.agenda.length > 0 && (
        <Kv k="البرنامج">{ev.agenda.map((a, i) => <div key={i}><Num>{hhmm(a.time)}</Num> {a.title}</div>)}</Kv>
      )}

      {can('edit') && ev.status !== 'CANCELLED' && (
        <div className="form-actions" style={{ marginTop: 12 }}>
          <button className="btn gh sm" onClick={onEdit}>تعديل</button>
          {ev.status === 'DRAFT' && <button className="btn sm" disabled={publish.isPending} onClick={() => publish.mutate()}>نشر</button>}
          {ev.upcoming && <ConfirmButton className="btn gh sm" onConfirm={() => cancel.mutate()}>إلغاء الفعالية</ConfirmButton>}
        </div>
      )}

      <h2 className="section">التذاكر · <Num>{count(active)}</Num> مؤكدة · <Num>{count(attended)}</Num> حضروا</h2>
      {tickets.isLoading && <Loading />}
      {tickets.data?.length === 0 && <Empty>لم يسجّل أحد بعد.</Empty>}
      {tickets.data?.map((t) => <TicketRow key={t.id} t={t} />)}
    </>
  )
}

function TicketRow({ t }: { t: EventTicket }) {
  const can = useCan()
  const qc = useQueryClient()
  const toast = useToast()
  const [rejecting, setRejecting] = useState(false)
  const [reason, setReason] = useState('')
  const done = (msg: string) => { qc.invalidateQueries({ queryKey: ['event-tickets', t.eventId] }); qc.invalidateQueries({ queryKey: ['events'] }); toast(msg) }
  const onError = (e: unknown) => toast(e instanceof ApiError ? e.message : 'تعذّر الحفظ', true)
  const approve = useMutation({ mutationFn: () => endpoints.approveTicket(t.id), onSuccess: () => done('أُكدت التذكرة'), onError })
  const reject = useMutation({ mutationFn: () => endpoints.rejectTicket(t.id, reason.trim()), onSuccess: () => done('رُفضت التذكرة'), onError })

  return (
    <div className="li" style={{ flexWrap: 'wrap' }}>
      <span className="ent">
        <b>{t.holderName} <span className="num mu">{t.holderVornxId}</span></b>
        <small>
          {ATTENDEE[t.role]}{t.seat ? ` · مقعد ${t.seat}` : ''} · <Num>{t.ticketNumber}</Num>
          {t.checkedInAt && <> · حضر {dateTime(t.checkedInAt)}</>}
        </small>
      </span>
      {t.status === 'UNDER_REVIEW' && can('operate') ? (
        <span style={{ display: 'flex', gap: 6 }}>
          <button className="btn sm" disabled={approve.isPending} onClick={() => approve.mutate()}>قبول</button>
          <button className="btn gh sm" onClick={() => setRejecting(!rejecting)}>رفض</button>
        </span>
      ) : <Tag label={TICKET_STATE[t.status]} />}
      {rejecting && (
        <form className="form" style={{ width: '100%' }} onSubmit={(e) => { e.preventDefault(); reject.mutate() }}>
          <input className="input" required maxLength={500} placeholder="سبب الرفض (يظهر للعميل)" value={reason} onChange={(e) => setReason(e.target.value)} />
          <div className="form-actions"><button className="btn danger sm" disabled={!reason.trim() || reject.isPending}>تأكيد الرفض</button></div>
        </form>
      )}
    </div>
  )
}

function EventForm({ ev, onDone }: { ev: VEvent | null; onDone: (e: VEvent) => void }) {
  const qc = useQueryClient()
  const toast = useToast()
  const [siteId, setSiteId] = useState<number | null>(ev?.siteId ?? null)
  const [aps, setAps] = useState<number[]>(ev?.accessPointIds ?? [])
  const [f, setF] = useState({
    title: ev?.title ?? '', description: ev?.description ?? '', category: ev?.category ?? '',
    startsAt: ev ? toRiyadhInput(ev.startsAt) : riyadhInputIn(24 * 7),
    endsAt: ev ? toRiyadhInput(ev.endsAt) : riyadhInputIn(24 * 7 + 3),
    doorsOpenAt: toRiyadhInput(ev?.doorsOpenAt), fee: ev ? String(ev.fee) : '0',
    capacity: ev?.capacity ? String(ev.capacity) : '', dressCode: ev?.dressCode ?? '', contactPhone: ev?.contactPhone ?? '',
  })
  const [approval, setApproval] = useState(ev?.requiresApproval ?? false)
  const [agenda, setAgenda] = useState<AgendaItem[]>(ev?.agenda.map((a) => ({ ...a, time: hhmm(a.time) })) ?? [])
  const [error, setError] = useState<string | null>(null)
  const set = (k: keyof typeof f) => (e: { target: { value: string } }) => setF({ ...f, [k]: e.target.value })

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    const body = {
      siteId: siteId!, title: f.title.trim(), description: f.description.trim() || undefined,
      category: f.category.trim() || undefined, startsAt: fromRiyadhInput(f.startsAt), endsAt: fromRiyadhInput(f.endsAt),
      doorsOpenAt: f.doorsOpenAt ? fromRiyadhInput(f.doorsOpenAt) : null, fee: Number(f.fee || 0),
      capacity: f.capacity ? Number(f.capacity) : null, requiresApproval: approval,
      dressCode: f.dressCode.trim() || undefined, contactPhone: f.contactPhone.trim() || undefined,
      accessPointIds: aps, agenda: agenda.filter((a) => a.time && a.title.trim()),
    }
    try {
      const saved = ev ? await endpoints.updateEvent(ev.id, body) : await endpoints.createEvent(body)
      qc.invalidateQueries({ queryKey: ['events'] })
      toast(ev ? 'حُفظت الفعالية' : 'أُنشئت الفعالية كمسودة. انشرها عندما تكون جاهزة.')
      onDone(saved)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'تعذّر الحفظ')
    }
  }

  return (
    <form className="form" onSubmit={submit}>
      <label className="field"><span>العنوان</span><input className="input" required maxLength={200} value={f.title} onChange={set('title')} /></label>
      <label className="field"><span>الوصف</span><textarea className="input" maxLength={5000} value={f.description} onChange={set('description')} /></label>
      <label className="field"><span>التصنيف</span><input className="input" maxLength={80} placeholder="مؤتمر، معرض، عشاء…" value={f.category} onChange={set('category')} /></label>
      <label className="field"><span>المكان</span><SiteSelect value={siteId} onChange={(v) => { setSiteId(v); setAps([]) }} /></label>
      <div className="field"><span>القاعات / الأبواب (بدون اختيار = كل الموقع)</span><AccessPointChecks siteId={siteId} value={aps} onChange={setAps} /></div>
      <div className="row2">
        <label className="field"><span>يبدأ (بتوقيت الرياض)</span><input className="input" type="datetime-local" dir="ltr" required value={f.startsAt} onChange={set('startsAt')} /></label>
        <label className="field"><span>ينتهي</span><input className="input" type="datetime-local" dir="ltr" required min={f.startsAt} value={f.endsAt} onChange={set('endsAt')} /></label>
      </div>
      <label className="field"><span>فتح الأبواب (اختياري)</span><input className="input" type="datetime-local" dir="ltr" max={f.startsAt} value={f.doorsOpenAt} onChange={set('doorsOpenAt')} /></label>
      <div className="row2">
        <label className="field"><span>الرسوم (0 = مجانية)</span><input className="input" type="number" dir="ltr" min={0} step="0.01" value={f.fee} onChange={set('fee')} /></label>
        <label className="field"><span>السعة (فارغ = غير محدودة)</span><input className="input" type="number" dir="ltr" min={1} value={f.capacity} onChange={set('capacity')} /></label>
      </div>
      <div className="row2">
        <label className="field"><span>اللباس</span><input className="input" maxLength={100} value={f.dressCode} onChange={set('dressCode')} /></label>
        <label className="field"><span>جوال التواصل</span><input className="input" dir="ltr" maxLength={20} value={f.contactPhone} onChange={set('contactPhone')} /></label>
      </div>
      <label style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
        <input type="checkbox" checked={approval} onChange={(e) => setApproval(e.target.checked)} /> كل تسجيل يحتاج موافقة فريقك
      </label>

      <div className="field"><span>البرنامج</span>
        {agenda.map((a, i) => (
          <div key={i} style={{ display: 'flex', gap: 6 }}>
            <input className="input" type="time" dir="ltr" style={{ width: 120 }} value={a.time}
                   onChange={(e) => setAgenda(agenda.map((x, j) => (j === i ? { ...x, time: e.target.value } : x)))} />
            <input className="input" maxLength={200} placeholder="الفقرة" value={a.title}
                   onChange={(e) => setAgenda(agenda.map((x, j) => (j === i ? { ...x, title: e.target.value } : x)))} />
            <button type="button" className="ib" aria-label="حذف الفقرة" onClick={() => setAgenda(agenda.filter((_, j) => j !== i))}><Icon name="close" /></button>
          </div>
        ))}
        <button type="button" className="btn gh sm" style={{ alignSelf: 'start' }} onClick={() => setAgenda([...agenda, { time: '', title: '' }])}>
          <Icon name="plus" />فقرة
        </button>
      </div>
      {error && <div className="error-box">{error}</div>}
      <div className="form-actions"><button className="btn" disabled={siteId === null}>حفظ</button></div>
    </form>
  )
}
