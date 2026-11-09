import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../api/client'
import { endpoints } from '../api/endpoints'
import type { TicketPriority, TicketStatus } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { ago, dateTime } from '../lib/format'
import { CATEGORY, PRIORITY, TICKET_STATUS } from '../lib/labels'
import { Empty, ErrorBox, Loading, Num, Pager, Tag } from '../ui/bits'
import { Drawer } from '../ui/Drawer'
import { useToast } from '../ui/Toast'

const FILTERS: [TicketStatus | '', string][] = [['', 'الكل'], ['OPEN', 'مفتوحة'], ['IN_PROGRESS', 'قيد المعالجة'], ['CLOSED', 'مغلقة']]
const canAnswer = (role?: string) => role === 'SUPER_ADMIN' || role === 'ACCOUNT_MANAGER' || role === 'SUPPORT_AGENT'

/** "الدعم": the inbox of partner tickets. */
export function SupportPage() {
  const [status, setStatus] = useState<TicketStatus | ''>('OPEN')
  const [mine, setMine] = useState(false)
  const [page, setPage] = useState(0)
  const [openId, setOpenId] = useState<number | null>(null)
  const params = { status: status || undefined, mine: mine || undefined, page }
  const list = useQuery({ queryKey: ['tickets', params], queryFn: () => endpoints.tickets(params), refetchInterval: 30_000 })

  return (
    <>
      <div className="toolbar">
        <div className="chips grow">
          {FILTERS.map(([v, t]) => (
            <button key={v} className={`chip${status === v ? ' on' : ''}`} onClick={() => { setStatus(v); setPage(0) }}>{t}</button>
          ))}
        </div>
        <label style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
          <input type="checkbox" checked={mine} onChange={(e) => { setMine(e.target.checked); setPage(0) }} /> المسندة إليّ فقط
        </label>
      </div>
      {list.error && <ErrorBox error={list.error} />}
      {list.isLoading ? <Loading /> : (
        <div className="tw">
          <table>
            <thead><tr><th>الطلب</th><th>الجهة</th><th>الأولوية</th><th>الحالة</th><th>المسؤول</th><th>آخر رسالة</th></tr></thead>
            <tbody>
              {list.data?.content.map((t) => (
                <tr key={t.id} className="click" onClick={() => setOpenId(t.id)}>
                  <td className="ent"><b>{t.subject}</b><small><Num>{t.number}</Num> · {CATEGORY[t.category]}</small></td>
                  <td>{t.partnerName}</td>
                  <td><Tag label={PRIORITY[t.priority]} /></td>
                  <td><Tag label={TICKET_STATUS[t.status]} /></td>
                  <td>{t.assignedTo ?? '—'}</td>
                  <td>{ago(t.lastMessageAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {list.data?.content.length === 0 && <Empty>لا توجد طلبات هنا.</Empty>}
        </div>
      )}
      {list.data && <Pager page={list.data.page} totalPages={list.data.totalPages} onChange={setPage} />}

      <Drawer open={openId !== null} onClose={() => setOpenId(null)} title="طلب الدعم">
        {openId !== null && <TicketThread id={openId} />}
      </Drawer>
    </>
  )
}

function TicketThread({ id }: { id: number }) {
  const { me } = useAuth()
  const qc = useQueryClient()
  const toast = useToast()
  const [body, setBody] = useState('')
  const ticket = useQuery({ queryKey: ['ticket', id], queryFn: () => endpoints.ticket(id) })
  const onSaved = (t: unknown) => {
    qc.setQueryData(['ticket', id], t)
    qc.invalidateQueries({ queryKey: ['tickets'] })
    qc.invalidateQueries({ queryKey: ['summary'] })
  }
  const reply = useMutation({
    mutationFn: () => endpoints.replyTicket(id, body),
    onSuccess: (t) => { onSaved(t); setBody(''); toast('أُرسل الرد وأُبلغت الجهة') },
    onError: (e) => toast(e instanceof ApiError ? e.message : 'تعذّر الإرسال', true),
  })
  const update = useMutation({
    mutationFn: (b: { status?: TicketStatus; priority?: TicketPriority }) => endpoints.updateTicket(id, b),
    onSuccess: (t) => { onSaved(t); toast('حُفظ') },
  })

  if (ticket.isLoading) return <Loading />
  if (ticket.error) return <ErrorBox error={ticket.error} />
  const t = ticket.data!
  const answer = canAnswer(me?.role)

  return (
    <>
      <h3 style={{ margin: '0 0 4px' }}>{t.subject}</h3>
      <p className="mu" style={{ margin: '0 0 12px' }}>
        <Num>{t.number}</Num> · {t.partnerName} · {CATEGORY[t.category]}
      </p>
      {answer && (
        <div className="row2" style={{ marginBottom: 14 }}>
          <label className="field"><span>الحالة</span>
            <select className="input" value={t.status} onChange={(e) => update.mutate({ status: e.target.value as TicketStatus })}>
              {(Object.keys(TICKET_STATUS) as TicketStatus[]).map((s) => <option key={s} value={s}>{TICKET_STATUS[s].text}</option>)}
            </select>
          </label>
          <label className="field"><span>الأولوية</span>
            <select className="input" value={t.priority} onChange={(e) => update.mutate({ priority: e.target.value as TicketPriority })}>
              {(Object.keys(PRIORITY) as TicketPriority[]).map((p) => <option key={p} value={p}>{PRIORITY[p].text}</option>)}
            </select>
          </label>
        </div>
      )}
      {t.messages?.map((m, i) => (
        <article key={i} className={`msg${m.senderType === 'ADMIN' ? ' admin' : ''}`}>
          <header><b>{m.senderName}</b><span>{dateTime(m.sentAt)}</span></header>
          {/* React escapes this text: a message can never inject HTML into the dashboard. */}
          <p>{m.body}</p>
        </article>
      ))}
      {answer && (
        <form className="form" onSubmit={(e) => { e.preventDefault(); if (body.trim()) reply.mutate() }}>
          <textarea className="input" placeholder="اكتب ردك للجهة…" maxLength={5000} value={body} onChange={(e) => setBody(e.target.value)} />
          <div className="form-actions"><button className="btn" disabled={reply.isPending || !body.trim()}>إرسال الرد</button></div>
        </form>
      )}
    </>
  )
}
