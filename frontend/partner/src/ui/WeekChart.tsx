import type { PartnerOverview } from '../api/types'
import { count } from '../lib/format'
import { Empty, Loading } from './bits'

/** Arabic weekday initials, Sunday first (JavaScript's getUTCDay order), as in hotel.html. */
const DAY = ['ح', 'ن', 'ث', 'ر', 'خ', 'ج', 'س']
const FULL = new Intl.DateTimeFormat('ar-SA-u-ca-gregory-nu-latn', { weekday: 'long', day: 'numeric', month: 'long', timeZone: 'UTC' })

/**
 * The design's 7-day bar chart (plain SVG): allowed entries per day, today in full color.
 * Hover a bar: the date, allowed and denied entries.
 */
export function WeekChart({ days }: { days: PartnerOverview['lastSevenDays'] | undefined }) {
  if (!days) return <Loading />
  const max = Math.max(...days.map((d) => d.allowed))
  if (max === 0 && days.every((d) => d.denied === 0)) {
    return <Empty>لا توجد عمليات دخول في آخر 7 أيام. تظهر هنا فور أن تمسح أجهزتك أول بطاقة.</Empty>
  }
  const W = 520, H = 150, bw = 44
  return (
    <svg className="chart" viewBox={`0 0 ${W} ${H}`} width="100%" role="img"
      aria-label={`الدخول المسموح خلال 7 أيام: ${days.map((d) => d.allowed).join('، ')}`}>
      {days.map((d, i) => {
        const h = max > 0 ? (d.allowed / max) * (H - 40) : 0
        const x = 20 + i * 72
        const date = new Date(d.date + 'T12:00:00Z')
        return (
          <g key={d.date}>
            <rect x={x} y={H - 22 - h} width={bw} height={Math.max(h, 1)} rx="4" fill="var(--p2)" opacity={i === days.length - 1 ? 1 : 0.55}>
              <title>{`${FULL.format(date)}: ${count(d.allowed)} مسموح · ${count(d.denied)} مرفوض`}</title>
            </rect>
            <text x={x + bw / 2} y={H - 6} fontSize="11" textAnchor="middle" fill="var(--mu)">{DAY[date.getUTCDay()]}</text>
            <text x={x + bw / 2} y={H - 27 - h} fontSize="11" textAnchor="middle" fill="var(--tx)">{count(d.allowed)}</text>
          </g>
        )
      })}
    </svg>
  )
}
