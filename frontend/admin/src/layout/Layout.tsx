import { useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../auth/AuthContext'
import { endpoints } from '../api/endpoints'
import { useRealtime } from '../api/useRealtime'
import { ago } from '../lib/format'
import { ADMIN_ROLE } from '../lib/labels'
import { Icon, type IconName } from '../ui/Icon'

/** The sidebar groups from the design: "الإدارة" and "المنصة". */
const NAV: { group: string; items: { to: string; label: string; icon: IconName }[] }[] = [
  {
    group: 'الإدارة',
    items: [
      { to: '/', label: 'نظرة عامة', icon: 'home' },
      { to: '/partners', label: 'الجهات', icon: 'partners' },
      { to: '/contracts', label: 'العقود والتجديدات', icon: 'contract' },
      { to: '/invoices', label: 'الفواتير', icon: 'invoice' },
      { to: '/support', label: 'الدعم', icon: 'support' },
      { to: '/inquiries', label: 'رسائل الموقع', icon: 'mail' },
    ],
  },
  {
    group: 'المنصة',
    items: [
      { to: '/activity', label: 'سجل النشاط', icon: 'activity' },
      { to: '/account', label: 'حسابي والأجهزة', icon: 'account' },
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
        <p className="logo-sub">منصة الجهات مقدمة الخدمات</p>
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
            <small>{me ? ADMIN_ROLE[me.role] : ''}</small>
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
    try { localStorage.setItem('vornx.admin.theme', next) } catch { /* ignore */ }
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
