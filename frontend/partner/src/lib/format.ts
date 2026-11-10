// Display helpers. All dates are shown in Riyadh time, whatever the viewer's computer is set to.
//
// Locale "ar-SA-u-ca-gregory-nu-latn":
//   ar-SA     Arabic month names
//   ca-gregory the Gregorian calendar (ar-SA alone defaults to the Hijri calendar!)
//   nu-latn   0-9 digits, like the design (not ٠-٩)

const ZONE = 'Asia/Riyadh'
const LOCALE = 'ar-SA-u-ca-gregory-nu-latn'

const dateFmt = new Intl.DateTimeFormat(LOCALE, { timeZone: ZONE, day: 'numeric', month: 'short', year: 'numeric' })
const dateTimeFmt = new Intl.DateTimeFormat(LOCALE, {
  timeZone: ZONE, day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
})

/** "2026-12-31" (a LocalDate from Java) or an ISO instant -> "31 ديسمبر 2026" */
export function date(value: string | null | undefined): string {
  if (!value) return '—'
  // A plain date has no time zone: read it at noon UTC so it never shifts to the previous day.
  const d = /^\d{4}-\d{2}-\d{2}$/.test(value) ? new Date(value + 'T12:00:00Z') : new Date(value)
  return dateFmt.format(d)
}

export function dateTime(value: string | null | undefined): string {
  return value ? dateTimeFmt.format(new Date(value)) : '—'
}

/** 12000 -> "12,000.00" (digits left-to-right, inside Arabic text) */
export function money(value: number | null | undefined): string {
  if (value === null || value === undefined) return '—'
  return value.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

export function count(value: number | null | undefined): string {
  return value === null || value === undefined ? '—' : value.toLocaleString('en-US')
}

/** "3 دقائق" style relative time for "last message" columns. */
export function ago(value: string | null | undefined): string {
  if (!value) return '—'
  const s = Math.round((Date.now() - new Date(value).getTime()) / 1000)
  if (s < 60) return 'الآن'
  const m = Math.round(s / 60)
  if (m < 60) return `قبل ${m} د`
  const h = Math.round(m / 60)
  if (h < 24) return `قبل ${h} س`
  return date(value)
}

/** "Mozilla/5.0 (Windows NT 10.0...) Chrome/156..." -> "Chrome · Windows". Unknown strings are shown as they are. */
export function deviceName(userAgent: string | null): string {
  if (!userAgent) return 'جهاز غير معروف'
  const ua = userAgent
  const browser = /Edg\//.test(ua) ? 'Edge' : /OPR\//.test(ua) ? 'Opera' : /Chrome\//.test(ua) ? 'Chrome'
    : /Firefox\//.test(ua) ? 'Firefox' : /Safari\//.test(ua) ? 'Safari' : null
  const os = /iPhone/.test(ua) ? 'iPhone' : /iPad/.test(ua) ? 'iPad' : /Android/.test(ua) ? 'Android'
    : /Windows/.test(ua) ? 'Windows' : /Mac OS X|Macintosh/.test(ua) ? 'macOS' : /Linux/.test(ua) ? 'Linux' : null
  if (browser || os) return [browser, os].filter(Boolean).join(' · ')
  return ua.length > 40 ? ua.slice(0, 40) + '…' : ua
}

/** "2026-11" for <input type="month"> default. */
export function thisMonth(): string {
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone: ZONE, year: 'numeric', month: '2-digit' }).formatToParts(new Date())
  return `${parts.find((p) => p.type === 'year')!.value}-${parts.find((p) => p.type === 'month')!.value}`
}

// ---------- <input type="datetime-local"> <-> Java Instant ----------
// The input has no time zone. Staff type Riyadh time (UTC+3, no daylight saving), so we add "+03:00"
// before sending, and show instants back in Riyadh time - whatever zone the staff's computer uses.

/** "2026-11-10T14:00" (typed in Riyadh) -> "2026-11-10T11:00:00.000Z" */
export function fromRiyadhInput(local: string): string {
  return new Date(`${local}:00+03:00`).toISOString()
}

/** ISO instant -> "2026-11-10T14:00" for the input's value. */
export function toRiyadhInput(iso: string | null | undefined): string {
  if (!iso) return ''
  return new Date(new Date(iso).getTime() + 3 * 3600_000).toISOString().slice(0, 16)
}

/** Now + some hours, ready for a datetime-local input. */
export function riyadhInputIn(hours: number): string {
  return toRiyadhInput(new Date(Date.now() + hours * 3600_000).toISOString())
}

/** "14:30:00" -> "14:30" (Java LocalTime -> <input type="time">) */
export function hhmm(time: string | null | undefined): string {
  return time ? time.slice(0, 5) : ''
}

const timeFmt = new Intl.DateTimeFormat(LOCALE, { timeZone: ZONE, hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23' })
export function time(value: string): string {
  return timeFmt.format(new Date(value))
}
