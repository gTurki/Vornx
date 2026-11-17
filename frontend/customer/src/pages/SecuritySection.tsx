import { useEffect, useState, type FormEvent } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../api/client'
import { endpoints } from '../api/endpoints'
import type { TotpSetup } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { dateTime } from '../lib/format'
import { Kv, Loading, Tag } from '../ui/bits'
import { useToast } from '../ui/Toast'
import { digitsOnly } from './AuthPages'
import { PasswordInput } from '../ui/PasswordInput'

function msg(e: unknown) {
  return e instanceof ApiError ? e.message : 'حدث خطأ غير متوقع.'
}

/** "حسابي" > الأمان: verified email / mobile, and the authenticator app. */
export function SecuritySection() {
  const { me } = useAuth()
  return (
    <>
      <h2 className="section">الأمان</h2>
      <div className="panel">
        <Kv k="البريد الإلكتروني">
          {me?.emailVerified ? <Tag label={{ text: 'مؤكَّد', tone: 'ok' }} /> : <Tag label={{ text: 'غير مؤكَّد', tone: 'soon' }} />}
        </Kv>
        <Kv k="رقم الجوال">
          {me?.phoneVerified ? <Tag label={{ text: 'مؤكَّد عبر واتساب', tone: 'ok' }} /> : <Tag label={{ text: 'غير مؤكَّد', tone: 'mu' }} />}
        </Kv>
        <Authenticator />
      </div>
    </>
  )
}

/**
 * Turn the authenticator app on: 1) scan the QR code (or type the key), 2) type the first code,
 * 3) save the 10 recovery codes (shown only now). Off: needs the password.
 */
function Authenticator() {
  const qc = useQueryClient()
  const toast = useToast()
  const status = useQuery({ queryKey: ['totp'], queryFn: endpoints.totpStatus })
  const [setup, setSetup] = useState<TotpSetup | null>(null)
  const [recovery, setRecovery] = useState<string[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const refresh = () => {
    qc.invalidateQueries({ queryKey: ['totp'] })
    qc.invalidateQueries({ queryKey: ['me'] })
  }

  async function start() {
    setError(null)
    setBusy(true)
    try {
      setSetup(await endpoints.totpSetup())
    } catch (e) {
      setError(msg(e))
    } finally {
      setBusy(false)
    }
  }

  if (status.isLoading) return <Loading />

  if (recovery) {
    return (
      <div className="totp">
        <b>تم تفعيل تطبيق المصادقة ✅</b>
        <p className="mu">
          احفظ رموز الاسترداد هذه في مكان آمن (خارج جوالك). إذا فقدت جوالك، يعمل كل رمز مرة واحدة بدل رمز التطبيق.
          <b> لن تظهر مرة أخرى.</b>
        </p>
        <ol className="recovery" dir="ltr">{recovery.map((r) => <li key={r}><code>{r}</code></li>)}</ol>
        <div className="form-actions">
          <button className="btn gh sm" type="button" onClick={() => {
            navigator.clipboard?.writeText(recovery.join('\n')).then(() => toast('نُسخت الرموز'), () => undefined)
          }}>نسخ الرموز</button>
          <button className="btn sm" type="button" onClick={() => { setRecovery(null); refresh() }}>حفظتها</button>
        </div>
      </div>
    )
  }

  if (status.data?.enabled) {
    return (
      <div className="totp">
        <Kv k="تطبيق المصادقة"><Tag label={{ text: 'مفعّل', tone: 'ok' }} /></Kv>
        <p className="mu">
          منذ {status.data.enabledAt ? dateTime(status.data.enabledAt) : '-'} · رموز استرداد متبقية: {status.data.recoveryCodesLeft}.
          {' '}يطلب تسجيل الدخول الرمز من التطبيق بدل البريد.
        </p>
        <TurnOff onDone={() => { refresh(); toast('أُوقف تطبيق المصادقة') }} />
      </div>
    )
  }

  if (setup) {
    return <SetupSteps setup={setup} onCancel={() => setSetup(null)} onEnabled={(codes) => { setSetup(null); setRecovery(codes) }} />
  }

  return (
    <div className="totp">
      <Kv k="تطبيق المصادقة"><Tag label={{ text: 'غير مفعّل', tone: 'mu' }} /></Kv>
      <p className="mu">
        حماية أقوى: يطلب تسجيل الدخول رمزًا من تطبيق على جوالك (Google Authenticator أو Microsoft Authenticator) بدل
        الرمز الذي يصل بالبريد. مجاني ويعمل بدون إنترنت.
      </p>
      {error && <div className="error-box">{error}</div>}
      <div className="form-actions"><button className="btn sm" disabled={busy} onClick={start}>تفعيل تطبيق المصادقة</button></div>
    </div>
  )
}

function SetupSteps({ setup, onCancel, onEnabled }: { setup: TotpSetup; onCancel: () => void; onEnabled: (codes: string[]) => void }) {
  const [qr, setQr] = useState<string | null>(null)
  const [code, setCode] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  // The QR library is loaded only here (not in the main bundle). The QR is drawn in the browser: the secret
  // never goes to a third-party QR service.
  useEffect(() => {
    let alive = true
    import('qrcode')
      .then((q) => q.toDataURL(setup.otpauthUri, { margin: 1, width: 200, errorCorrectionLevel: 'M' }))
      .then((url) => { if (alive) setQr(url) })
      .catch(() => undefined)
    return () => { alive = false }
  }, [setup.otpauthUri])

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (code.length !== 6) return
    setBusy(true)
    setError(null)
    try {
      onEnabled((await endpoints.totpEnable(code)).recoveryCodes)
    } catch (err) {
      setError(msg(err))
      setCode('')
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="totp form" onSubmit={submit}>
      <b>1. امسح الرمز بتطبيق المصادقة</b>
      <div className="qr">{qr ? <img src={qr} width={200} height={200} alt="رمز QR لتطبيق المصادقة" /> : <Loading />}</div>
      <p className="mu">
        على نفس الجوال؟ <a href={setup.otpauthUri}>افتح في تطبيق المصادقة</a>، أو اكتب هذا المفتاح يدويًا:
      </p>
      <code className="secret" dir="ltr">{setup.secret.match(/.{1,4}/g)?.join(' ')}</code>
      <label className="field"><span>2. اكتب الرمز الذي يظهر في التطبيق</span>
        <input className="input otp" dir="ltr" inputMode="numeric" autoComplete="one-time-code" maxLength={12} required
          value={code} onChange={(e) => setCode(digitsOnly(e.target.value))} placeholder="••••••" />
      </label>
      {error && <div className="error-box">{error}</div>}
      <div className="form-actions">
        <button className="btn sm" disabled={busy || code.length !== 6}>{busy ? 'جارٍ التحقق…' : 'تفعيل'}</button>
        <button className="btn gh sm" type="button" onClick={onCancel}>إلغاء</button>
      </div>
    </form>
  )
}

function TurnOff({ onDone }: { onDone: () => void }) {
  const [open, setOpen] = useState(false)
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  if (!open) {
    return <div className="form-actions"><button className="btn gh sm" onClick={() => setOpen(true)}>إيقاف تطبيق المصادقة</button></div>
  }

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await endpoints.totpDisable(password)
      onDone()
    } catch (err) {
      setError(msg(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="form" onSubmit={submit}>
      <label className="field"><span>للتأكيد، اكتب كلمة المرور</span>
        <PasswordInput className="input" dir="ltr" required autoComplete="current-password" value={password}
          onChange={(e) => setPassword(e.target.value)} />
      </label>
      {error && <div className="error-box">{error}</div>}
      <div className="form-actions">
        <button className="btn sm" disabled={busy}>إيقاف</button>
        <button className="btn gh sm" type="button" onClick={() => setOpen(false)}>إلغاء</button>
      </div>
    </form>
  )
}
