import { useState } from 'react'
import { Link, NavLink, Outlet, useLocation } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '../auth/AuthContext'
import { endpoints } from '../api/endpoints'
import { useRealtime } from '../api/useRealtime'
import { ago, count } from '../lib/format'
import { Icon, type IconName } from '../ui/Icon'
import { Logo } from '../ui/Logo'
import { LangSwitch } from '../ui/LangSwitch'
import { InstallBanner } from '../ui/InstallBanner'
import { tr } from '../i18n'

type Item = { to: string; label: string; icon: IconName }

/** Desktop: everything in the sidebar. */
const NAV: Item[] = [
  { to: '/', label: tr('الرئيسية'), icon: 'home' },
  { to: '/card', label: tr('بطاقتي'), icon: 'card' },
  { to: '/permits', label: tr('التصاريح'), icon: 'key' },
  { to: '/subscriptions', label: tr('اشتراكاتي'), icon: 'sub' },
  { to: '/events', label: tr('الفعاليات'), icon: 'ticket' },
  { to: '/wallet', label: tr('المحفظة'), icon: 'wallet' },
  { to: '/access-log', label: tr('سجل الدخول'), icon: 'log' },
  { to: '/account', label: tr('حسابي'), icon: 'account' },
]

/** Phone: the 5 most used at the bottom; "الخدمات" opens the rest. */
const TABS: Item[] = [
  { to: '/', label: tr('الرئيسية'), icon: 'home' },
  { to: '/permits', label: tr('التصاريح'), icon: 'key' },
  { to: '/card', label: tr('بطاقتي'), icon: 'card' },
  { to: '/wallet', label: tr('المحفظة'), icon: 'wallet' },
  { to: '/services', label: tr('الخدمات'), icon: 'grid' },
]

export function Layout() {
  useRealtime()
  const { me, logout } = useAuth()
  const { pathname } = useLocation()
  // Points change after payments, so read them fresh (not from the login-time copy).
  const profile = useQuery({ queryKey: ['me'], queryFn: endpoints.me, initialData: me ?? undefined })
  const p = profile.data

  return (
    <div className="app">
      <aside>
        <Link to="/account" className="logo" aria-label={tr('حسابي')}><Logo /></Link>
        <nav aria-label={tr('القائمة')}>
          {NAV.map((i) => (
            <NavLink key={i.to} to={i.to} end={i.to === '/'} className={({ isActive }) => `nb${isActive ? ' on' : ''}`}>
              <Icon name={i.icon} />{i.label}
            </NavLink>
          ))}
        </nav>
        <div className="side-foot">
          <button className="nb" onClick={logout}><Icon name="logout" />{tr('تسجيل الخروج')}</button>
        </div>
      </aside>

      <div className="wrap">
        <main>
          <header className="top">
            {/* the avatar + name opens "حسابي" */}
            <Link to="/account" className="hello" aria-label={tr('حسابي')}>
              <div className="av" aria-hidden="true">{p?.firstName.charAt(0)}</div>
              <div>
                <b>{tr('أهلًا يا')}{' '}{p?.firstName}</b>
                <small>{tr('نقاطك')}{' '}<span className="num" style={{ color: 'var(--pr)' }}>{count(p?.points)}</span></small>
              </div>
            </Link>
            <LangSwitch className="ib lang-ib" />
            <Bell />
            <ThemeToggle />
          </header>
          <InstallBanner />
          {/* keyed by the page: each page plays its entrance (blocks slide up one after another) */}
          <div key={pathname} className="page-in"><Outlet /></div>
          <footer className="ft">{tr('© 2026 VORNX · بطاقة واحدة لكل شيء')}</footer>
        </main>
      </div>

      <nav className="tabbar" aria-label={tr('التنقل السريع')}>
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
      <h2>{tr('كل الخدمات')}</h2>
      <div className="tiles">
        {NAV.filter((i) => i.to !== '/').map((i) => (
          <NavLink key={i.to} to={i.to} className="tile">
            <span className="ic"><Icon name={i.icon} /></span><b>{i.label}</b>
          </NavLink>
        ))}
        <button className="tile" onClick={logout}>
          <span className="ic"><Icon name="logout" /></span><b>{tr('تسجيل الخروج')}</b>
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
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content', next === 'dark' ? '#080B14' : '#F4F6FB')
    try { localStorage.setItem('vornx.customer.theme', next) } catch { /* ignore */ }
    setDark(!dark)
  }
  return (
    <button className="ib" onClick={toggle} aria-label={tr('الوضع الليلي / النهاري')} title={tr('الوضع الليلي / النهاري')}>
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
      <button className="ib" onClick={() => setOpen(!open)} aria-label={tr('التنبيهات ({0})', { 0: n })} aria-expanded={open}>
        <Icon name="bell" />
        {n > 0 && <span className="badge num">{n > 99 ? '99+' : n}</span>}
      </button>
      {open && (
        <div className="bell-panel">
          <div className="bell-head">
            <b>{tr('التنبيهات')}</b>
            {n > 0 && <button className="link" onClick={() => readAll.mutate()}>{tr('تعليم الكل كمقروء')}</button>}
          </div>
          {list.isLoading && <p className="mu" style={{ padding: '0 16px' }}>{tr('جارٍ التحميل…')}</p>}
          {list.data?.content.length === 0 && <p className="mu" style={{ padding: '0 16px' }}>{tr('لا توجد تنبيهات.')}</p>}
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
