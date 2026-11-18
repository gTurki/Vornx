import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../api/client'
import { endpoints } from '../api/endpoints'
import type { ApplicationStatus, PartnerApplication } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { ago, dateTime } from '../lib/format'
import { SECTORS } from '../lib/labels'
import { Empty, ErrorBox, Kv, Loading, Num, Pager, Tag } from '../ui/bits'
import { Drawer } from '../ui/Drawer'
import { SectorIcon } from '../ui/SectorIcon'
import { useToast } from '../ui/Toast'

const FILTERS: [ApplicationStatus | '', string][] = [['PENDING', 'بانتظار القرار'], ['APPROVED', 'مقبولة'], ['REJECTED', 'مرفوضة'], ['', 'الكل']]
const STATUS: Record<ApplicationStatus, { text: string; tone: 'ok' | 'soon' | 'bad' }> = {
  PENDING: { text: 'بانتظار القرار', tone: 'soon' }, APPROVED: { text: 'مقبولة', tone: 'ok' }, REJECTED: { text: 'مرفوضة', tone: 'bad' },
}
const canDecide = (role?: string) => role === 'SUPER_ADMIN' || role === 'ACCOUNT_MANAGER'

/**
 * "طلبات الانضمام": companies that applied on the website ("انضم كشريك").
 * Approve = the partner is created (onboarding) and its manager gets an invite email. Reject = a polite email with the reason.
 */
export function ApplicationsPage() {
  const [status, setStatus] = useState<ApplicationStatus | ''>('PENDING')
  const [page, setPage] = useState(0)
  const [open, setOpen] = useState<PartnerApplication | null>(null)
  const params = { status: status || undefined, page }
  const list = useQuery({ queryKey: ['applications', params], queryFn: () => endpoints.applications(params) })

  return (
    <>
      <p className="lead">
        الجهات التي طلبت الانضمام من الموقع. تحقق من السجل التجاري وتواصل مع الجهة، ثم اقبل الطلب: تُنشأ الجهة وتصل
        دعوة لمديرها لتعيين كلمة المرور، أو ارفضه مع ذكر السبب.
      </p>
      <div className="chips" style={{ marginBottom: 14 }}>
        {FILTERS.map(([v, t]) => (
          <button key={v} className={`chip${status === v ? ' on' : ''}`} onClick={() => { setStatus(v); setPage(0) }}>{t}</button>
        ))}
      </div>
      {list.error && <ErrorBox error={list.error} />}
      {list.isLoading ? <Loading /> : (
        <div className="tw">
          <table>
            <thead><tr><th>الجهة</th><th>الخدمة</th><th>السجل التجاري</th><th>مقدّم الطلب</th><th>وصل</th><th>الحالة</th></tr></thead>
            <tbody>
              {list.data?.content.map((a) => (
                <tr key={a.id} className="click" onClick={() => setOpen(a)}>
                  <td><div className="ent"><SectorIcon sector={a.sector} /><div><b>{a.companyName}</b><small>{a.city}</small></div></div></td>
                  <td>{SECTORS[a.sector]}</td>
                  <td><Num>{a.crNumber}</Num></td>
                  <td className="ent"><b>{a.contactName}</b><small><Num>{a.email}</Num></small></td>
                  <td>{ago(a.createdAt)}</td>
                  <td><Tag label={STATUS[a.status]} /></td>
                </tr>
              ))}
            </tbody>
          </table>
          {list.data?.content.length === 0 && <Empty>لا توجد طلبات هنا.</Empty>}
        </div>
      )}
      {list.data && <Pager page={list.data.page} totalPages={list.data.totalPages} onChange={setPage} />}

      <Drawer open={open !== null} onClose={() => setOpen(null)} title="طلب انضمام">
        {open && <ApplicationDetails app={open} onChanged={setOpen} />}
      </Drawer>
    </>
  )
}

function ApplicationDetails({ app: a, onChanged }: { app: PartnerApplication; onChanged: (a: PartnerApplication) => void }) {
  const { me } = useAuth()
  const qc = useQueryClient()
  const toast = useToast()
  const navigate = useNavigate()
  const [rejecting, setRejecting] = useState(false)
  const [reason, setReason] = useState('')
  const refresh = () => {
    qc.invalidateQueries({ queryKey: ['applications'] })
    qc.invalidateQueries({ queryKey: ['applications-pending'] })
    qc.invalidateQueries({ queryKey: ['partners'] })
    qc.invalidateQueries({ queryKey: ['overview'] })
  }
  const onError = (e: unknown) => toast(e instanceof ApiError ? e.message : 'تعذّر الحفظ', true)
  const approve = useMutation({
    mutationFn: () => endpoints.approveApplication(a.id),
    onSuccess: (r) => { onChanged(r.application); refresh(); toast(`أُنشئت الجهة وأُرسلت دعوة إلى ${a.email}`) },
    onError,
  })
  const reject = useMutation({
    mutationFn: () => endpoints.rejectApplication(a.id, reason.trim()),
    onSuccess: (r) => { onChanged(r); refresh(); setRejecting(false); toast('رُفض الطلب وأُرسل السبب بالبريد') },
    onError,
  })

  return (
    <>
      <div className="li"><b>{a.companyName}</b><Tag label={STATUS[a.status]} /></div>
      <Kv k="الخدمة">{SECTORS[a.sector]}</Kv>
      <Kv k="السجل التجاري"><Num>{a.crNumber}</Num></Kv>
      <Kv k="المدينة">{a.city}</Kv>
      <Kv k="مقدّم الطلب">{a.contactName}{a.jobTitle ? ` · ${a.jobTitle}` : ''}</Kv>
      <Kv k="البريد"><a href={`mailto:${a.email}`}><Num>{a.email}</Num></a></Kv>
      <Kv k="الجوال"><a href={`tel:${a.phone}`}><Num>{a.phone}</Num></a></Kv>
      <Kv k="وصل">{dateTime(a.createdAt)}</Kv>
      {a.reviewedBy && <Kv k={a.status === 'APPROVED' ? 'قبله' : 'رفضه'}>{a.reviewedBy} · {dateTime(a.reviewedAt)}</Kv>}
      {a.message && <p dir="auto" style={{ whiteSpace: 'pre-wrap', background: 'var(--bg)', padding: 12, borderRadius: 8 }}>{a.message}</p>}
      {a.rejectReason && <p className="mu">سبب الرفض: {a.rejectReason}</p>}

      {a.status === 'APPROVED' && a.partnerId && (
        <div className="form-actions"><button className="btn gh" onClick={() => navigate(`/partners?q=${encodeURIComponent(a.companyName)}`)}>فتح الجهة</button></div>
      )}

      {a.status === 'PENDING' && canDecide(me?.role) && !rejecting && (
        <>
          <p className="mu" style={{ fontSize: 13 }}>
            القبول يُنشئ الجهة (قيد التهيئة) وأنت مدير حسابها، ويرسل إلى {a.contactName} دعوة لتعيين كلمة المرور. أضف العقد بعدها من «الجهات».
          </p>
          <div className="form-actions">
            <button className="btn" disabled={approve.isPending} onClick={() => approve.mutate()}>{approve.isPending ? 'جارٍ القبول…' : 'قبول وإرسال الدعوة'}</button>
            <button className="btn gh" onClick={() => setRejecting(true)}>رفض</button>
          </div>
        </>
      )}
      {a.status === 'PENDING' && rejecting && (
        <form className="form" onSubmit={(e) => { e.preventDefault(); if (reason.trim()) reject.mutate() }}>
          <label className="field"><span>سبب الرفض (يصل لمقدّم الطلب بالبريد)</span>
            <textarea className="input" required maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)}
              placeholder="مثال: السجل التجاري غير ساري. يسعدنا استقبال طلب جديد بعد تجديده." />
          </label>
          <div className="form-actions">
            <button className="btn danger" disabled={reject.isPending || !reason.trim()}>إرسال الرفض</button>
            <button type="button" className="btn gh" onClick={() => setRejecting(false)}>إلغاء</button>
          </div>
        </form>
      )}
    </>
  )
}
