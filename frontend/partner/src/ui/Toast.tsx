import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from 'react'

type Toast = { text: string; error?: boolean }
const ToastContext = createContext<(text: string, error?: boolean) => void>(() => {})

/** The small message at the bottom of the screen ("حُفظ الإعداد"), like in the design. */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [toast, setToast] = useState<Toast | null>(null)
  const timer = useRef<number | undefined>(undefined)

  const show = useCallback((text: string, error = false) => {
    setToast({ text, error })
    window.clearTimeout(timer.current)
    timer.current = window.setTimeout(() => setToast(null), error ? 5000 : 2800)
  }, [])

  return (
    <ToastContext.Provider value={show}>
      {children}
      {toast && (
        <div className={`toast${toast.error ? ' error' : ''}`} role="status" aria-live="polite">
          {toast.text}
        </div>
      )}
    </ToastContext.Provider>
  )
}

export const useToast = () => useContext(ToastContext)
