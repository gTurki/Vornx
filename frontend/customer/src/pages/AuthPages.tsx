import { useState, type FormEvent, type ReactNode } from 'react'
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import { endpoints } from '../api/endpoints'
import { useAuth } from '../auth/AuthContext'

function errorText(e: unknown) {
  return e instanceof ApiError ? e.message : 'حدث خطأ غير متوقع.'
}

function AuthCard({ title, sub, children, onSubmit }: { title: string; sub?: string; children: ReactNode; onSubmit: (e: FormEvent) => void }) {
  return (
    <div className="auth-page">
      <form className="auth-card form" onSubmit={onSubmit}>
        <div className="logo">VORNX</div>
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

  if (status === 'in') return <Navigate to="/" replace />

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await login(email.trim(), password)
      navigate('/', { replace: true })
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
        <input className="input" type="password" dir="ltr" autoComplete="current-password" required value={password} onChange={(e) => setPassword(e.target.value)} />
      </label>
      {error && <div className="error-box">{error}</div>}
      <button className="btn wide" disabled={busy}>{busy ? 'جارٍ الدخول…' : 'متابعة'}</button>
      <div className="auth-links"><Link to="/register">إنشاء حساب</Link>·<Link to="/forgot-password">نسيت كلمة المرور؟</Link></div>
    </AuthCard>
  )
}

export function RegisterPage() {
  const { status, loginWith } = useAuth()
  const navigate = useNavigate()
  const [f, setF] = useState({ firstName: '', lastName: '', email: '', phone: '', password: '', confirmPassword: '' })
  const [agree, setAgree] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const set = (k: keyof typeof f) => (e: { target: { value: string } }) => setF({ ...f, [k]: e.target.value })

  if (status === 'in') return <Navigate to="/" replace />

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await loginWith(await endpoints.register({
        ...f, firstName: f.firstName.trim(), lastName: f.lastName.trim(), email: f.email.trim(),
        phone: f.phone.replace(/\s/g, ''), language: 'AR',
      }))
      navigate('/', { replace: true })
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
      <label className="field"><span>كلمة المرور (10 أحرف على الأقل)</span><input className="input" type="password" dir="ltr" required minLength={10} autoComplete="new-password" value={f.password} onChange={set('password')} /></label>
      <label className="field"><span>تأكيد كلمة المرور</span><input className="input" type="password" dir="ltr" required minLength={10} autoComplete="new-password" value={f.confirmPassword} onChange={set('confirmPassword')} /></label>
      <label style={{ display: 'flex', gap: 8, alignItems: 'center', fontSize: 13.5 }}>
        <input type="checkbox" required checked={agree} onChange={(e) => setAgree(e.target.checked)} /> أوافق على الشروط وسياسة الخصوصية
      </label>
      {error && <div className="error-box">{error}</div>}
      <button className="btn wide" disabled={busy}>{busy ? 'جارٍ الإنشاء…' : 'إنشاء الحساب'}</button>
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
            <input className="input" type="password" dir="ltr" minLength={10} required autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} />
          </label>
          <label className="field"><span>تأكيد كلمة المرور</span>
            <input className="input" type="password" dir="ltr" minLength={10} required autoComplete="new-password" value={confirm} onChange={(e) => setConfirm(e.target.value)} />
          </label>
          {error && <div className="error-box">{error}</div>}
          <button className="btn wide">حفظ كلمة المرور</button>
        </>
      )}
    </AuthCard>
  )
}
