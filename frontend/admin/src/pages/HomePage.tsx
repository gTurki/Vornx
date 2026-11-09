import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { endpoints } from '../api/endpoints'
import { useAuth } from '../auth/AuthContext'
import { ago, count, date, money } from '../lib/format'
import { ACTIONS, CONTRACT_STATUS, PRIORITY } from '../lib/labels'
import { Empty, ErrorBox, Loading, Num, Tag } from '../ui/bits'

/** "نظرة عامة": the numbers that matter today, and what needs attention now. */
export function HomePage() {
  const { me } = useAuth()
  const summary = useQuery({ queryKey: ['summary'], queryFn: endpoints.summary })
  const attention = useQuery({ queryKey: ['contracts', true], queryFn: () => endpoints.contracts(true) })
  const tickets = useQuery({ queryKey: ['tickets', { status: 'OPEN' }], queryFn: () => endpoints.tickets({ status: 'OPEN' }) })
  const activity = useQuery({ queryKey: ['activity', { home: true }], queryFn: () => endpoints.activity({ size: 6 }) })
  const s = summary.data

  return (
    <>
      <p className="lead">أهلًا {me?.fullName}. الجهات التي تقدّم خدماتها للعميل النهائي عبر VORNX، في مكان واحد.</p>

      {summary.error ? <ErrorBox error={summary.error} /> : (
        <div className="kpis">
          <div className="kp"><span className="num">{count(s?.activePartners)}</span><span>جهة شريكة نشطة</span></div>
          <div className="kp"><span className="num">{money(s?.monthlyRecurringRevenue)}</span><span>الإيراد الشهري، ر.س</span></div>
          <div className={`kp${s && s.contractsNeedingAttention > 0 ? ' warn' : ''}`}>
            <span className="num">{count(s?.contractsNeedingAttention)}</span><span>عقود تحتاج متابعة</span>
          </div>
          <div className={`kp${s && s.overdueInvoices > 0 ? ' bad' : ''}`}>
            <span className="num">{count(s?.overdueInvoices)}</span>
            <span>فواتير متأخرة · <Num>{money(s?.overdueAmount)}</Num> ر.س</span>
          </div>
          <div className="kp"><span className="num">{count(s?.openSupportTickets)}</span><span>طلبات دعم مفتوحة</span></div>
        </div>
      )}

      <div className="cols">
        <section>
          <h2>تحتاج متابعة الآن</h2>
          <div className="panel">
            {attention.isLoading && <Loading />}
            {attention.data?.length === 0 && <Empty>كل العقود سارية. لا شيء يحتاج متابعة.</Empty>}
            {attention.data?.map((c) => (
              <Link to="/contracts" key={c.id} className="rem" style={{ color: 'inherit', textDecoration: 'none' }}>
                <div>
                  <b>{c.partnerName}</b>
                  <div className="mu">{c.planName} · ينتهي {date(c.endDate)}</div>
                </div>
                <div className="bar"><i style={{ width: `${c.progressPercent}%` }} /></div>
                <Tag label={CONTRACT_STATUS[c.status]} />
              </Link>
            ))}
          </div>

          <h2 className="section">طلبات الدعم المفتوحة</h2>
          <div className="panel">
            {tickets.data?.content.length === 0 && <Empty>لا توجد طلبات مفتوحة.</Empty>}
            {tickets.data?.content.map((t) => (
              <Link to="/support" key={t.id} className="li" style={{ color: 'inherit', textDecoration: 'none' }}>
                <span><b>{t.subject}</b><span className="mu"> · {t.partnerName}</span></span>
                <Tag label={PRIORITY[t.priority]} />
              </Link>
            ))}
          </div>
        </section>

        <section>
          <h2>آخر النشاط</h2>
          <div className="panel">
            {activity.data?.content.length === 0 && <Empty>لا يوجد نشاط بعد.</Empty>}
            <div className="tl">
              {activity.data?.content.map((a) => (
                <div className="ev" key={a.id}>
                  <b>{ACTIONS[a.action] ?? a.action}</b>
                  <div className="mu">{a.actorName} · {ago(a.at)}</div>
                </div>
              ))}
            </div>
            <Link to="/activity">عرض السجل كاملًا</Link>
          </div>
        </section>
      </div>
    </>
  )
}
