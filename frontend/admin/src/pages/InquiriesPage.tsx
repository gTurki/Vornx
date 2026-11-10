import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../api/client'
import { endpoints } from '../api/endpoints'
import type { Inquiry, InquiryInterest, InquiryStatus } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { ago, dateTime } from '../lib/format'
import { Empty, ErrorBox, Kv, Loading, Num, Pager, Tag } from '../ui/bits'
import { Drawer } from '../ui/Drawer'
import { useToast } from '../ui/Toast'

const INTEREST: Record<InquiryInterest, string> = {
  TECHNICAL_INTEGRATION: 'تكامل تقني', ENTERPRISE: 'تعاون مؤسسي', PILOT: 'إثبات مفهوم وتجارب',
  STRATEGIC: 'شراكة استراتيجية واستثمار', OTHER: 'أخرى',
}
const FILTERS: [InquiryStatus | '', string][] = [['NEW', 'جديدة'], ['HANDLED', 'تمت متابعتها'], ['', 'الكل']]
const canAnswer = (role?: string) => role === 'SUPER_ADMIN' || role === 'ACCOUNT_MANAGER' || role === 'SUPPORT_AGENT'

/** "رسائل الموقع": what visitors sent through the public website's contact form. */
export function InquiriesPage() {
  const [status, setStatus] = useState<InquiryStatus | ''>('NEW')
  const [page, setPage] = useState(0)
  const [open, setOpen] = useState<Inquiry | null>(null)
  const params = { status: status || undefined, page }
  const list = useQuery({ queryKey: ['inquiries', params], queryFn: () => endpoints.inquiries(params) })

  return (
    <>
      <p className="lead">رسائل الزوار من نموذج «تواصل معنا» في الموقع العام. ردّ على المرسل من بريد VORNX، ثم علّمها كـ«تمت متابعتها».</p>
      <div className="chips" style={{ marginBottom: 14 }}>
        {FILTERS.map(([v, t]) => (
          <button key={v} className={`chip${status === v ? ' on' : ''}`} onClick={() => { setStatus(v); setPage(0) }}>{t}</button>
        ))}
      </div>
      {list.error && <ErrorBox error={list.error} />}
      {list.isLoading ? <Loading /> : (
        <div className="tw">
          <table>
            <thead><tr><th>المرسل</th><th>الجهة</th><th>النوع</th><th>وصلت</th><th>الحالة</th></tr></thead>
            <tbody>
              {list.data?.content.map((i) => (
                <tr key={i.id} className="click" onClick={() => setOpen(i)}>
                  <td className="ent"><b>{i.name}</b><small><Num>{i.email}</Num></small></td>
                  <td className="ent"><b>{i.organization}</b><small>{i.jobTitle ?? ''}</small></td>
                  <td>{INTEREST[i.interest]}</td>
                  <td>{ago(i.createdAt)}</td>
                  <td><Tag label={i.status === 'NEW' ? { text: 'جديدة', tone: 'soon' } : { text: 'تمت متابعتها', tone: 'ok' }} /></td>
                </tr>
              ))}
            </tbody>
          </table>
          {list.data?.content.length === 0 && <Empty>لا توجد رسائل هنا.</Empty>}
        </div>
      )}
      {list.data && <Pager page={list.data.page} totalPages={list.data.totalPages} onChange={setPage} />}

      <Drawer open={open !== null} onClose={() => setOpen(null)} title="رسالة من الموقع">
        {open && <InquiryDetails inquiry={open} onChanged={setOpen} />}
      </Drawer>
    </>
  )
}

function InquiryDetails({ inquiry: i, onChanged }: { inquiry: Inquiry; onChanged: (i: Inquiry) => void }) {
  const { me } = useAuth()
  const qc = useQueryClient()
  const toast = useToast()
  const update = useMutation({
    mutationFn: (s: InquiryStatus) => endpoints.setInquiryStatus(i.id, s),
    onSuccess: (r) => { onChanged(r); qc.invalidateQueries({ queryKey: ['inquiries'] }); toast('حُفظ') },
    onError: (e) => toast(e instanceof ApiError ? e.message : 'تعذّر الحفظ', true),
  })
  // Reply from the team's mail app; the subject keeps the thread recognizable.
  const subject = encodeURIComponent(i.language === 'AR' ? 'رد VORNX على استفسارك' : 'VORNX: reply to your inquiry')

  return (
    <>
      <Kv k="الاسم">{i.name}</Kv>
      <Kv k="البريد"><Num>{i.email}</Num></Kv>
      <Kv k="الجهة">{i.organization}</Kv>
      {i.jobTitle && <Kv k="المسمى">{i.jobTitle}</Kv>}
      <Kv k="النوع">{INTEREST[i.interest]}</Kv>
      <Kv k="اللغة">{i.language === 'AR' ? 'العربية' : 'English'}</Kv>
      <Kv k="وصلت">{dateTime(i.createdAt)}</Kv>
      {i.handledBy && <Kv k="تابعها">{i.handledBy} · {dateTime(i.handledAt)}</Kv>}
      {/* dir="auto": an English message keeps its own direction. React escapes the text: no HTML from visitors. */}
      <p dir="auto" style={{ whiteSpace: 'pre-wrap', background: 'var(--bg)', padding: 12, borderRadius: 8 }}>{i.message}</p>
      <div className="form-actions">
        <a className="btn" href={`mailto:${i.email}?subject=${subject}`}>رد بالبريد</a>
        {canAnswer(me?.role) && (i.status === 'NEW'
          ? <button className="btn gh" disabled={update.isPending} onClick={() => update.mutate('HANDLED')}>تمت متابعتها</button>
          : <button className="btn gh" disabled={update.isPending} onClick={() => update.mutate('NEW')}>إعادة فتح</button>)}
      </div>
    </>
  )
}
