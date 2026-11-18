import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import { endpoints } from '../api/endpoints'
import type { LoginChallenge } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { Logo } from '../ui/Logo'
import { sendSms, smsAvailable, smsError, type SmsSession } from '../lib/firebasePhone'
import { PasswordInput } from '../ui/PasswordInput'
import { LangSwitch } from '../ui/LangSwitch'
import { tr } from '../i18n'
import { biometricName, getPasskey, passkeySupported, wasCancelled } from '../lib/passkey'

/** Keeps a phone number or email in left-to-right order inside Arabic text (Unicode isolate). */
export const ltr = (s?: string | null) => '\u2066' + (s ?? '') + '\u2069'

export function errorText(e: unknown) {
  return e instanceof ApiError ? e.message : tr('حدث خطأ غير متوقع.')
}

export function AuthCard({ title, sub, children, onSubmit }: { title: string; sub?: string; children: ReactNode; onSubmit: (e: FormEvent) => void }) {
  return (
    <div className="auth-page">
      <form className="auth-card form" onSubmit={onSubmit}>
        <LangSwitch />
        <div className="logo"><Logo variant="mark" /></div>
        <h1>{title}</h1>
        {sub && <p className="sub">{sub}</p>}
        {children}
        <p className="mu" style={{ textAlign: 'center', margin: '6px 0 0' }}>VORNX Secure Access 🔒</p>
      </form>
    </div>
  )
}

export function LoginPage() {
  const { status, login, loginWith } = useAuth()
  const navigate = useNavigate()
  const [faceBusy, setFaceBusy] = useState(false)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [challenge, setChallenge] = useState<LoginChallenge | null>(null)

  if (status === 'in') return <Navigate to="/" replace />
  if (challenge) {
    return <CodeStep challenge={challenge} onRestart={() => { setChallenge(null); setPassword('') }} />
  }

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      const c = await login(email.trim(), password)
      if (c) setChallenge(c)
      else navigate('/', { replace: true })
    } catch (err) {
      setError(errorText(err))
    } finally {
      setBusy(false)
    }
  }

  /** No email or password: the phone offers its VORNX passkeys, Face ID, and we're in. */
  async function withPasskey() {
    setFaceBusy(true)
    setError(null)
    try {
      const o = await endpoints.passkeyLoginOptions()
      await loginWith(await endpoints.passkeyLogin(o.challengeId, await getPasskey(o.options)))
      navigate('/', { replace: true })
    } catch (err) {
      if (!wasCancelled(err)) setError(errorText(err))
    } finally {
      setFaceBusy(false)
    }
  }

  return (
    <AuthCard title={tr('تسجيل الدخول')} sub={tr('أدخل بريدك الإلكتروني وكلمة المرور')} onSubmit={submit}>
      <label className="field"><span>{tr('البريد الإلكتروني')}</span>
        <input className="input" type="email" dir="ltr" autoComplete="username" required value={email} onChange={(e) => setEmail(e.target.value)} />
      </label>
      <label className="field"><span>{tr('كلمة المرور')}</span>
        <PasswordInput className="input" dir="ltr" autoComplete="current-password" required value={password} onChange={(e) => setPassword(e.target.value)} />
      </label>
      {error && <div className="error-box">{error}</div>}
      <button className="btn wide" disabled={busy}>{busy ? tr('جارٍ الدخول…') : tr('متابعة')}</button>
      {passkeySupported() && (
        <>
          <div className="or"><span>{tr('أو')}</span></div>
          <button type="button" className="btn gh wide face" disabled={faceBusy} onClick={withPasskey}>
            <FaceIcon />
            {faceBusy ? tr('جارٍ التحقق…') : biometricName() === 'faceid' ? tr('الدخول بـ Face ID') : biometricName() === 'hello' ? tr('الدخول بـ Windows Hello') : tr('الدخول بالبصمة')}
          </button>
        </>
      )}
      <div className="auth-links"><Link to="/register">{tr('إنشاء حساب')}</Link>·<Link to="/forgot-password">{tr('نسيت كلمة المرور؟')}</Link></div>
    </AuthCard>
  )
}

/** The Face ID corners + face: one line icon used on the login button and in "حسابي". */
export function FaceIcon() {
  return (
    <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" aria-hidden="true">
      <path d="M4 8V6a2 2 0 0 1 2-2h2M16 4h2a2 2 0 0 1 2 2v2M20 16v2a2 2 0 0 1-2 2h-2M8 20H6a2 2 0 0 1-2-2v-2M9 9.5v1M15 9.5v1M12 9.5v3.5h-1M9.5 15.5a3.5 3.5 0 0 0 5 0" />
    </svg>
  )
}

/** Accept pasted "123 456" or Arabic-Indic digits ٠١٢٣٤٥٦٧٨٩; keep 6 Latin digits. */
export function digitsOnly(v: string) {
  return v.replace(/[٠-٩]/g, (d) => String(d.charCodeAt(0) - 0x660)).replace(/\D/g, '').slice(0, 6)
}

/**
 * Step 2 of login / sign-up: the code.
 *  EMAIL: our API emailed it (after sign-up this also confirms the email).
 *  TOTP:  the customer's authenticator app shows it; a recovery code works instead (lost phone).
 *  SMS:   Firebase texts it and checks it; the API then checks Firebase's proof. If the SMS can't be sent,
 *         or the customer prefers, the same login continues by email ("أرسل الرمز بالبريد").
 */
function CodeStep({ challenge, onRestart }: { challenge: LoginChallenge; onRestart: () => void }) {
  const { loginWith } = useAuth()
  const navigate = useNavigate()
  const [c, setC] = useState(challenge)
  const [code, setCode] = useState(challenge.devCode ?? '')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [wait, setWait] = useState(challenge.resendIn)
  const [info, setInfo] = useState<string | null>(null)
  const [smsState, setSmsState] = useState<'sending' | 'sent' | 'failed'>('sending')
  const [recovery, setRecovery] = useState(false)
  const sent = useRef(false)
  const sms = useRef<SmsSession | null>(null)
  const smsStarted = useRef(false)
  const smsResends = useRef(0)
  const captchaBox = useRef<HTMLDivElement>(null)
  const isSms = c.channel === 'SMS'
  const isTotp = c.channel === 'TOTP'
  const signup = c.purpose === 'SIGNUP'

  // "send again" countdown
  useEffect(() => {
    if (wait <= 0) return
    const t = setTimeout(() => setWait(wait - 1), 1000)
    return () => clearTimeout(t)
  }, [wait])

  // forget the Firebase session when leaving this screen
  useEffect(() => () => sms.current?.dispose(), [])

  async function startSms() {
    if (!c.phoneNumber || !captchaBox.current) return
    setSmsState('sending')
    setError(null)
    try {
      sms.current?.dispose()
      sms.current = await sendSms(c.phoneNumber, captchaBox.current)
      setSmsState('sent')
      setWait(60)
    } catch (err) {
      const { text, useEmail } = smsError(err)
      setSmsState('failed')
      if (useEmail) await byEmail(tr('{0} أرسلنا الرمز إلى بريدك بدلًا من ذلك.', { 0: text }))
      else setError(text)
    }
  }

  // SMS login: send the SMS once when the screen opens (the ref stops React's dev double-run sending 2 SMS)
  useEffect(() => {
    if (!isSms || smsStarted.current) return
    smsStarted.current = true
    if (smsAvailable) startSms()
    else byEmail(null) // this build has no Firebase settings: continue by email
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isSms])

  async function verify(value: string) {
    if (sent.current) return
    sent.current = true
    setBusy(true)
    setError(null)
    setInfo(null)
    try {
      if (isSms) {
        if (!sms.current) throw new Error('no sms session')
        let idToken: string
        try {
          idToken = await sms.current.confirm(value)
        } catch (err) {
          setError(smsError(err).text)
          setCode('')
          return
        }
        await loginWith(await endpoints.verifyPhone(c.challengeToken, idToken))
        sms.current.dispose()
      } else {
        await loginWith(await endpoints.verifyCode(c.challengeToken, value))
      }
      navigate('/', { replace: true })
    } catch (err) {
      if (err instanceof ApiError && err.code === 'LOGIN_CHALLENGE_INVALID') {
        onRestart() // burned or expired: back to the password
        return
      }
      setError(errorText(err))
      setCode('')
    } finally {
      sent.current = false
      setBusy(false)
    }
  }

  /** Email: a new code. SMS: switch this login to email. */
  async function byEmail(message: string | null) {
    setError(null)
    setInfo(null)
    try {
      const next = await endpoints.resendCode(c.challengeToken)
      sms.current?.dispose()
      sms.current = null
      setC(next)
      setWait(next.resendIn)
      setCode(next.devCode ?? '')
      setInfo(message ?? (isSms ? tr('أرسلنا الرمز إلى بريدك.') : tr('أرسلنا رمزًا جديدًا. الرمز السابق لم يعد صالحًا.')))
    } catch (err) {
      if (err instanceof ApiError && err.code === 'LOGIN_CHALLENGE_INVALID') return onRestart()
      if (err instanceof ApiError && err.problem?.resendIn) setWait(err.problem.resendIn)
      setError(errorText(err))
    }
  }

  function resendSms() {
    smsResends.current++
    setInfo(null)
    startSms()
  }

  function onChange(v: string) {
    if (recovery) {
      // recovery code "ABCDE-FGH23": letters and digits, sent when 10 are typed
      const clean = v.toUpperCase().replace(/[^A-Z0-9-]/g, '').slice(0, 11)
      setCode(clean)
      if (clean.replace(/-/g, '').length === 10) verify(clean)
      return
    }
    const digits = digitsOnly(v)
    setCode(digits)
    if (digits.length === 6) verify(digits)
  }

  const title = signup ? tr('تأكيد بريدك الإلكتروني') : isTotp ? tr('رمز تطبيق المصادقة') : tr('رمز التحقق')
  const sub = isTotp
    ? recovery
      ? tr('اكتب أحد رموز الاسترداد التي حفظتها عند تفعيل التطبيق. كل رمز يعمل مرة واحدة.')
      : tr('افتح تطبيق المصادقة على جوالك (Google أو Microsoft Authenticator) واكتب رمز VORNX المكوّن من 6 أرقام.')
    : isSms
      ? smsState === 'sending' ? tr('جارٍ إرسال رسالة نصية إلى {0}…', { 0: ltr(c.destination) }) : tr('أرسلنا رسالة نصية من 6 أرقام إلى {0}', { 0: ltr(c.destination) })
      : signup
        ? tr('أرسلنا رمزًا من 6 أرقام إلى {0}. اكتبه هنا لتأكيد بريدك.', { 0: ltr(c.destination) })
        : tr('أرسلنا رمزًا من 6 أرقام إلى {0}', { 0: ltr(c.destination) })
  const canType = !isSms || smsState === 'sent'
  const ready = recovery ? code.replace(/-/g, '').length === 10 : code.length === 6

  return (
    <AuthCard title={title} sub={sub}
      onSubmit={(e) => { e.preventDefault(); if (ready) verify(code) }}>
      <label className="field"><span>{recovery ? tr('رمز الاسترداد') : tr('الرمز')}</span>
        <input className="input otp" dir="ltr" inputMode={recovery ? 'text' : 'numeric'} autoComplete="one-time-code" autoFocus
          maxLength={12} required disabled={!canType} value={code} onChange={(e) => onChange(e.target.value)}
          placeholder={recovery ? 'XXXXX-XXXXX' : '••••••'} />
      </label>
      {error && <div className="error-box">{error}</div>}
      {info && <p className="mu" style={{ textAlign: 'center', margin: 0 }}>{info}</p>}
      <button className="btn wide" disabled={busy || !canType || !ready}>{busy ? tr('جارٍ التحقق…') : tr('تأكيد')}</button>
      <div className="auth-links">
        {isTotp ? (
          <button type="button" className="link" onClick={() => { setRecovery(!recovery); setCode(''); setError(null) }}>
            {recovery ? tr('استخدم رمز التطبيق') : tr('فقدت جوالك؟ استخدم رمز استرداد')}
          </button>
        ) : isSms ? (
          smsResends.current < 2 && smsState !== 'sending'
            ? <button type="button" className="link" disabled={wait > 0} onClick={resendSms}>
                {wait > 0 ? tr('إعادة الإرسال بعد {0} ث', { 0: wait }) : tr('إعادة إرسال الرسالة')}
              </button>
            : null
        ) : c.resendsLeft > 0
          ? <button type="button" className="link" disabled={wait > 0} onClick={() => byEmail(null)}>
              {wait > 0 ? tr('إعادة الإرسال بعد {0} ث', { 0: wait }) : tr('إعادة إرسال الرمز')}
            </button>
          : <span className="mu">{tr('لا مزيد من الرموز لهذه المحاولة')}</span>}
        {isSms && <>·<button type="button" className="link" onClick={() => byEmail(null)}>{tr('أرسل الرمز بالبريد')}</button></>}
        ·<button type="button" className="link" onClick={onRestart}>{signup ? tr('تسجيل الدخول') : tr('تغيير الحساب')}</button>
      </div>
      <p className="mu" style={{ textAlign: 'center', margin: 0, fontSize: 12.5 }}>
        {isTotp
          ? tr('فقدت جوالك ورموز الاسترداد معًا؟ تواصل مع دعم VORNX.')
          : isSms ? tr('لم تصلك الرسالة؟ انتظر دقيقة أو اطلب الرمز بالبريد.') : tr('لم يصلك؟ تحقق من مجلد الرسائل غير المرغوبة.')}
      </p>
      {/* Firebase's invisible reCAPTCHA lives here */}
      <div ref={captchaBox} />
    </AuthCard>
  )
}

export function RegisterPage() {
  const { status } = useAuth()
  const navigate = useNavigate()
  const [f, setF] = useState({ firstName: '', lastName: '', email: '', phone: '', password: '', confirmPassword: '' })
  const [agree, setAgree] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [challenge, setChallenge] = useState<LoginChallenge | null>(null)
  const set = (k: keyof typeof f) => (e: { target: { value: string } }) => setF({ ...f, [k]: e.target.value })

  if (status === 'in') return <Navigate to="/" replace />
  // step 2: the code we emailed (confirms the email and logs in)
  if (challenge) return <CodeStep challenge={challenge} onRestart={() => navigate('/login', { replace: true })} />

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (!agree) return setError(tr('يلزم الموافقة على الشروط وسياسة الخصوصية.'))
    setBusy(true)
    setError(null)
    try {
      setChallenge(await endpoints.register({
        ...f, firstName: f.firstName.trim(), lastName: f.lastName.trim(), email: f.email.trim(),
        phone: f.phone.replace(/\s/g, ''), language: 'AR', acceptTerms: true,
      }))
    } catch (err) {
      setError(errorText(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthCard title={tr('إنشاء حساب')} sub={tr('بطاقة واحدة لغرفتك، ناديك، موقفك، ودوامك')} onSubmit={submit}>
      <div className="row2">
        <label className="field"><span>{tr('الاسم الأول')}</span><input className="input" required maxLength={50} autoComplete="given-name" value={f.firstName} onChange={set('firstName')} /></label>
        <label className="field"><span>{tr('اسم العائلة')}</span><input className="input" required maxLength={50} autoComplete="family-name" value={f.lastName} onChange={set('lastName')} /></label>
      </div>
      <label className="field"><span>{tr('البريد الإلكتروني')}</span><input className="input" type="email" dir="ltr" required autoComplete="email" value={f.email} onChange={set('email')} /></label>
      <label className="field"><span>{tr('رقم الجوال')}</span>
        <input className="input" type="tel" dir="ltr" required pattern="05[0-9]{8}" placeholder="05XXXXXXXX" autoComplete="tel" value={f.phone} onChange={set('phone')} />
      </label>
      <label className="field"><span>{tr('كلمة المرور (10 أحرف على الأقل)')}</span><PasswordInput className="input" dir="ltr" required minLength={10} autoComplete="new-password" value={f.password} onChange={set('password')} /></label>
      <label className="field"><span>{tr('تأكيد كلمة المرور')}</span><PasswordInput className="input" dir="ltr" required minLength={10} autoComplete="new-password" value={f.confirmPassword} onChange={set('confirmPassword')} /></label>
      <label className="agree">
        <input type="checkbox" required checked={agree} onChange={(e) => setAgree(e.target.checked)} />
        <span>{tr('قرأت وأوافق على')}{' '}<Link to="/terms" target="_blank">{tr('شروط الاستخدام')}</Link>{' '}{tr('و')}<Link to="/privacy" target="_blank">{tr('سياسة الخصوصية')}</Link>
        </span>
      </label>
      {error && <div className="error-box">{error}</div>}
      <button className="btn wide" disabled={busy}>{busy ? tr('جارٍ الإنشاء…') : tr('إنشاء الحساب')}</button>
      <p className="mu" style={{ textAlign: 'center', margin: 0, fontSize: 12.5 }}>{tr('سنرسل رمزًا إلى بريدك لتأكيده.')}</p>
      <div className="auth-links">{tr('لديك حساب؟')}{' '}<Link to="/login">{tr('سجّل الدخول')}</Link></div>
    </AuthCard>
  )
}

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [sent, setSent] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    try {
      await endpoints.forgotPassword(email.trim())
      setSent(true) // the API answers the same whether the email exists or not
    } catch (err) {
      setError(errorText(err))
    }
  }

  return (
    <AuthCard title={tr('نسيت كلمة المرور')} onSubmit={submit}>
      {sent ? (
        <p>{tr('إذا كان البريد مسجّلًا لدينا، ستصلك رسالة فيها رابط لإعادة التعيين خلال دقائق. الرابط صالح لمدة 30 دقيقة.')}</p>
      ) : (
        <>
          <label className="field"><span>{tr('البريد الإلكتروني')}</span><input className="input" type="email" dir="ltr" required value={email} onChange={(e) => setEmail(e.target.value)} /></label>
          {error && <div className="error-box">{error}</div>}
          <button className="btn wide">{tr('إرسال رابط الاستعادة')}</button>
        </>
      )}
      <div className="auth-links"><Link to="/login">{tr('رجوع لتسجيل الدخول')}</Link></div>
    </AuthCard>
  )
}

/** Opened from the email link: /reset-password?token=pr_... */
export function ResetPasswordPage() {
  const [params] = useSearchParams()
  const token = params.get('token') ?? ''
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [done, setDone] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    try {
      await endpoints.resetPassword(token, password, confirm)
      setDone(true)
    } catch (err) {
      setError(errorText(err))
    }
  }

  return (
    <AuthCard title={tr('كلمة مرور جديدة')} onSubmit={submit}>
      {done ? (
        <p>{tr('تم تغيير كلمة المرور، وتم تسجيل خروجك من جميع الأجهزة.')}{' '}<Link to="/login">{tr('سجّل الدخول')}</Link></p>
      ) : !token ? (
        <div className="error-box">{tr('الرابط ناقص. افتح الرابط من رسالة البريد كما هو.')}</div>
      ) : (
        <>
          <label className="field"><span>{tr('كلمة المرور الجديدة (10 أحرف على الأقل)')}</span>
            <PasswordInput className="input" dir="ltr" minLength={10} required autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} />
          </label>
          <label className="field"><span>{tr('تأكيد كلمة المرور')}</span>
            <PasswordInput className="input" dir="ltr" minLength={10} required autoComplete="new-password" value={confirm} onChange={(e) => setConfirm(e.target.value)} />
          </label>
          {error && <div className="error-box">{error}</div>}
          <button className="btn wide">{tr('حفظ كلمة المرور')}</button>
        </>
      )}
    </AuthCard>
  )
}
