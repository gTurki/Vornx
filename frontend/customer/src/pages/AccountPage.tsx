import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../api/client'
import { endpoints } from '../api/endpoints'
import { useAuth } from '../auth/AuthContext'
import { dateTime, deviceName } from '../lib/format'
import { ConfirmButton, ErrorBox, Kv, Loading, Num, Tag } from '../ui/bits'
import { useToast } from '../ui/Toast'
import { Icon } from '../ui/Icon'
import { SecuritySection } from './SecuritySection'
import { PasswordInput } from '../ui/PasswordInput'
import { tr } from '../i18n'

/** "حسابي": my profile, security (verified email/mobile, authenticator app), my logged-in devices, change password. */
export function AccountPage() {
  const { me, logout } = useAuth()
  const qc = useQueryClient()
  const toast = useToast()
  const sessions = useQuery({ queryKey: ['sessions'], queryFn: endpoints.sessions })
  const refreshSessions = () => qc.invalidateQueries({ queryKey: ['sessions'] })
  const revoke = useMutation({ mutationFn: endpoints.revokeSession, onSuccess: () => { refreshSessions(); toast(tr('سُجّل خروج الجهاز')) } })
  const revokeOthers = useMutation({
    mutationFn: endpoints.revokeOtherSessions,
    onSuccess: (r) => { refreshSessions(); toast(tr('سُجّل الخروج من {0} جهاز', { 0: r.revoked })) },
  })

  return (
    <div className="cols">
      <section>
        <h2>{tr('الأجهزة المسجّل دخولها')}</h2>
        <div className="panel">
          {sessions.isLoading && <Loading />}
          {sessions.data?.map((s) => (
            <div className="li" key={s.id}>
              <span>
                <b>{deviceName(s.userAgent)}</b> {s.current && <Tag label={{ text: tr('هذا الجهاز'), tone: 'ok' }} />}
                <div className="mu">{tr('آخر استخدام')}{' '}{dateTime(s.lastUsedAt)} · <Num>{s.ipAddress ?? ''}</Num></div>
              </span>
              {!s.current && <ConfirmButton className="btn gh sm" onConfirm={() => revoke.mutate(s.id)}>{tr('تسجيل خروج')}</ConfirmButton>}
            </div>
          ))}
          {sessions.data && sessions.data.length > 1 && (
            <div className="form-actions" style={{ marginTop: 12 }}>
              <ConfirmButton onConfirm={() => revokeOthers.mutate()}>{tr('تسجيل الخروج من كل الأجهزة الأخرى')}</ConfirmButton>
            </div>
          )}
        </div>
      </section>
      <section>
        <h2>{tr('حسابي')}</h2>
        <div className="panel">
          <Kv k={tr('الاسم')}>{me?.firstName} {me?.lastName}</Kv>
          <Kv k={tr('البريد')}><Num>{me?.email}</Num></Kv>
          <Kv k={tr('رقم VORNX')}><Num>{me?.vornxId}</Num></Kv>
          <Kv k={tr('الجوال')}><Num>{me?.phone}</Num></Kv>
        </div>
        <SecuritySection />
        <h2 className="section">{tr('تغيير كلمة المرور')}</h2>
        <ChangePassword />
        {/* a clear way out on every device (on phones the sidebar is hidden) */}
        <button className="btn gh wide signout" onClick={logout}><Icon name="logout" />{tr('تسجيل الخروج')}</button>
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
      toast(tr('تم تغيير كلمة المرور وتسجيل الخروج من أجهزتك الأخرى'))
    } catch (err) {
      setError(err instanceof ApiError ? err.message : tr('تعذّر الحفظ'))
    }
  }

  return (
    <form className="form panel" onSubmit={submit}>
      <label className="field"><span>{tr('كلمة المرور الحالية')}</span><PasswordInput className="input" dir="ltr" required autoComplete="current-password" value={current} onChange={(e) => setCurrent(e.target.value)} /></label>
      <label className="field"><span>{tr('الجديدة (10 أحرف على الأقل)')}</span><PasswordInput className="input" dir="ltr" minLength={10} required autoComplete="new-password" value={next} onChange={(e) => setNext(e.target.value)} /></label>
      <label className="field"><span>{tr('تأكيد الجديدة')}</span><PasswordInput className="input" dir="ltr" minLength={10} required autoComplete="new-password" value={confirm} onChange={(e) => setConfirm(e.target.value)} /></label>
      {error && <ErrorBox error={new ApiError(400, { status: 400, detail: error })} />}
      <div className="form-actions"><button className="btn">{tr('حفظ')}</button></div>
    </form>
  )
}
