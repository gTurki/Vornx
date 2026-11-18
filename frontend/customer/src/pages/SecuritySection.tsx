import { useEffect, useState, type FormEvent } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError } from '../api/client'
import { endpoints } from '../api/endpoints'
import type { TotpSetup } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { dateTime } from '../lib/format'
import { ConfirmButton, Kv, Loading, Tag } from '../ui/bits'
import { useToast } from '../ui/Toast'
import { digitsOnly, FaceIcon } from './AuthPages'
import { PasswordInput } from '../ui/PasswordInput'
import { tr } from '../i18n'
import { alreadyOnDevice, biometricName, createPasskey, deviceHasBiometrics, passkeySupported, wasCancelled } from '../lib/passkey'

function msg(e: unknown) {
  return e instanceof ApiError ? e.message : tr('حدث خطأ غير متوقع.')
}

/** "حسابي" > الأمان: verified email / mobile, and the authenticator app. */
export function SecuritySection() {
  const { me } = useAuth()
  return (
    <>
      <h2 className="section">{tr('الأمان')}</h2>
      <div className="panel">
        <Kv k={tr('البريد الإلكتروني')}>
          {me?.emailVerified ? <Tag label={{ text: tr('مؤكَّد'), tone: 'ok' }} /> : <Tag label={{ text: tr('غير مؤكَّد'), tone: 'soon' }} />}
        </Kv>
        <Kv k={tr('رقم الجوال')}>
          {me?.phoneVerified ? <Tag label={{ text: tr('مؤكَّد عبر واتساب'), tone: 'ok' }} /> : <Tag label={{ text: tr('غير مؤكَّد'), tone: 'mu' }} />}
        </Kv>
        <Passkeys />
        <Authenticator />
      </div>
    </>
  )
}

/**
 * Face ID / fingerprint sign-in (passkeys): add this device, see the devices that can sign in, remove one.
 * A passkey made on an iPhone usually syncs to the person's other Apple devices through iCloud Keychain ("متزامن").
 */
function Passkeys() {
  const qc = useQueryClient()
  const toast = useToast()
  const list = useQuery({ queryKey: ['passkeys'], queryFn: endpoints.passkeys, enabled: passkeySupported() })
  const [canAdd, setCanAdd] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  useEffect(() => { deviceHasBiometrics().then(setCanAdd) }, [])

  if (!passkeySupported()) return null
  const label = biometricName() === 'faceid' ? 'Face ID' : biometricName() === 'hello' ? 'Windows Hello' : tr('البصمة')

  async function add() {
    setBusy(true)
    setError(null)
    try {
      const o = await endpoints.passkeyOptions()
      await endpoints.addPasskey(o.challengeId, await createPasskey(o.options))
      qc.invalidateQueries({ queryKey: ['passkeys'] })
      toast(tr('تم. من الآن ادخل بـ {0} بدون كلمة مرور', { 0: label }))
    } catch (e) {
      if (alreadyOnDevice(e)) setError(tr('هذا الجهاز مضاف مسبقًا.'))
      else if (!wasCancelled(e)) setError(msg(e))
    } finally {
      setBusy(false)
    }
  }

  async function remove(id: number) {
    try {
      await endpoints.removePasskey(id)
      qc.invalidateQueries({ queryKey: ['passkeys'] })
      toast(tr('أُزيل الجهاز'))
    } catch (e) {
      toast(msg(e), true)
    }
  }

  const items = list.data ?? []
  return (
    <div className="totp">
      <Kv k={tr('Face ID والبصمة')}>
        {items.length ? <Tag label={{ text: tr('مفعّل'), tone: 'ok' }} /> : <Tag label={{ text: tr('غير مفعّل'), tone: 'mu' }} />}
      </Kv>
      <p className="mu">{tr('ادخل بلمسة واحدة بـ Face ID أو البصمة، بدون كلمة مرور ولا رمز. بصمتك ووجهك لا يغادران جهازك: نحتفظ فقط بمفتاح عام لا يمكن استخدامه للدخول.')}</p>
      {items.length > 0 && (
        <div className="pk-list">
          {items.map((p) => (
            <div className="li" key={p.id}>
              <span>
                <b>{p.name}</b>{p.synced && <span className="mu">{' · '}{tr('متزامن')}</span>}
                <small className="mu">{tr('أُضيف')}{' '}{dateTime(p.createdAt)}{p.lastUsedAt ? ' · ' + tr('آخر دخول') + ' ' + dateTime(p.lastUsedAt) : ''}</small>
              </span>
              <ConfirmButton className="btn gh sm" onConfirm={() => remove(p.id)}>{tr('إزالة')}</ConfirmButton>
            </div>
          ))}
        </div>
      )}
      {error && <div className="error-box">{error}</div>}
      {canAdd ? (
        <div className="form-actions">
          <button className="btn sm face" disabled={busy} onClick={add}>
            <FaceIcon />{busy ? tr('جارٍ الإضافة…') : tr('إضافة هذا الجهاز ({0})', { 0: label })}
          </button>
        </div>
      ) : (
        <p className="mu" style={{ fontSize: 13 }}>{tr('هذا الجهاز لا يدعم Face ID أو البصمة. افتح VORNX من جوالك لإضافته.')}</p>
      )}
    </div>
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
        <b>{tr('تم تفعيل تطبيق المصادقة ✅')}</b>
        <p className="mu">{tr('احفظ رموز الاسترداد هذه في مكان آمن (خارج جوالك). إذا فقدت جوالك، يعمل كل رمز مرة واحدة بدل رمز التطبيق.')}<b>{' '}{tr('لن تظهر مرة أخرى.')}</b>
        </p>
        <ol className="recovery" dir="ltr">{recovery.map((r) => <li key={r}><code>{r}</code></li>)}</ol>
        <div className="form-actions">
          <button className="btn gh sm" type="button" onClick={() => {
            navigator.clipboard?.writeText(recovery.join('\n')).then(() => toast(tr('نُسخت الرموز')), () => undefined)
          }}>{tr('نسخ الرموز')}</button>
          <button className="btn sm" type="button" onClick={() => { setRecovery(null); refresh() }}>{tr('حفظتها')}</button>
        </div>
      </div>
    )
  }

  if (status.data?.enabled) {
    return (
      <div className="totp">
        <Kv k={tr('تطبيق المصادقة')}><Tag label={{ text: tr('مفعّل'), tone: 'ok' }} /></Kv>
        <p className="mu">{tr('منذ')}{' '}{status.data.enabledAt ? dateTime(status.data.enabledAt) : '-'}{' '}{tr('· رموز استرداد متبقية:')}{' '}{status.data.recoveryCodesLeft}.
          {' '}{tr('يطلب تسجيل الدخول الرمز من التطبيق بدل البريد.')}</p>
        <TurnOff onDone={() => { refresh(); toast(tr('أُوقف تطبيق المصادقة')) }} />
      </div>
    )
  }

  if (setup) {
    return <SetupSteps setup={setup} onCancel={() => setSetup(null)} onEnabled={(codes) => { setSetup(null); setRecovery(codes) }} />
  }

  return (
    <div className="totp">
      <Kv k={tr('تطبيق المصادقة')}><Tag label={{ text: tr('غير مفعّل'), tone: 'mu' }} /></Kv>
      <p className="mu">{tr('حماية أقوى: يطلب تسجيل الدخول رمزًا من تطبيق على جوالك (Google Authenticator أو Microsoft Authenticator) بدل الرمز الذي يصل بالبريد. مجاني ويعمل بدون إنترنت.')}</p>
      {error && <div className="error-box">{error}</div>}
      <div className="form-actions"><button className="btn sm" disabled={busy} onClick={start}>{tr('تفعيل تطبيق المصادقة')}</button></div>
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
      <b>{tr('1. امسح الرمز بتطبيق المصادقة')}</b>
      <div className="qr">{qr ? <img src={qr} width={200} height={200} alt={tr('رمز QR لتطبيق المصادقة')} /> : <Loading />}</div>
      <p className="mu">{tr('على نفس الجوال؟')}{' '}<a href={setup.otpauthUri}>{tr('افتح في تطبيق المصادقة')}</a>{tr('، أو اكتب هذا المفتاح يدويًا:')}</p>
      <code className="secret" dir="ltr">{setup.secret.match(/.{1,4}/g)?.join(' ')}</code>
      <label className="field"><span>{tr('2. اكتب الرمز الذي يظهر في التطبيق')}</span>
        <input className="input otp" dir="ltr" inputMode="numeric" autoComplete="one-time-code" maxLength={12} required
          value={code} onChange={(e) => setCode(digitsOnly(e.target.value))} placeholder="••••••" />
      </label>
      {error && <div className="error-box">{error}</div>}
      <div className="form-actions">
        <button className="btn sm" disabled={busy || code.length !== 6}>{busy ? tr('جارٍ التحقق…') : tr('تفعيل')}</button>
        <button className="btn gh sm" type="button" onClick={onCancel}>{tr('إلغاء')}</button>
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
    return <div className="form-actions"><button className="btn gh sm" onClick={() => setOpen(true)}>{tr('إيقاف تطبيق المصادقة')}</button></div>
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
      <label className="field"><span>{tr('للتأكيد، اكتب كلمة المرور')}</span>
        <PasswordInput className="input" dir="ltr" required autoComplete="current-password" value={password}
          onChange={(e) => setPassword(e.target.value)} />
      </label>
      {error && <div className="error-box">{error}</div>}
      <div className="form-actions">
        <button className="btn sm" disabled={busy}>{tr('إيقاف')}</button>
        <button className="btn gh sm" type="button" onClick={() => setOpen(false)}>{tr('إلغاء')}</button>
      </div>
    </form>
  )
}
