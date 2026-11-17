import { useEffect, useState } from 'react'
import { tr } from '../i18n'
import { install } from '../lib/install'

const DISMISSED = 'vornx.customer.install-dismissed'

/**
 * "ثبّت تطبيق VORNX": an install button where the browser allows it (Android, Chrome, Edge), or the
 * "Share > Add to Home Screen" steps on iPhone. Hidden once installed, or after "لاحقًا".
 */
export function InstallBanner() {
  const [, rerender] = useState(0)
  const [dismissed, setDismissed] = useState(() => {
    try { return localStorage.getItem(DISMISSED) === '1' } catch { return false }
  })
  useEffect(() => install.subscribe(() => rerender((n) => n + 1)), [])

  if (dismissed || install.installed()) return null
  const ios = install.isIosSafari()
  if (!ios && !install.canPrompt()) return null

  const close = () => {
    setDismissed(true)
    try { localStorage.setItem(DISMISSED, '1') } catch { /* ignore */ }
  }

  return (
    <div className="install" role="region" aria-label={tr('تثبيت التطبيق')}>
      <img src="/icons/icon-192.png" alt="" width={44} height={44} />
      <div className="grow">
        <b>{tr('ثبّت تطبيق VORNX')}</b>
        <small>
          {ios
            ? tr('من Safari: اضغط زر المشاركة ⎋ ثم «إضافة إلى الشاشة الرئيسية».')
            : tr('افتح بطاقتك وتصاريحك بلمسة من الشاشة الرئيسية، حتى بدون متصفح.')}
        </small>
      </div>
      {!ios && <button className="btn sm" onClick={() => install.prompt()}>{tr('تثبيت')}</button>}
      <button className="btn gh sm" onClick={close}>{tr('لاحقًا')}</button>
    </div>
  )
}
