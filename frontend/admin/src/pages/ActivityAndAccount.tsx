import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../api/client'
import { endpoints } from '../api/endpoints'
import { useAuth } from '../auth/AuthContext'
import { dateTime, deviceName } from '../lib/format'
import { ACTIONS, ACTOR, ADMIN_ROLE } from '../lib/labels'
import { ConfirmButton, Empty, ErrorBox, Kv, Loading, Num, Pager, Tag } from '../ui/bits'
import { useToast } from '../ui/Toast'

/** "سجل النشاط": full audit trail - who did what, when, on which record. */
export function ActivityPage() {
  const [action, setAction] = useState('')
  const [page, setPage] = useState(0)
  const params = { action: action || undefined, page }
  const list = useQuery({ queryKey: ['activity', params], queryFn: () => endpoints.activity(params) })

  return (
    <>
      <p className="lead">سجل تدقيق كامل لكل إجراء في المنصة: من نفّذه، ومتى، وعلى أي جهة.</p>
      <div className="toolbar">
        <select className="input" style={{ width: 'auto' }} value={action} onChange={(e) => { setAction(e.target.value); setPage(0) }}>
          <option value="">كل الإجراءات</option>
          {Object.entries(ACTIONS).map(([code, text]) => <option key={code} value={code}>{text}</option>)}
        </select>
      </div>
      {list.error && <ErrorBox error={list.error} />}
      {list.isLoading ? <Loading /> : (
        <div className="tw">
          <table>
            <thead><tr><th>الوقت</th><th>المنفّذ</th><th>الإجراء</th><th>السجل</th><th>التفاصيل</th><th>IP</th></tr></thead>
            <tbody>
              {list.data?.content.map((a) => (
                <tr key={a.id}>
                  <td style={{ whiteSpace: 'nowrap' }}>{dateTime(a.at)}</td>
                  <td className="ent"><b>{a.actorName}</b><small>{ACTOR[a.actorType]}</small></td>
                  <td>{ACTIONS[a.action] ?? a.action}</td>
                  <td>{a.targetType ? <code>{a.targetType} #{a.targetId ?? '—'}</code> : '—'}</td>
                  {/* dir="auto": English details keep their own left-to-right order inside the Arabic page */}
                  <td dir="auto">{a.details ?? '—'}</td>
                  <td><Num>{a.ipAddress ?? '—'}</Num></td>
                </tr>
              ))}
            </tbody>
          </table>
          {list.data?.content.length === 0 && <Empty>لا يوجد نشاط مطابق.</Empty>}
        </div>
      )}
      {list.data && <Pager page={list.data.page} totalPages={list.data.totalPages} onChange={setPage} />}
    </>
  )
}

/** "حسابي والأجهزة": my profile, my logged-in devices, change password. */
export function AccountPage() {
  const { me } = useAuth()
  const qc = useQueryClient()
  const toast = useToast()
  const sessions = useQuery({ queryKey: ['sessions'], queryFn: endpoints.sessions })
  const refreshSessions = () => qc.invalidateQueries({ queryKey: ['sessions'] })
  const revoke = useMutation({ mutationFn: endpoints.revokeSession, onSuccess: () => { refreshSessions(); toast('سُجّل خروج الجهاز') } })
  const revokeOthers = useMutation({
    mutationFn: endpoints.revokeOtherSessions,
    onSuccess: (r) => { refreshSessions(); toast(`سُجّل الخروج من ${r.revoked} جهاز`) },
  })

  return (
    <div className="cols">
      <section>
        <h2>الأجهزة المسجّل دخولها</h2>
        <div className="panel">
          {sessions.isLoading && <Loading />}
          {sessions.data?.map((s) => (
            <div className="li" key={s.id}>
              <span>
                <b>{deviceName(s.userAgent)}</b> {s.current && <Tag label={{ text: 'هذا الجهاز', tone: 'ok' }} />}
                <div className="mu">آخر استخدام {dateTime(s.lastUsedAt)} · <Num>{s.ipAddress ?? ''}</Num></div>
              </span>
              {!s.current && <ConfirmButton className="btn gh sm" onConfirm={() => revoke.mutate(s.id)}>تسجيل خروج</ConfirmButton>}
            </div>
          ))}
          {sessions.data && sessions.data.length > 1 && (
            <div className="form-actions" style={{ marginTop: 12 }}>
              <ConfirmButton onConfirm={() => revokeOthers.mutate()}>تسجيل الخروج من كل الأجهزة الأخرى</ConfirmButton>
            </div>
          )}
        </div>
      </section>
      <section>
        <h2>حسابي</h2>
        <div className="panel">
          <Kv k="الاسم">{me?.fullName}</Kv>
          <Kv k="البريد"><Num>{me?.email}</Num></Kv>
          <Kv k="الدور">{me ? ADMIN_ROLE[me.role] : ''}</Kv>
        </div>
        <h2 className="section">تغيير كلمة المرور</h2>
        <ChangePassword />
      </section>
    </div>
  )
}

function ChangePassword() {
  const toast = useToast()
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState<string | null>(null)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    try {
      await endpoints.changePassword(current, next, confirm)
      setCurrent(''); setNext(''); setConfirm('')
      toast('تم تغيير كلمة المرور وتسجيل الخروج من أجهزتك الأخرى')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'تعذّر الحفظ')
    }
  }

  return (
    <form className="form panel" onSubmit={submit}>
      <label className="field"><span>كلمة المرور الحالية</span><input className="input" type="password" dir="ltr" required autoComplete="current-password" value={current} onChange={(e) => setCurrent(e.target.value)} /></label>
      <label className="field"><span>الجديدة (10 أحرف على الأقل)</span><input className="input" type="password" dir="ltr" minLength={10} required autoComplete="new-password" value={next} onChange={(e) => setNext(e.target.value)} /></label>
      <label className="field"><span>تأكيد الجديدة</span><input className="input" type="password" dir="ltr" minLength={10} required autoComplete="new-password" value={confirm} onChange={(e) => setConfirm(e.target.value)} /></label>
      {error && <ErrorBox error={new ApiError(400, { status: 400, detail: error })} />}
      <div className="form-actions"><button className="btn">حفظ</button></div>
    </form>
  )
}
