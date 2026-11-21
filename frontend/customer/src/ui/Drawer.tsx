import { useEffect, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { Icon } from './Icon'
import { tr } from '../i18n'

/**
 * The side panel from the design (#dr / #dp); a bottom sheet on phones. Closes on Esc or a click on the dark background.
 * Drawn at the top of the page (a portal into <body>), so it always opens in front of you, never further down the
 * page, whatever it was opened from. The page behind doesn't scroll while it's open.
 */
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
    const before = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.removeEventListener('keydown', onKey)
      document.body.style.overflow = before
    }
  }, [open, onClose])

  if (!open) return null
  return createPortal(
    <div className="drawer" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="drawer-panel" role="dialog" aria-modal="true">
        <div className="drawer-head">
          <h2>{title}</h2>
          <button className="ib" onClick={onClose} aria-label={tr('إغلاق')}>
            <Icon name="close" />
          </button>
        </div>
        {children}
      </div>
    </div>,
    document.body,
  )
}
