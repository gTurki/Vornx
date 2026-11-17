// Arabic / English for the customer app.
//
// The Arabic text itself is the key: tr('تسجيل الدخول') -> "Log in" in English, the same text in Arabic.
// Variables use {names}: tr('قبل {0} د', { 0: m }).
// Switching language saves the choice and RELOADS the page, so every text (even ones computed when a file
// loads) comes out in the new language. This module has no imports, so it is ready before everything else.
import { EN } from './en'

export type Lang = 'ar' | 'en'
const KEY = 'vornx.customer.lang'

function saved(): Lang {
  try {
    const v = localStorage.getItem(KEY)
    if (v === 'ar' || v === 'en') return v
  } catch { /* private mode, blocked storage */ }
  return 'ar'
}

export const lang: Lang = saved()
export const isEn = lang === 'en'

// <html lang dir>: the whole layout flips (the CSS uses logical properties: inline-start/end)
if (typeof document !== 'undefined') {
  document.documentElement.lang = lang
  document.documentElement.dir = isEn ? 'ltr' : 'rtl'
  if (isEn) document.title = 'VORNX — My card'
}

const missing = new Set<string>()

export function tr(ar: string, vars?: Record<string | number, string | number>): string {
  let text = ar
  if (isEn) {
    const en = EN[ar]
    if (en !== undefined) text = en
    else if (import.meta.env.DEV && !missing.has(ar)) {
      missing.add(ar)
      console.warn('[i18n] no English for:', ar)
    }
  }
  if (vars) text = text.replace(/\{(\w+)\}/g, (m, k: string) => (k in vars ? String(vars[k]) : m))
  return text
}

/** Saves the language and reloads (keeps the current page). */
export function setLang(next: Lang) {
  if (next === lang) return
  try { localStorage.setItem(KEY, next) } catch { /* ignore */ }
  location.reload()
}
