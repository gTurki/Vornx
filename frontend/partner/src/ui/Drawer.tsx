import { useEffect, type ReactNode } from 'react'
import { Icon } from './Icon'

/** The side panel from the design (#dr / #dp). Closes on Esc or a click on the dark background. */
export function Drawer({ open, onClose, title, children }: {
  open: boolean
  onClose: () => void
  title: ReactNode
  children: ReactNode
}) {
  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose()
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [open, onClose])

  if (!open) return null
  return (
    <div className="drawer" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="drawer-panel" role="dialog" aria-modal="true">
        <div className="drawer-head">
          <h2>{title}</h2>
          <button className="ib" onClick={onClose} aria-label="إغلاق">
            <Icon name="close" />
          </button>
        </div>
        {children}
      </div>
    </div>
  )
}
