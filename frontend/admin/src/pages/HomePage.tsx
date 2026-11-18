import { Link, useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { endpoints } from '../api/endpoints'
import type { AdminOverview } from '../api/types'
import { count, days, money } from '../lib/format'
import { CONTRACT_STATUS, SECTORS, TIER } from '../lib/labels'
import { Empty, ErrorBox, Loading, Num, Tag } from '../ui/bits'
import { SECTOR_ORDER, SectorIcon } from '../ui/SectorIcon'

/**
 * "نظرة عامة", laid out like the design (index 55): the 8 services, the key numbers, 6 months of revenue,
 * what needs attention now, and the biggest partners. One request: GET /api/admin/overview.
 */
export function HomePage() {
  const overview = useQuery({ queryKey: ['overview'], queryFn: endpoints.overview })
  const o = overview.data

  if (overview.error) return <ErrorBox error={overview.error} />
  if (!o) return <Loading />

  const bySector = new Map(o.sectors.map((s) => [s.sector, s.partners]))
  const change = o.mrrChangePercent

  return (
    <>
      <p className="lead">الجهات التي تقدّم خدماتها للعميل النهائي عبر VORNX: من الدفع والدوام إلى الفنادق والمواقف، في مكان واحد.</p>

      <div className="strip">
        {SECTOR_ORDER.map((s) => (
          <Link key={s} to={`/partners?sector=${s}`} className="sv">
            <SectorIcon sector={s} />
            <b>{SECTORS[s]}</b>
            <span className="num">{count(bySector.get(s) ?? 0)}</span>
          </Link>
        ))}
      </div>

      <div className="kpis">
        <div className="kp"><span className="num">{count(o.customers)}</span><span>مستفيد نهائي</span></div>
        <div className="kp">
          <span className="num">{count(Math.round(o.monthlyRecurringRevenue))}</span><span>الإيراد الشهري، ر.س</span>
          {change !== null && (change === 0
            ? <em className="dl flat">بدون تغيير عن الشهر الماضي</em>
            : <em className={`dl${change < 0 ? ' down' : ''}`}>{change < 0 ? '▼' : '▲'} {Math.abs(change)}% عن الشهر الماضي</em>)}
        </div>
        <div className="kp"><span className="num">{count(o.activePartners)}</span><span>جهة شريكة</span></div>
        <div className={`kp${o.contractsNeedingAttention > 0 ? ' bad' : ''}`}>
          <span className="num">{count(o.contractsNeedingAttention)}</span><span>عقود تحتاج متابعة</span>
        </div>
      </div>

      <div className="cols">
        <section>
          <h2>الإيراد الشهري خلال آخر 6 أشهر</h2>
          <div className="panel"><RevenueChart months={o.revenueByMonth} /></div>
        </section>
        <section>
          <h2>تحتاج متابعة الآن</h2>
          <div className="panel"><Attention items={o.attention} /></div>
        </section>
      </div>

      <h2 style={{ marginTop: 28 }}>أكبر الجهات إيرادًا</h2>
      <TopPartners rows={o.topPartners} />

      {o.openSupportTickets > 0 && (
        <p className="mu" style={{ marginTop: 18 }}>
          طلبات دعم مفتوحة: <Num>{count(o.openSupportTickets)}</Num> · <Link to="/support">افتح الدعم</Link>
        </p>
      )}
    </>
  )
}

/** "سبتمبر" from "2026-09" */
const monthName = new Intl.DateTimeFormat('ar-SA-u-ca-gregory-nu-latn', { month: 'long', timeZone: 'UTC' })
const label = (period: string) => monthName.format(new Date(period + '-15T12:00:00Z'))

/** The design's line chart (plain SVG, no chart library): invoiced amount per month. */
function RevenueChart({ months }: { months: AdminOverview['revenueByMonth'] }) {
  const W = 520, H = 150
  const max = Math.max(...months.map((m) => m.amount))
  if (max <= 0) return <Empty>لا توجد فواتير في آخر 6 أشهر بعد. تظهر هنا بعد إصدار الفواتير الشهرية.</Empty>

  const pts = months.map((m, i) => [20 + (i * (W - 40)) / (months.length - 1), H - 26 - (m.amount / max) * (H - 50)])
  const line = pts.map((p) => p.join(',')).join(' ')
  const last = pts[pts.length - 1]
  const lastAmount = months[months.length - 1].amount

  return (
    <svg className="chart" viewBox={`0 0 ${W} ${H}`} width="100%" role="img"
      aria-label={`الإيراد الشهري: ${months.map((m) => `${label(m.period)} ${money(m.amount)}`).join('، ')}`}>
      <defs>
        <linearGradient id="ag" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="var(--p2)" stopOpacity=".3" />
          <stop offset="1" stopColor="var(--p2)" stopOpacity="0" />
        </linearGradient>
      </defs>
      {[0, 1, 2].map((j) => (
        <line key={j} x1="20" x2={W - 20} y1={24 + (j * (H - 50)) / 2} y2={24 + (j * (H - 50)) / 2} stroke="var(--ln)" strokeDasharray="3 4" />
      ))}
      <polygon points={`${line} ${last[0]},${H - 26} 20,${H - 26}`} fill="url(#ag)" />
      <polyline points={line} fill="none" stroke="var(--p2)" strokeWidth="2.5" strokeLinejoin="round" />
      {pts.map((p, i) => (
        <g key={months[i].period}>
          <circle cx={p[0]} cy={p[1]} r="4" fill="var(--sf)" stroke="var(--p2)" strokeWidth="2">
            <title>{`${label(months[i].period)}: ${money(months[i].amount)} ر.س`}</title>
          </circle>
          <text x={p[0]} y={H - 4} textAnchor="middle" fontSize="11" fill="var(--mu)">{label(months[i].period)}</text>
        </g>
      ))}
      <text x={last[0]} y={last[1] - 12} textAnchor="middle" fontSize="12" fontWeight="700" fill="var(--pr)">
        {count(Math.round(lastAmount))}
      </text>
    </svg>
  )
}

function Attention({ items }: { items: AdminOverview['attention'] }) {
  if (items.length === 0) return <Empty>كل العقود سارية ولا توجد فواتير متأخرة.</Empty>
  return (
    <>
      {items.map((a) => a.kind === 'CONTRACT' ? (
        <Link key={`c${a.id}`} to="/contracts" className="li">
          <span>{a.partnerName}<small className="mu">{a.daysLeft < 0 ? `انتهى منذ ${days(-a.daysLeft)}` : `يتبقى ${days(a.daysLeft)}`}</small></span>
          {a.contractStatus && <Tag label={CONTRACT_STATUS[a.contractStatus]} />}
        </Link>
      ) : (
        <Link key={`i${a.id}`} to="/invoices" className="li">
          <span>{a.partnerName}
            <small className="mu">فاتورة <Num>{a.detail}</Num> · <Num>{money(a.amount)}</Num> ر.س · متأخرة {days(-a.daysLeft)}</small>
          </span>
          <Tag label={{ text: 'فاتورة متأخرة', tone: 'bad' }} />
        </Link>
      ))}
    </>
  )
}

function TopPartners({ rows }: { rows: AdminOverview['topPartners'] }) {
  const navigate = useNavigate()
  if (rows.length === 0) return <div className="panel"><Empty>لا توجد عقود سارية بعد.</Empty></div>
  return (
    <div className="tw">
      <table>
        <thead>
          <tr><th>الجهة</th><th>الخدمة</th><th>الباقة</th><th>المستفيدون</th><th>الإيراد الشهري</th><th>العقد</th></tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.partnerId} className="click" onClick={() => navigate(`/partners?q=${encodeURIComponent(r.name)}`)}>
              <td><div className="ent"><SectorIcon sector={r.sector} /><div><b>{r.name}</b><small>{r.city ?? ''}</small></div></div></td>
              <td>{SECTORS[r.sector]}</td>
              <td>{r.planName}<small className="mu" style={{ display: 'block' }}>{TIER[r.tier]}</small></td>
              <td><Num>{count(r.beneficiaries)}</Num></td>
              <td><Num>{count(Math.round(r.monthlyFee))}</Num> ر.س</td>
              <td><Tag label={CONTRACT_STATUS[r.contractStatus]} /></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
