import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../api/client'
import { endpoints } from '../api/endpoints'
import type { TicketCategory, TicketPriority } from '../api/types'
import { useCan } from '../auth/AuthContext'
import { ago, dateTime } from '../lib/format'
import { CATEGORY, keys, PRIORITY, TICKET_STATUS } from '../lib/labels'
import { Empty, ErrorBox, Loading, Num, Pager, Tag } from '../ui/bits'
import { Drawer } from '../ui/Drawer'
import { Icon } from '../ui/Icon'
import { useToast } from '../ui/Toast'

/** "الدعم": the partner's tickets with the VORNX team. */
export function SupportPage() {
  const can = useCan()
  const [page, setPage] = useState(0)
  const [openId, setOpenId] = useState<number | null>(null)
  const [creating, setCreating] = useState(false)
  const list = useQuery({ queryKey: ['tickets', page], queryFn: () => endpoints.tickets({ page }), refetchInterval: 30_000 })

  return (
    <>
      <p className="lead">مفتاح لا يفتح؟ تحتاج خدمة جديدة؟ افتح طلبًا وسيرد عليك فريق VORNX هنا وعلى بريدك.</p>
      <div className="toolbar">
        <span className="grow" />
        {can('operate') && <button className="btn" onClick={() => setCreating(true)}><Icon name="plus" />طلب جديد</button>}
      </div>
      {list.error && <ErrorBox error={list.error} />}
      {list.isLoading ? <Loading /> : (
        <div className="tw">
          <table>
            <thead><tr><th>الطلب</th><th>الأولوية</th><th>الحالة</th><th>آخر رسالة</th></tr></thead>
            <tbody>
              {list.data?.content.map((t) => (
                <tr key={t.id} className="click" onClick={() => setOpenId(t.id)}>
                  <td className="ent"><b>{t.subject}</b><small><Num>{t.number}</Num> · {CATEGORY[t.category]}</small></td>
                  <td><Tag label={PRIORITY[t.priority]} /></td>
                  <td><Tag label={TICKET_STATUS[t.status]} /></td>
                  <td>{ago(t.lastMessageAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {list.data?.content.length === 0 && <Empty>لا توجد طلبات بعد.</Empty>}
        </div>
      )}
      {list.data && <Pager page={list.data.page} totalPages={list.data.totalPages} onChange={setPage} />}

      <Drawer open={openId !== null} onClose={() => setOpenId(null)} title="طلب الدعم">
        {openId !== null && <TicketThread id={openId} />}
      </Drawer>
      <Drawer open={creating} onClose={() => setCreating(false)} title="طلب دعم جديد">
        {creating && <NewTicket onDone={(id) => { setCreating(false); setOpenId(id) }} />}
      </Drawer>
    </>
  )
}

function NewTicket({ onDone }: { onDone: (id: number) => void }) {
  const qc = useQueryClient()
  const toast = useToast()
  const [category, setCategory] = useState<TicketCategory>('DIGITAL_KEY_ISSUE')
  const [priority, setPriority] = useState<TicketPriority>('MEDIUM')
  const [subject, setSubject] = useState('')
  const [message, setMessage] = useState('')
  const [error, setError] = useState<string | null>(null)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    try {
      const t = await endpoints.openTicket({ category, priority, subject: subject.trim(), message: message.trim() })
      qc.invalidateQueries({ queryKey: ['tickets'] })
      toast('أُرسل الطلب لفريق VORNX')
      onDone(t.id)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'تعذّر الإرسال')
    }
  }

  return (
    <form className="form" onSubmit={submit}>
      <div className="row2">
        <label className="field"><span>النوع</span>
          <select className="input" value={category} onChange={(e) => setCategory(e.target.value as TicketCategory)}>
            {keys(CATEGORY).map((c) => <option key={c} value={c}>{CATEGORY[c]}</option>)}
          </select>
        </label>
        <label className="field"><span>الأولوية</span>
          <select className="input" value={priority} onChange={(e) => setPriority(e.target.value as TicketPriority)}>
            {keys(PRIORITY).map((p) => <option key={p} value={p}>{PRIORITY[p].text}</option>)}
          </select>
        </label>
      </div>
      <label className="field"><span>الموضوع</span><input className="input" required maxLength={200} value={subject} onChange={(e) => setSubject(e.target.value)} /></label>
      <label className="field"><span>التفاصيل</span><textarea className="input" required maxLength={5000} value={message} onChange={(e) => setMessage(e.target.value)} /></label>
      {error && <div className="error-box">{error}</div>}
      <div className="form-actions"><button className="btn">إرسال</button></div>
    </form>
  )
}

function TicketThread({ id }: { id: number }) {
  const can = useCan()
  const qc = useQueryClient()
  const toast = useToast()
  const [body, setBody] = useState('')
  const ticket = useQuery({ queryKey: ['ticket', id], queryFn: () => endpoints.ticket(id) })
  const reply = useMutation({
    mutationFn: () => endpoints.replyTicket(id, body.trim()),
    onSuccess: (t) => { qc.setQueryData(['ticket', id], t); qc.invalidateQueries({ queryKey: ['tickets'] }); setBody(''); toast('أُرسل الرد') },
    onError: (e) => toast(e instanceof ApiError ? e.message : 'تعذّر الإرسال', true),
  })

  if (ticket.isLoading) return <Loading />
  if (ticket.error) return <ErrorBox error={ticket.error} />
  const t = ticket.data!

  return (
    <>
      <h3 style={{ margin: '0 0 4px' }}>{t.subject}</h3>
      <p className="mu" style={{ margin: '0 0 12px' }}>
        <Num>{t.number}</Num> · {CATEGORY[t.category]} · <Tag label={TICKET_STATUS[t.status]} />
        {t.assignedTo && <> · المسؤول: {t.assignedTo}</>}
      </p>
      {t.messages?.map((m, i) => (
        <article key={i} className={`msg${m.senderType === 'ADMIN' ? ' admin' : ''}`}>
          <header><b>{m.senderType === 'ADMIN' ? `${m.senderName} · فريق VORNX` : m.senderName}</b><span>{dateTime(m.sentAt)}</span></header>
          <p>{m.body}</p>
        </article>
      ))}
      {can('operate') && (
        <form className="form" onSubmit={(e) => { e.preventDefault(); if (body.trim()) reply.mutate() }}>
          <textarea className="input" placeholder="اكتب ردك…" maxLength={5000} value={body} onChange={(e) => setBody(e.target.value)} />
          <div className="form-actions"><button className="btn" disabled={reply.isPending || !body.trim()}>إرسال</button></div>
        </form>
      )}
    </>
  )
}
