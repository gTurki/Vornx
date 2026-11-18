import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../auth/AuthContext'
import { endpoints } from '../api/endpoints'
import { useRealtime } from '../api/useRealtime'
import { ago } from '../lib/format'
import { ADMIN_ROLE } from '../lib/labels'
import { Icon, type IconName } from '../ui/Icon'
import { Logo } from '../ui/Logo'

/** The sidebar groups from the design: "الإدارة" and "المنصة". */
const NAV: { group: string; items: { to: string; label: string; icon: IconName }[] }[] = [
  {
    group: 'الإدارة',
    items: [
      { to: '/', label: 'نظرة عامة', icon: 'home' },
      { to: '/partners', label: 'الجهات', icon: 'partners' },
      { to: '/applications', label: 'طلبات الانضمام', icon: 'plus' },
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
  // new "انضم كشريك" requests: a badge on the menu (refreshed with the live notifications too)
  const pending = useQuery({ queryKey: ['applications-pending'], queryFn: endpoints.pendingApplications, refetchInterval: 120_000 })
  const pendingApps = pending.data?.count ?? 0
  const title = TITLES['/' + (pathname.split('/')[1] ?? '')] ?? 'VORNX'

  return (
    <div className="app">
      <aside>
        <div className="logo"><Logo /><Logo variant="mark" /></div>
        <p className="logo-sub">منصة الجهات مقدمة الخدمات</p>
        {NAV.map((g) => (
          <nav key={g.group} aria-label={g.group}>
            <div className="nav-group">{g.group}</div>
            {g.items.map((i) => (
              <NavLink key={i.to} to={i.to} end={i.to === '/'} className={({ isActive }) => `nb${isActive ? ' on' : ''}`}>
                <Icon name={i.icon} />
                {i.label}
                {i.to === '/applications' && pendingApps > 0 && <span className="cnt num" aria-label={`${pendingApps} بانتظار القرار`}>{pendingApps}</span>}
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
          <SearchBox />
          <Bell />
          <Link to="/account" className="ib me-chip" aria-label="حسابي">
            <b className="av">{me?.fullName.trim().charAt(0).toUpperCase()}</b>
            <span>{me?.fullName.split(' ')[0]}</span>
          </Link>
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

/** "ابحث عن جهة أو خدمة ( / )": Enter opens the partners list filtered by the text. "/" jumps here. */
function SearchBox() {
  const navigate = useNavigate()
  const ref = useRef<HTMLInputElement>(null)
  const [q, setQ] = useState('')

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const t = e.target as HTMLElement
      if (e.key === '/' && !['INPUT', 'TEXTAREA', 'SELECT'].includes(t.tagName) && !t.isContentEditable) {
        e.preventDefault()
        ref.current?.focus()
      }
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [])

  function submit(e: FormEvent) {
    e.preventDefault()
    navigate(q.trim() ? `/partners?q=${encodeURIComponent(q.trim())}` : '/partners')
  }

  return (
    <form className="srch-form" role="search" onSubmit={submit}>
      <input ref={ref} className="srch" type="search" value={q} onChange={(e) => setQ(e.target.value)}
        placeholder="ابحث عن جهة أو خدمة  ( / )" aria-label="ابحث عن جهة" />
    </form>
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
