import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../api/client'
import { endpoints } from '../api/endpoints'
import { useAuth } from '../auth/AuthContext'
import { dateTime, deviceName } from '../lib/format'
import { ConfirmButton, ErrorBox, Kv, Loading, Num, Tag } from '../ui/bits'
import { useToast } from '../ui/Toast'

/** "حسابي": my profile, my logged-in devices, change password. */
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
          <Kv k="الاسم">{me?.firstName} {me?.lastName}</Kv>
          <Kv k="البريد"><Num>{me?.email}</Num></Kv>
          <Kv k="رقم VORNX"><Num>{me?.vornxId}</Num></Kv>
          <Kv k="الجوال"><Num>{me?.phone}</Num></Kv>
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
