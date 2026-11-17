import { useState, type InputHTMLAttributes } from 'react'
import { tr } from '../i18n'

/**
 * A password field with the design's 👁 button: shows / hides what was typed (handy on phones).
 * Same props as <input>; the type is managed here.
 */
export function PasswordInput(props: Omit<InputHTMLAttributes<HTMLInputElement>, 'type'>) {
  const [shown, setShown] = useState(false)
  return (
    <span className="pw">
      <input {...props} type={shown ? 'text' : 'password'} />
      <button type="button" className="eye" onClick={() => setShown(!shown)}
        aria-label={shown ? tr('إخفاء كلمة المرور') : tr('إظهار كلمة المرور')} aria-pressed={shown} title={shown ? tr('إخفاء') : tr('إظهار')}>
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z" />
          <circle cx="12" cy="12" r="3" />
          {shown && <path d="M3 3l18 18" />}
        </svg>
      </button>
    </span>
  )
}
