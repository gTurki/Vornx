import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { endpoints } from '../api/endpoints'
import { useAuth } from '../auth/AuthContext'
import { ago, count, date } from '../lib/format'
import { ACTIONS, CONTRACT_STATUS, REQUEST_TYPE } from '../lib/labels'
import { Empty, ErrorBox, Loading, Num, Tag } from '../ui/bits'
import { useSites } from '../ui/pickers'
import { AccessRow } from './LivePage'

/** "نظرة عامة": today at a glance, and what is waiting for a decision. */
export function HomePage() {
  const { me } = useAuth()
  const sites = useSites()
  // size=1: we only want the totals, not the rows.
  const permits = useQuery({ queryKey: ['permits', 'total'], queryFn: () => endpoints.permits({ size: 1 }) })
  const pending = useQuery({ queryKey: ['requests', { status: 'PENDING', home: true }], queryFn: () => endpoints.requests({ status: 'PENDING', size: 5 }) })
  const live = useQuery({ queryKey: ['access-events', { home: true }], queryFn: () => endpoints.accessEvents({ size: 6 }), refetchInterval: 60_000 })
  const contract = useQuery({ queryKey: ['contract'], queryFn: endpoints.contract, retry: false })
  const activity = useQuery({ queryKey: ['activity', { home: true }], queryFn: () => endpoints.activity({ size: 5 }) })
  const c = contract.data
  const waiting = pending.data?.totalElements ?? 0

  return (
    <>
      <div className="hero">
        <div>
          <h2>أهلًا بك، {me?.partnerName}</h2>
          <p>كل ما يمر عبر VORNX في مواقعك: من يدخل، ومتى، وبأي تصريح. أصدر المفاتيح الرقمية وتابع التشغيل لحظة بلحظة.</p>
        </div>
        <Link className="btn" to="/permits">إدارة التصاريح والمفاتيح</Link>
      </div>

      <div className="kpis">
        <div className="kp"><span className="num">{count(sites.data?.length)}</span><span>موقع</span></div>
        <div className="kp"><span className="num">{count(permits.data?.totalElements)}</span><span>تصريح صادر</span></div>
        <div className={`kp${waiting > 0 ? ' warn' : ''}`}><span className="num">{count(pending.data?.totalElements)}</span><span>طلب اشتراك بانتظار قرارك</span></div>
        <div className={`kp${c && c.status !== 'ACTIVE' ? ' warn' : ''}`}>
          <span className="num">{c ? count(c.daysLeft) : '—'}</span><span>يومًا متبقية في العقد</span>
        </div>
      </div>

      <div className="cols">
        <section>
          <h2><span className="pulse" />آخر عمليات الدخول</h2>
          <div className="panel">
            {live.error && <ErrorBox error={live.error} />}
            {live.isLoading && <Loading />}
            {live.data?.content.length === 0 && <Empty>لا توجد عمليات دخول بعد. ستظهر هنا فور أن تمسح أجهزتك أول بطاقة.</Empty>}
            <div className="feed">{live.data?.content.map((e) => <AccessRow key={e.decisionNumber} e={e} />)}</div>
            <Link to="/live">فتح التشغيل المباشر</Link>
          </div>

          <h2 className="section">بانتظار قرارك</h2>
          <div className="panel">
            {pending.data?.content.length === 0 && <Empty>لا توجد طلبات معلّقة.</Empty>}
            {pending.data?.content.map((r) => (
              <Link to="/subscriptions" key={r.id} className="li" style={{ color: 'inherit', textDecoration: 'none' }}>
                <span><b>{r.customerName}</b><span className="mu"> · {r.planName} · {REQUEST_TYPE[r.type]}</span></span>
                <span className="mu">{ago(r.createdAt)}</span>
              </Link>
            ))}
          </div>
        </section>

        <section>
          <h2>العقد مع VORNX</h2>
          <div className="panel">
            {contract.isLoading && <Loading />}
            {contract.error && <Empty>لا يوجد عقد ساري بعد. سيضيفه مدير حسابك.</Empty>}
            {c && (
              <>
                <div className="li"><b>{c.planName}</b><Tag label={CONTRACT_STATUS[c.status]} /></div>
                <div className="mu">ينتهي {date(c.endDate)} · <Num>{c.number}</Num></div>
                <div className="bar"><i style={{ width: `${c.progressPercent}%` }} /></div>
                <p style={{ marginBottom: 0 }}><Link to="/billing">العقد والفواتير</Link></p>
              </>
            )}
          </div>

          <h2 className="section">آخر النشاط</h2>
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
