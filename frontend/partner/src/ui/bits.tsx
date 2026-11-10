import { useEffect, useState, type ReactNode } from 'react'
import { ApiError } from '../api/client'
import type { Label } from '../lib/labels'

/** Colored status pill: <Tag label={PARTNER_STATUS[p.status]} /> */
export function Tag({ label }: { label: Label }) {
  return <span className={`tag ${label.tone}`}>{label.text}</span>
}

export function Num({ children }: { children: ReactNode }) {
  return <span className="num">{children}</span>
}

export function Kv({ k, children }: { k: string; children: ReactNode }) {
  return (
    <div className="kv">
      <span>{k}</span>
      <b>{children}</b>
    </div>
  )
}

export function Loading() {
  return <p className="mu pad">جارٍ التحميل…</p>
}

export function Empty({ children }: { children: ReactNode }) {
  return <p className="mu pad">{children}</p>
}

export function ErrorBox({ error }: { error: unknown }) {
  const text = error instanceof ApiError ? error.message : 'حدث خطأ غير متوقع.'
  return <div className="error-box">{text}</div>
}

/** Previous / next for paged lists. */
export function Pager({ page, totalPages, onChange }: { page: number; totalPages: number; onChange: (p: number) => void }) {
  if (totalPages <= 1) return null
  return (
    <div className="pager">
      <button className="btn gh" disabled={page <= 0} onClick={() => onChange(page - 1)}>السابق</button>
      <span className="mu">
        صفحة <Num>{page + 1}</Num> من <Num>{totalPages}</Num>
      </span>
      <button className="btn gh" disabled={page >= totalPages - 1} onClick={() => onChange(page + 1)}>التالي</button>
    </div>
  )
}

/**
 * For dangerous actions: first click turns into "اضغط مرة أخرى للتأكيد", second click runs it.
 * (Like "Tap again to confirm" in the customer app. No browser confirm() popups.)
 */
export function ConfirmButton({ children, onConfirm, className = 'btn gh', disabled }: {
  children: ReactNode
  onConfirm: () => void
  className?: string
  disabled?: boolean
}) {
  const [armed, setArmed] = useState(false)
  useEffect(() => {
    if (!armed) return
    const t = window.setTimeout(() => setArmed(false), 4000)
    return () => window.clearTimeout(t)
  }, [armed])
  return (
    <button
      className={`${className}${armed ? ' danger' : ''}`}
      disabled={disabled}
      onClick={() => (armed ? (setArmed(false), onConfirm()) : setArmed(true))}
    >
      {armed ? 'اضغط مرة أخرى للتأكيد' : children}
    </button>
  )
}

/** Delays a value (e.g. the search box) so we don't call the API on every key press. */
export function useDebounced<T>(value: T, ms = 350): T {
  const [v, setV] = useState(value)
  useEffect(() => {
    const t = window.setTimeout(() => setV(value), ms)
    return () => window.clearTimeout(t)
  }, [value, ms])
  return v
}
