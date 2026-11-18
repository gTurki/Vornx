import { useSyncExternalStore } from 'react'
import { tr } from '../i18n'

/**
 * The look of the VORNX card, chosen by the customer on "بطاقتي" (from the design: 4 colors + 4 pictures).
 * Saved on this device only (a personal preference, like the theme).
 *  ink 'dark' = the picture is light, so the name and number are written in dark ink.
 */
export type CardSkin = { id: string; name: string; ink: 'light' | 'dark'; back: [string, string] }

export const CARD_SKINS: CardSkin[] = [
  { id: 'riyadh', name: tr('الأصلية'), ink: 'light', back: ['#0A0070', '#1000B4'] },
  { id: 'oxford', name: tr('أوكسفورد'), ink: 'light', back: ['#0a0070', '#1000b4'] },
  { id: 'blue', name: tr('أزرق'), ink: 'light', back: ['#0a2585', '#113fd1'] },
  { id: 'black', name: tr('أسود'), ink: 'light', back: ['#000', '#1c1c1c'] },
  { id: 'grey', name: tr('رمادي'), ink: 'dark', back: ['#b9bcc0', '#d1d3d4'] },
  { id: 'skyline', name: tr('أفق الرياض'), ink: 'dark', back: ['#0d1b3a', '#1d3270'] },
  { id: 'heritage', name: tr('تراث وأصالة'), ink: 'dark', back: ['#1c130a', '#3b2a17'] },
  { id: 'impossible', name: tr('المستحيل ليس سعودياً'), ink: 'dark', back: ['#06231a', '#0e3b2c'] },
  { id: 'ya-alsaudi', name: tr('يا السعودي'), ink: 'light', back: ['#04170f', '#0b3b2a'] },
]

const KEY = 'vornx.customer.cardSkin'
const EVENT = 'vornx-card-skin'

function read(): string {
  try {
    const v = localStorage.getItem(KEY)
    return v && CARD_SKINS.some((s) => s.id === v) ? v : 'riyadh'
  } catch {
    return 'riyadh'
  }
}

export function setCardSkin(id: string) {
  try { localStorage.setItem(KEY, id) } catch { /* ignore: the choice just won't be remembered */ }
  window.dispatchEvent(new Event(EVENT))
}

function subscribe(cb: () => void) {
  window.addEventListener(EVENT, cb)
  window.addEventListener('storage', cb) // another tab changed it
  return () => {
    window.removeEventListener(EVENT, cb)
    window.removeEventListener('storage', cb)
  }
}

/** The chosen skin; every card on screen updates when it changes. */
export function useCardSkin(): CardSkin {
  const id = useSyncExternalStore(subscribe, read, () => 'riyadh')
  return CARD_SKINS.find((s) => s.id === id) ?? CARD_SKINS[0]
}
