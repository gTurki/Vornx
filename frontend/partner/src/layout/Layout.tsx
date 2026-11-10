import { useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../auth/AuthContext'
import { endpoints } from '../api/endpoints'
import { useRealtime } from '../api/useRealtime'
import { ago } from '../lib/format'
import { PARTNER_ROLE } from '../lib/labels'
import { Icon, type IconName } from '../ui/Icon'

/** The sidebar, grouped: daily operations, setup, and the account with VORNX. */
const NAV: { group: string; items: { to: string; label: string; icon: IconName }[] }[] = [
  {
    group: 'التشغيل',
    items: [
      { to: '/', label: 'نظرة عامة', icon: 'home' },
      { to: '/live', label: 'التشغيل المباشر', icon: 'live' },
      { to: '/permits', label: 'التصاريح والمفاتيح', icon: 'key' },
      { to: '/subscriptions', label: 'طلبات الاشتراك', icon: 'check' },
    ],
  },
  {
    group: 'الإعداد',
    items: [
      { to: '/sites', label: 'المواقع ونقاط الدخول', icon: 'site' },
      { to: '/plans', label: 'الباقات', icon: 'plan' },
      { to: '/events', label: 'الفعاليات', icon: 'event' },
    ],
  },
  {
    group: 'الحساب',
    items: [
      { to: '/billing', label: 'العقد والفواتير', icon: 'contract' },
      { to: '/team', label: 'الفريق والأجهزة', icon: 'team' },
      { to: '/support', label: 'الدعم', icon: 'support' },
      { to: '/activity', label: 'سجل النشاط', icon: 'activity' },
      { to: '/account', label: 'حسابي', icon: 'account' },
    ],
  },
]

const TITLES: Record<string, string> = Object.fromEntries(NAV.flatMap((g) => g.items.map((i) => [i.to, i.label])))

export function Layout() {
  useRealtime()
  const { me, logout } = useAuth()
  const { pathname } = useLocation()
  const title = TITLES['/' + (pathname.split('/')[1] ?? '')] ?? 'VORNX'

  return (
    <div className="app">
      <aside>
        <div className="logo">VORNX</div>
        <div className="host"><b>{me?.partnerName}</b>بوابة الشريك</div>
        {NAV.map((g) => (
          <nav key={g.group} aria-label={g.group}>
            <div className="nav-group">{g.group}</div>
            {g.items.map((i) => (
              <NavLink key={i.to} to={i.to} end={i.to === '/'} className={({ isActive }) => `nb${isActive ? ' on' : ''}`}>
                <Icon name={i.icon} />
                {i.label}
              </NavLink>
            ))}
          </nav>
        ))}
        <div className="side-foot">
          <div className="me">
            <b>{me?.fullName}</b>
            <small>{me ? PARTNER_ROLE[me.role] : ''}</small>
          </div>
          <button className="nb" onClick={logout}>
            <Icon name="logout" />
            تسجيل الخروج
          </button>
        </div>
      </aside>

      <div className="wrap">
        <header className="top">
          <h1>{title}</h1>
          <Bell />
          <ThemeToggle />
        </header>
        <main>
          <Outlet />
          <footer className="ft">
            <span>© 2026 <b>VORNX</b> — للتقنية والأنظمة الذكية</span>
            <span><i className="dot" />متصل بالخادم</span>
          </footer>
        </main>
      </div>
    </div>
  )
}

function ThemeToggle() {
  const [dark, setDark] = useState(document.documentElement.dataset.theme === 'dark')
  const toggle = () => {
    const next = dark ? 'light' : 'dark'
    document.documentElement.dataset.theme = next
    try { localStorage.setItem('vornx.partner.theme', next) } catch { /* ignore */ }
    setDark(!dark)
  }
  return (
    <button className="ib" onClick={toggle} aria-label="الوضع الليلي / النهاري" title="الوضع الليلي / النهاري">
      <Icon name={dark ? 'sun' : 'moon'} />
    </button>
  )
}

/** The bell: unread count (checked every 30 s) and the latest notifications. */
function Bell() {
  const [open, setOpen] = useState(false)
  const qc = useQueryClient()
  const unread = useQuery({ queryKey: ['notifications', 'unread'], queryFn: endpoints.unreadCount, refetchInterval: 120_000 }) // fallback; live updates come over the WebSocket
  const list = useQuery({ queryKey: ['notifications', 'list'], queryFn: endpoints.notifications, enabled: open })
  const readAll = useMutation({
    mutationFn: endpoints.readAllNotifications,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['notifications'] }),
  })
  const n = unread.data?.unread ?? 0

  return (
    <div className="bell">
      <button className="ib" onClick={() => setOpen(!open)} aria-label={`التنبيهات (${n})`} aria-expanded={open}>
        <Icon name="bell" />
        {n > 0 && <span className="badge num">{n > 99 ? '99+' : n}</span>}
      </button>
      {open && (
        <div className="bell-panel">
          <div className="bell-head">
            <b>التنبيهات</b>
            {n > 0 && <button className="link" onClick={() => readAll.mutate()}>تعليم الكل كمقروء</button>}
          </div>
          {list.isLoading && <p className="mu pad">جارٍ التحميل…</p>}
          {list.data?.content.length === 0 && <p className="mu pad">لا توجد تنبيهات.</p>}
          {list.data?.content.map((x) => (
            <div key={x.id} className={`notif${x.read ? '' : ' unread'}`}>
              <b>{x.titleAr}</b>
              <span>{x.bodyAr}</span>
              <small className="mu">{ago(x.createdAt)}</small>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
