import { useEffect, useRef, useState } from 'react'
import { ApiError } from '../api/client'
import { endpoints } from '../api/endpoints'
import type { LoginChallenge } from '../api/types'
import { useAuth } from '../auth/AuthContext'
import { AuthCard, digitsOnly, errorText, ltr } from './AuthPages'

/**
 * Shown instead of the app until the customer confirms their mobile: we send a 6-digit code by WhatsApp.
 * (The API enforces it too: other customer endpoints answer 403 PHONE_NOT_VERIFIED until then.)
 */
export function VerifyPhonePage() {
  const { me, updateMe, logout } = useAuth()
  const [c, setC] = useState<LoginChallenge | null>(null)
  const [code, setCode] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [info, setInfo] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [wait, setWait] = useState(0)
  const checking = useRef(false)

  useEffect(() => {
    if (wait <= 0) return
    const t = setTimeout(() => setWait(wait - 1), 1000)
    return () => clearTimeout(t)
  }, [wait])

  async function send() {
    setBusy(true)
    setError(null)
    setInfo(null)
    try {
      const next = c ? await endpoints.phoneResend(c.challengeToken) : await endpoints.phoneStart()
      setC(next)
      setWait(next.resendIn)
      setCode(next.devCode ?? '')
      if (c) setInfo('أرسلنا رمزًا جديدًا. الرمز السابق لم يعد صالحًا.')
    } catch (err) {
      if (err instanceof ApiError && err.code === 'PHONE_ALREADY_VERIFIED') return updateMe(await endpoints.me())
      if (err instanceof ApiError && err.code === 'LOGIN_CHALLENGE_INVALID') setC(null)
      if (err instanceof ApiError && err.problem?.resendIn) setWait(err.problem.resendIn)
      setError(errorText(err))
    } finally {
      setBusy(false)
    }
  }

  async function confirm(value: string) {
    if (!c || checking.current) return
    checking.current = true
    setBusy(true)
    setError(null)
    setInfo(null)
    try {
      updateMe(await endpoints.phoneConfirm(c.challengeToken, value)) // the gate opens
    } catch (err) {
      if (err instanceof ApiError && err.code === 'LOGIN_CHALLENGE_INVALID') {
        setC(null) // too many wrong codes or expired: ask for a new one
      }
      setError(errorText(err))
      setCode('')
    } finally {
      checking.current = false
      setBusy(false)
    }
  }

  function onChange(v: string) {
    const d = digitsOnly(v)
    setCode(d)
    if (d.length === 6) confirm(d)
  }

  return (
    <AuthCard title="تأكيد رقم الجوال"
      sub={c ? `أرسلنا رمزًا من 6 أرقام عبر واتساب إلى ${ltr(c.destination)}` : `خطوة أخيرة: نرسل رمزًا عبر واتساب إلى رقمك ${ltr(me?.phone)} للتأكد أنه لك.`}
      onSubmit={(e) => { e.preventDefault(); if (!c) send(); else if (code.length === 6) confirm(code) }}>
      {c && (
        <label className="field"><span>الرمز من واتساب</span>
          <input className="input otp" dir="ltr" inputMode="numeric" autoComplete="one-time-code" autoFocus
            maxLength={12} required value={code} onChange={(e) => onChange(e.target.value)} placeholder="••••••" />
        </label>
      )}
      {error && <div className="error-box">{error}</div>}
      {info && <p className="mu" style={{ textAlign: 'center', margin: 0 }}>{info}</p>}
      {c
        ? <button className="btn wide" disabled={busy || code.length !== 6}>{busy ? 'جارٍ التحقق…' : 'تأكيد'}</button>
        : <button className="btn wide" disabled={busy}>{busy ? 'جارٍ الإرسال…' : 'أرسل الرمز عبر واتساب'}</button>}
      <div className="auth-links">
        {c && (c.resendsLeft > 0
          ? <><button type="button" className="link" disabled={wait > 0 || busy} onClick={send}>
              {wait > 0 ? `إعادة الإرسال بعد ${wait} ث` : 'إعادة إرسال الرمز'}
            </button>·</>
          : <><button type="button" className="link" onClick={() => { setC(null); setCode('') }}>اطلب رمزًا جديدًا</button>·</>)}
        <button type="button" className="link" onClick={logout}>تسجيل الخروج</button>
      </div>
      <p className="mu" style={{ textAlign: 'center', margin: 0, fontSize: 12.5 }}>
        تأكد أن واتساب يعمل على هذا الرقم. الرقم غير صحيح؟ تواصل مع دعم VORNX.
      </p>
    </AuthCard>
  )
}
