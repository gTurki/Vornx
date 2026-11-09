import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom'
import { ApiError } from '../api/client'
import { endpoints } from '../api/endpoints'
import { useAuth } from '../auth/AuthContext'

function errorText(e: unknown) {
  return e instanceof ApiError ? e.message : 'حدث خطأ غير متوقع.'
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
    <div className="auth-page">
      <form className="auth-card form" onSubmit={submit}>
        <div className="logo">VORNX</div>
        <h1>تسجيل الدخول — لوحة الإدارة</h1>
        <label className="field">
          <span>البريد الإلكتروني</span>
          <input className="input" type="email" dir="ltr" autoComplete="username" required
                 value={email} onChange={(e) => setEmail(e.target.value)} />
        </label>
        <label className="field">
          <span>كلمة المرور</span>
          <input className="input" type="password" dir="ltr" autoComplete="current-password" required
                 value={password} onChange={(e) => setPassword(e.target.value)} />
        </label>
        {error && <div className="error-box">{error}</div>}
        <button className="btn" disabled={busy}>{busy ? 'جارٍ الدخول…' : 'دخول'}</button>
        <Link to="/forgot-password" className="mu">نسيت كلمة المرور؟</Link>
      </form>
    </div>
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
    <div className="auth-page">
      <form className="auth-card form" onSubmit={submit}>
        <div className="logo">VORNX</div>
        <h1>نسيت كلمة المرور</h1>
        {sent ? (
          <p>إذا كان البريد مسجّلًا لدينا، ستصلك رسالة فيها رابط لإعادة التعيين خلال دقائق. الرابط صالح لمدة 30 دقيقة.</p>
        ) : (
          <>
            <label className="field">
              <span>البريد الإلكتروني</span>
              <input className="input" type="email" dir="ltr" required value={email} onChange={(e) => setEmail(e.target.value)} />
            </label>
            {error && <div className="error-box">{error}</div>}
            <button className="btn">إرسال رابط الاستعادة</button>
          </>
        )}
        <Link to="/login" className="mu">رجوع لتسجيل الدخول</Link>
      </form>
    </div>
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
    <div className="auth-page">
      <form className="auth-card form" onSubmit={submit}>
        <div className="logo">VORNX</div>
        <h1>تعيين كلمة مرور جديدة</h1>
        {done ? (
          <p>تم تغيير كلمة المرور، وتم تسجيل خروجك من جميع الأجهزة. <Link to="/login">سجّل الدخول</Link></p>
        ) : !token ? (
          <div className="error-box">الرابط ناقص. افتح الرابط من رسالة البريد كما هو.</div>
        ) : (
          <>
            <label className="field">
              <span>كلمة المرور الجديدة (10 أحرف على الأقل)</span>
              <input className="input" type="password" dir="ltr" minLength={10} required autoComplete="new-password"
                     value={password} onChange={(e) => setPassword(e.target.value)} />
            </label>
            <label className="field">
              <span>تأكيد كلمة المرور</span>
              <input className="input" type="password" dir="ltr" minLength={10} required autoComplete="new-password"
                     value={confirm} onChange={(e) => setConfirm(e.target.value)} />
            </label>
            {error && <div className="error-box">{error}</div>}
            <button className="btn">حفظ كلمة المرور</button>
          </>
        )}
      </form>
    </div>
  )
}
