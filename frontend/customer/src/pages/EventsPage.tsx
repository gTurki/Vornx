import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../api/client'
import { endpoints } from '../api/endpoints'
import type { EventTicket, ReminderOption, VEvent } from '../api/types'
import { count, dateTime, hhmm, money } from '../lib/format'
import { ATTENDEE, keys, REMINDER, TICKET_STATE } from '../lib/labels'
import { ConfirmButton, Empty, ErrorBox, Kv, Loading, Num, Pager, Tag } from '../ui/bits'
import { Drawer } from '../ui/Drawer'
import { useToast } from '../ui/Toast'
import { tr } from '../i18n'

/** "الفعاليات": upcoming events to register for, and my tickets. */
export function EventsPage() {
  const [tab, setTab] = useState<'tickets' | 'browse'>('tickets')
  const tickets = useQuery({ queryKey: ['tickets'], queryFn: endpoints.tickets })
  return (
    <>
      <div className="tabs">
        <button className={tab === 'tickets' ? 'on' : ''} onClick={() => setTab('tickets')}>{tr('تذاكري')}</button>
        <button className={tab === 'browse' ? 'on' : ''} onClick={() => setTab('browse')}>{tr('اكتشف الفعاليات')}</button>
      </div>
      {tab === 'tickets' ? (
        <>
          {tickets.error && <ErrorBox error={tickets.error} />}
          {tickets.isLoading && <Loading />}
          {tickets.data?.length === 0 && (
            <div className="notice">
              <b>{tr('لا توجد تذاكر بعد')}</b>
              <p className="mu" style={{ margin: '4px 0 10px' }}>{tr('سجّل في فعالية، وتصبح بطاقتك تذكرة الدخول.')}</p>
              <button className="btn sm" onClick={() => setTab('browse')}>{tr('اكتشف الفعاليات')}</button>
            </div>
          )}
          <div className="cols" style={{ gap: 16 }}>{tickets.data?.map((t) => <TicketCard key={t.id} t={t} />)}</div>
        </>
      ) : <Browse mine={tickets.data ?? []} />}
    </>
  )
}

function TicketCard({ t }: { t: EventTicket }) {
  const qc = useQueryClient()
  const toast = useToast()
  const onError = (e: unknown) => toast(e instanceof ApiError ? e.message : tr('تعذّر الحفظ'), true)
  const done = (msg: string) => { qc.invalidateQueries({ queryKey: ['tickets'] }); qc.invalidateQueries({ queryKey: ['events'] }); toast(msg) }
  const cancel = useMutation({ mutationFn: () => endpoints.cancelTicket(t.id), onSuccess: () => done(tr('أُلغيت التذكرة')), onError })
  const remind = useMutation({ mutationFn: (r: ReminderOption | null) => endpoints.setReminder(t.id, r), onSuccess: () => done(tr('حُفظ التذكير')), onError })
  const active = t.upcoming && (t.status === 'CONFIRMED' || t.status === 'UNDER_REVIEW')

  return (
    <div className="panel">
      <div className="li" style={{ paddingTop: 0 }}>
        <span className="ent"><b>{t.eventTitle}</b><small>{t.venue}</small></span>
        <Tag label={TICKET_STATE[t.status]} />
      </div>
      <Kv k={tr('الموعد')}>{dateTime(t.startsAt)}</Kv>
      <Kv k={tr('الصفة')}>{ATTENDEE[t.role]}{t.seat ? tr(' · مقعد {0}', { 0: t.seat }) : ''}</Kv>
      <Kv k={tr('رقم التذكرة')}><Num>{t.ticketNumber}</Num></Kv>
      {t.verificationCode && <Kv k={tr('رمز التحقق')}><Num>{t.verificationCode}</Num></Kv>}
      {t.rejectionReason && <p className="bad" style={{ margin: '8px 0 0' }}>{tr('سبب الرفض:')}{' '}{t.rejectionReason}</p>}
      {t.checkedInAt && <p className="ok" style={{ margin: '8px 0 0' }}>{tr('سُجّل حضورك')}{' '}{dateTime(t.checkedInAt)}</p>}
      {active && (
        <div className="form-actions" style={{ marginTop: 12, alignItems: 'center' }}>
          <select className="input" style={{ width: 'auto', padding: '6px 10px' }} aria-label={tr('التذكير')} value={t.reminder ?? ''}
                  onChange={(e) => remind.mutate((e.target.value || null) as ReminderOption | null)}>
            <option value="">{tr('بدون تذكير')}</option>
            {keys(REMINDER).map((r) => <option key={r} value={r}>{tr('ذكّرني')}{' '}{REMINDER[r]}</option>)}
          </select>
          <ConfirmButton className="btn gh sm" onConfirm={() => cancel.mutate()}>{tr('إلغاء التذكرة')}</ConfirmButton>
        </div>
      )}
    </div>
  )
}

function Browse({ mine }: { mine: EventTicket[] }) {
  const [page, setPage] = useState(0)
  const [openId, setOpenId] = useState<number | null>(null)
  const events = useQuery({ queryKey: ['events', page], queryFn: () => endpoints.events(page) })
  const open = events.data?.content.find((e) => e.id === openId) ?? null
  const has = (id: number) => mine.some((t) => t.eventId === id && t.status !== 'CANCELLED' && t.status !== 'REJECTED')

  return (
    <>
      {events.error && <ErrorBox error={events.error} />}
      {events.isLoading && <Loading />}
      {events.data?.content.length === 0 && <Empty>{tr('لا توجد فعاليات قادمة حاليًا.')}</Empty>}
      <div className="tiles" style={{ gridTemplateColumns: 'repeat(auto-fill,minmax(240px,1fr))' }}>
        {events.data?.content.map((e) => (
          <button key={e.id} className="tile" onClick={() => setOpenId(e.id)}>
            <small>{e.category ?? tr('فعالية')} · {e.organizer}</small>
            <b>{e.title}</b>
            <small>{dateTime(e.startsAt)} · {e.venue}</small>
            <span>
              {e.fee > 0 ? <><Num>{money(e.fee)}</Num>{' '}{tr('ر.س')}</> : tr('مجانية')}
              {e.spotsLeft !== null && <span className="mu"> · {e.spotsLeft > 0 ? <>{tr('متبقٍ')}{' '}<Num>{count(e.spotsLeft)}</Num>{' '}{tr('مقعد')}</> : tr('اكتملت')}</span>}
              {has(e.id) && <span className="ok">{' '}{tr('· مسجّل')}</span>}
            </span>
          </button>
        ))}
      </div>
      {events.data && <Pager page={events.data.page} totalPages={events.data.totalPages} onChange={setPage} />}
      <Drawer open={open !== null} onClose={() => setOpenId(null)} title={open?.title ?? ''}>
        {open && <EventDetails e={open} registered={has(open.id)} />}
      </Drawer>
    </>
  )
}

function EventDetails({ e, registered }: { e: VEvent; registered: boolean }) {
  const qc = useQueryClient()
  const toast = useToast()
  const register = useMutation({
    mutationFn: () => endpoints.registerEvent(e.id),
    onSuccess: (t) => {
      ['tickets', 'events', 'wallet', 'transactions', 'permits'].forEach((k) => qc.invalidateQueries({ queryKey: [k] }))
      toast(t.status === 'CONFIRMED' ? tr('سُجّلت! بطاقتك هي تذكرة الدخول.') : tr('أُرسل طلب التسجيل للمنظّم.'))
    },
    onError: (err) => toast(err instanceof ApiError ? err.message : tr('تعذّر التسجيل'), true),
  })
  const full = e.spotsLeft === 0
  const maps = e.latitude !== null && e.longitude !== null ? `https://maps.google.com/?q=${e.latitude},${e.longitude}` : null

  return (
    <>
      <p className="mu" style={{ marginTop: 0 }}>{e.organizer}{e.category ? ` · ${e.category}` : ''}</p>
      {e.description && <p style={{ whiteSpace: 'pre-wrap' }}>{e.description}</p>}
      <Kv k={tr('يبدأ')}>{dateTime(e.startsAt)}</Kv>
      <Kv k={tr('ينتهي')}>{dateTime(e.endsAt)}</Kv>
      {e.doorsOpenAt && <Kv k={tr('فتح الأبواب')}>{dateTime(e.doorsOpenAt)}</Kv>}
      <Kv k={tr('المكان')}>{e.venue}{e.address ? ` · ${e.address}` : ''}</Kv>
      <Kv k={tr('الرسوم')}>{e.fee > 0 ? <><Num>{money(e.fee)}</Num>{' '}{tr('ر.س')}</> : tr('مجانية')}</Kv>
      {e.dressCode && <Kv k={tr('اللباس')}>{e.dressCode}</Kv>}
      {e.agenda.length > 0 && <Kv k={tr('البرنامج')}>{e.agenda.map((a, i) => <div key={i}><Num>{hhmm(a.time)}</Num> {a.title}</div>)}</Kv>}
      {maps && <p><a href={maps} target="_blank" rel="noopener noreferrer">{tr('الموقع على الخريطة ↗')}</a></p>}
      <div className="form-actions" style={{ marginTop: 14 }}>
        {registered ? <Tag label={{ text: tr('أنت مسجّل'), tone: 'ok' }} /> : (
          <ConfirmButton className="btn wide" onConfirm={() => register.mutate()} disabled={full || register.isPending}>
            {full ? tr('اكتملت المقاعد') : e.fee > 0 ? tr('سجّل وادفع {0} ر.س', { 0: money(e.fee) }) : tr('سجّل مجانًا')}
          </ConfirmButton>
        )}
      </div>
      {e.requiresApproval && !registered && <p className="mu">{tr('يراجع المنظّم طلبك قبل التأكيد.')}</p>}
    </>
  )
}
