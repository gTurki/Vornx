// "Install the app" (PWA). Chrome/Edge/Android fire `beforeinstallprompt` ONCE, early, maybe before React shows
// the banner: we keep the event here so the banner can use it later. iPhone Safari has no such event: the customer
// adds the app from the Share menu, so we show those steps instead.

type InstallEvent = Event & { prompt: () => Promise<void>; userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }> }

let deferred: InstallEvent | null = null
const listeners = new Set<() => void>()
const notify = () => listeners.forEach((l) => l())

if (typeof window !== 'undefined') {
  window.addEventListener('beforeinstallprompt', (e) => {
    e.preventDefault() // we show our own button
    deferred = e as InstallEvent
    notify()
  })
  window.addEventListener('appinstalled', () => { deferred = null; notify() })
}

export const install = {
  /** Already opened as an installed app? */
  installed: () => matchMedia('(display-mode: standalone)').matches || (navigator as { standalone?: boolean }).standalone === true,
  /** iPhone / iPad Safari: no install button, only "Share > Add to Home Screen". */
  isIosSafari: () => /iPhone|iPad|iPod/.test(navigator.userAgent) && /Safari/.test(navigator.userAgent) && !/CriOS|FxiOS|EdgiOS/.test(navigator.userAgent),
  canPrompt: () => deferred !== null,
  /** Shows the browser's install dialog. True if the customer accepted. */
  prompt: async () => {
    if (!deferred) return false
    const e = deferred
    deferred = null
    await e.prompt()
    const { outcome } = await e.userChoice
    notify()
    return outcome === 'accepted'
  },
  subscribe: (l: () => void) => { listeners.add(l); return () => { listeners.delete(l) } },
}

/** Registers the service worker (production builds only: in development it would cache stale code). */
export function registerServiceWorker() {
  if (!import.meta.env.PROD || !('serviceWorker' in navigator)) return
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').catch(() => undefined) // the app works without it
  })
}
