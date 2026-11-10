import { useState } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../auth/AuthContext'
import { endpoints } from '../api/endpoints'
import { ago, count } from '../lib/format'
import { Icon, type IconName } from '../ui/Icon'

type Item = { to: string; label: string; icon: IconName }

/** Desktop: everything in the sidebar. */
const NAV: Item[] = [
  { to: '/', label: 'الرئيسية', icon: 'home' },
  { to: '/card', label: 'بطاقتي', icon: 'card' },
  { to: '/permits', label: 'التصاريح', icon: 'key' },
  { to: '/subscriptions', label: 'اشتراكاتي', icon: 'sub' },
  { to: '/events', label: 'الفعاليات', icon: 'ticket' },
  { to: '/wallet', label: 'المحفظة', icon: 'wallet' },
  { to: '/access-log', label: 'سجل الدخول', icon: 'log' },
  { to: '/account', label: 'حسابي', icon: 'account' },
]

/** Phone: the 5 most used at the bottom; "الخدمات" opens the rest. */
const TABS: Item[] = [
  { to: '/', label: 'الرئيسية', icon: 'home' },
  { to: '/permits', label: 'التصاريح', icon: 'key' },
  { to: '/card', label: 'بطاقتي', icon: 'card' },
  { to: '/wallet', label: 'المحفظة', icon: 'wallet' },
  { to: '/services', label: 'الخدمات', icon: 'grid' },
]

export function Layout() {
  const { me, logout } = useAuth()
  // Points change after payments, so read them fresh (not from the login-time copy).
  const profile = useQuery({ queryKey: ['me'], queryFn: endpoints.me, initialData: me ?? undefined })
  const p = profile.data

  return (
    <div className="app">
      <aside>
        <div className="logo">VORNX</div>
        <nav aria-label="القائمة">
          {NAV.map((i) => (
            <NavLink key={i.to} to={i.to} end={i.to === '/'} className={({ isActive }) => `nb${isActive ? ' on' : ''}`}>
              <Icon name={i.icon} />{i.label}
            </NavLink>
          ))}
        </nav>
        <div className="side-foot">
          <button className="nb" onClick={logout}><Icon name="logout" />تسجيل الخروج</button>
        </div>
      </aside>

      <div className="wrap">
        <main>
          <header className="top">
            <div className="hello">
              <div className="av" aria-hidden="true">{p?.firstName.charAt(0)}</div>
              <div>
                <b>أهلًا يا {p?.firstName}</b>
                <small>نقاطك <span className="num" style={{ color: 'var(--pr)' }}>{count(p?.points)}</span></small>
              </div>
            </div>
            <Bell />
            <ThemeToggle />
          </header>
          <Outlet />
          <footer className="ft">© 2026 VORNX · بطاقة واحدة لكل شيء</footer>
        </main>
      </div>

      <nav className="tabbar" aria-label="التنقل السريع">
        {TABS.map((i) => (
          <NavLink key={i.to} to={i.to} end={i.to === '/'} className={({ isActive }) => (isActive ? 'on' : '')}>
            <Icon name={i.icon} />{i.label}
          </NavLink>
        ))}
      </nav>
    </div>
  )
}

/** On phones, "الخدمات" lists every page (the sidebar is hidden there). */
export function ServicesPage() {
  const { logout } = useAuth()
  return (
    <>
      <h2>كل الخدمات</h2>
      <div className="tiles">
        {NAV.filter((i) => i.to !== '/').map((i) => (
          <NavLink key={i.to} to={i.to} className="tile">
            <span className="ic"><Icon name={i.icon} /></span><b>{i.label}</b>
          </NavLink>
        ))}
        <button className="tile" onClick={logout}>
          <span className="ic"><Icon name="logout" /></span><b>تسجيل الخروج</b>
        </button>
      </div>
    </>
  )
}

function ThemeToggle() {
  const [dark, setDark] = useState(document.documentElement.dataset.theme === 'dark')
  const toggle = () => {
    const next = dark ? 'light' : 'dark'
    document.documentElement.dataset.theme = next
    try { localStorage.setItem('vornx.customer.theme', next) } catch { /* ignore */ }
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
  const unread = useQuery({ queryKey: ['notifications', 'unread'], queryFn: endpoints.unreadCount, refetchInterval: 30_000 })
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
          {list.isLoading && <p className="mu" style={{ padding: '0 16px' }}>جارٍ التحميل…</p>}
          {list.data?.content.length === 0 && <p className="mu" style={{ padding: '0 16px' }}>لا توجد تنبيهات.</p>}
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
