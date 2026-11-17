import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import { endpoints } from '../api/endpoints'
import type { LoginChallenge } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { Logo } from '../ui/Logo'
import { sendSms, smsAvailable, smsError, type SmsSession } from '../lib/firebasePhone'
import { PasswordInput } from '../ui/PasswordInput'

/** Keeps a phone number or email in left-to-right order inside Arabic text (Unicode isolate). */
export const ltr = (s?: string | null) => '\u2066' + (s ?? '') + '\u2069'

export function errorText(e: unknown) {
  return e instanceof ApiError ? e.message : 'حدث خطأ غير متوقع.'
}

export function AuthCard({ title, sub, children, onSubmit }: { title: string; sub?: string; children: ReactNode; onSubmit: (e: FormEvent) => void }) {
  return (
    <div className="auth-page">
      <form className="auth-card form" onSubmit={onSubmit}>
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
  const { status, login } = useAuth()
  const navigate = useNavigate()
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

  return (
    <AuthCard title="تسجيل الدخول" sub="أدخل بريدك الإلكتروني وكلمة المرور" onSubmit={submit}>
      <label className="field"><span>البريد الإلكتروني</span>
        <input className="input" type="email" dir="ltr" autoComplete="username" required value={email} onChange={(e) => setEmail(e.target.value)} />
      </label>
      <label className="field"><span>كلمة المرور</span>
        <PasswordInput className="input" dir="ltr" autoComplete="current-password" required value={password} onChange={(e) => setPassword(e.target.value)} />
      </label>
      {error && <div className="error-box">{error}</div>}
      <button className="btn wide" disabled={busy}>{busy ? 'جارٍ الدخول…' : 'متابعة'}</button>
      <div className="auth-links"><Link to="/register">إنشاء حساب</Link>·<Link to="/forgot-password">نسيت كلمة المرور؟</Link></div>
    </AuthCard>
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
      if (useEmail) await byEmail(`${text} أرسلنا الرمز إلى بريدك بدلًا من ذلك.`)
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
      setInfo(message ?? (isSms ? 'أرسلنا الرمز إلى بريدك.' : 'أرسلنا رمزًا جديدًا. الرمز السابق لم يعد صالحًا.'))
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

  const title = signup ? 'تأكيد بريدك الإلكتروني' : isTotp ? 'رمز تطبيق المصادقة' : 'رمز التحقق'
  const sub = isTotp
    ? recovery
      ? 'اكتب أحد رموز الاسترداد التي حفظتها عند تفعيل التطبيق. كل رمز يعمل مرة واحدة.'
      : 'افتح تطبيق المصادقة على جوالك (Google أو Microsoft Authenticator) واكتب رمز VORNX المكوّن من 6 أرقام.'
    : isSms
      ? smsState === 'sending' ? `جارٍ إرسال رسالة نصية إلى ${ltr(c.destination)}…` : `أرسلنا رسالة نصية من 6 أرقام إلى ${ltr(c.destination)}`
      : signup
        ? `أرسلنا رمزًا من 6 أرقام إلى ${ltr(c.destination)}. اكتبه هنا لتأكيد بريدك.`
        : `أرسلنا رمزًا من 6 أرقام إلى ${ltr(c.destination)}`
  const canType = !isSms || smsState === 'sent'
  const ready = recovery ? code.replace(/-/g, '').length === 10 : code.length === 6

  return (
    <AuthCard title={title} sub={sub}
      onSubmit={(e) => { e.preventDefault(); if (ready) verify(code) }}>
      <label className="field"><span>{recovery ? 'رمز الاسترداد' : 'الرمز'}</span>
        <input className="input otp" dir="ltr" inputMode={recovery ? 'text' : 'numeric'} autoComplete="one-time-code" autoFocus
          maxLength={12} required disabled={!canType} value={code} onChange={(e) => onChange(e.target.value)}
          placeholder={recovery ? 'XXXXX-XXXXX' : '••••••'} />
      </label>
      {error && <div className="error-box">{error}</div>}
      {info && <p className="mu" style={{ textAlign: 'center', margin: 0 }}>{info}</p>}
      <button className="btn wide" disabled={busy || !canType || !ready}>{busy ? 'جارٍ التحقق…' : 'تأكيد'}</button>
      <div className="auth-links">
        {isTotp ? (
          <button type="button" className="link" onClick={() => { setRecovery(!recovery); setCode(''); setError(null) }}>
            {recovery ? 'استخدم رمز التطبيق' : 'فقدت جوالك؟ استخدم رمز استرداد'}
          </button>
        ) : isSms ? (
          smsResends.current < 2 && smsState !== 'sending'
            ? <button type="button" className="link" disabled={wait > 0} onClick={resendSms}>
                {wait > 0 ? `إعادة الإرسال بعد ${wait} ث` : 'إعادة إرسال الرسالة'}
              </button>
            : null
        ) : c.resendsLeft > 0
          ? <button type="button" className="link" disabled={wait > 0} onClick={() => byEmail(null)}>
              {wait > 0 ? `إعادة الإرسال بعد ${wait} ث` : 'إعادة إرسال الرمز'}
            </button>
          : <span className="mu">لا مزيد من الرموز لهذه المحاولة</span>}
        {isSms && <>·<button type="button" className="link" onClick={() => byEmail(null)}>أرسل الرمز بالبريد</button></>}
        ·<button type="button" className="link" onClick={onRestart}>{signup ? 'تسجيل الدخول' : 'تغيير الحساب'}</button>
      </div>
      <p className="mu" style={{ textAlign: 'center', margin: 0, fontSize: 12.5 }}>
        {isTotp
          ? 'فقدت جوالك ورموز الاسترداد معًا؟ تواصل مع دعم VORNX.'
          : isSms ? 'لم تصلك الرسالة؟ انتظر دقيقة أو اطلب الرمز بالبريد.' : 'لم يصلك؟ تحقق من مجلد الرسائل غير المرغوبة.'}
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
    if (!agree) return setError('يلزم الموافقة على الشروط وسياسة الخصوصية.')
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
    <AuthCard title="إنشاء حساب" sub="بطاقة واحدة لغرفتك، ناديك، موقفك، ودوامك" onSubmit={submit}>
      <div className="row2">
        <label className="field"><span>الاسم الأول</span><input className="input" required maxLength={50} autoComplete="given-name" value={f.firstName} onChange={set('firstName')} /></label>
        <label className="field"><span>اسم العائلة</span><input className="input" required maxLength={50} autoComplete="family-name" value={f.lastName} onChange={set('lastName')} /></label>
      </div>
      <label className="field"><span>البريد الإلكتروني</span><input className="input" type="email" dir="ltr" required autoComplete="email" value={f.email} onChange={set('email')} /></label>
      <label className="field"><span>رقم الجوال</span>
        <input className="input" type="tel" dir="ltr" required pattern="05[0-9]{8}" placeholder="05XXXXXXXX" autoComplete="tel" value={f.phone} onChange={set('phone')} />
      </label>
      <label className="field"><span>كلمة المرور (10 أحرف على الأقل)</span><PasswordInput className="input" dir="ltr" required minLength={10} autoComplete="new-password" value={f.password} onChange={set('password')} /></label>
      <label className="field"><span>تأكيد كلمة المرور</span><PasswordInput className="input" dir="ltr" required minLength={10} autoComplete="new-password" value={f.confirmPassword} onChange={set('confirmPassword')} /></label>
      <label className="agree">
        <input type="checkbox" required checked={agree} onChange={(e) => setAgree(e.target.checked)} />
        <span>
          قرأت وأوافق على <Link to="/terms" target="_blank">شروط الاستخدام</Link> و<Link to="/privacy" target="_blank">سياسة الخصوصية</Link>
        </span>
      </label>
      {error && <div className="error-box">{error}</div>}
      <button className="btn wide" disabled={busy}>{busy ? 'جارٍ الإنشاء…' : 'إنشاء الحساب'}</button>
      <p className="mu" style={{ textAlign: 'center', margin: 0, fontSize: 12.5 }}>سنرسل رمزًا إلى بريدك لتأكيده.</p>
      <div className="auth-links">لديك حساب؟ <Link to="/login">سجّل الدخول</Link></div>
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
    <AuthCard title="نسيت كلمة المرور" onSubmit={submit}>
      {sent ? (
        <p>إذا كان البريد مسجّلًا لدينا، ستصلك رسالة فيها رابط لإعادة التعيين خلال دقائق. الرابط صالح لمدة 30 دقيقة.</p>
      ) : (
        <>
          <label className="field"><span>البريد الإلكتروني</span><input className="input" type="email" dir="ltr" required value={email} onChange={(e) => setEmail(e.target.value)} /></label>
          {error && <div className="error-box">{error}</div>}
          <button className="btn wide">إرسال رابط الاستعادة</button>
        </>
      )}
      <div className="auth-links"><Link to="/login">رجوع لتسجيل الدخول</Link></div>
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
    <AuthCard title="كلمة مرور جديدة" onSubmit={submit}>
      {done ? (
        <p>تم تغيير كلمة المرور، وتم تسجيل خروجك من جميع الأجهزة. <Link to="/login">سجّل الدخول</Link></p>
      ) : !token ? (
        <div className="error-box">الرابط ناقص. افتح الرابط من رسالة البريد كما هو.</div>
      ) : (
        <>
          <label className="field"><span>كلمة المرور الجديدة (10 أحرف على الأقل)</span>
            <PasswordInput className="input" dir="ltr" minLength={10} required autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} />
          </label>
          <label className="field"><span>تأكيد كلمة المرور</span>
            <PasswordInput className="input" dir="ltr" minLength={10} required autoComplete="new-password" value={confirm} onChange={(e) => setConfirm(e.target.value)} />
          </label>
          {error && <div className="error-box">{error}</div>}
          <button className="btn wide">حفظ كلمة المرور</button>
        </>
      )}
    </AuthCard>
  )
}
