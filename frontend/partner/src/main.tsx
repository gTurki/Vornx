import { StrictMode, type ReactNode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ApiError } from './api/client'
import { AuthProvider, useAuth } from './auth/AuthContext'
import { Layout } from './layout/Layout'
import { ActivityPage, AccountPage } from './pages/ActivityAndAccount'
import { AcceptInvitePage, ForgotPasswordPage, LoginPage, ResetPasswordPage } from './pages/AuthPages'
import { BillingPage } from './pages/BillingPage'
import { EventsPage } from './pages/EventsPage'
import { HomePage } from './pages/HomePage'
import { LivePage } from './pages/LivePage'
import { PermitsPage } from './pages/PermitsPage'
import { PlansPage } from './pages/PlansPage'
import { SitesPage } from './pages/SitesPage'
import { SubscriptionsPage } from './pages/SubscriptionsPage'
import { SupportPage } from './pages/SupportPage'
import { TeamPage } from './pages/TeamPage'
import { ToastProvider } from './ui/Toast'
import './styles.css'

// TanStack Query = server data cache: loading/error states, refetching, and no duplicate requests.
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 15_000,
      refetchOnWindowFocus: true,
      // Don't retry errors that won't fix themselves (403, 404, validation...).
      retry: (n, e) => n < 2 && (!(e instanceof ApiError) || e.status === 0 || e.status >= 500),
    },
  },
})

/** Pages behind login: while checking the saved session show nothing, then go to /login if needed. */
function Protected({ children }: { children: ReactNode }) {
  const { status } = useAuth()
  if (status === 'loading') return <div className="auth-page" />
  return status === 'in' ? <>{children}</> : <Navigate to="/login" replace />
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <BrowserRouter>
          <AuthProvider>
            <Routes>
              <Route path="/login" element={<LoginPage />} />
              <Route path="/forgot-password" element={<ForgotPasswordPage />} />
              <Route path="/reset-password" element={<ResetPasswordPage />} />
              <Route path="/accept-invite" element={<AcceptInvitePage />} />
              <Route element={<Protected><Layout /></Protected>}>
                <Route index element={<HomePage />} />
                <Route path="live" element={<LivePage />} />
                <Route path="permits" element={<PermitsPage />} />
                <Route path="subscriptions" element={<SubscriptionsPage />} />
                <Route path="sites" element={<SitesPage />} />
                <Route path="plans" element={<PlansPage />} />
                <Route path="events" element={<EventsPage />} />
                <Route path="billing" element={<BillingPage />} />
                <Route path="team" element={<TeamPage />} />
                <Route path="support" element={<SupportPage />} />
                <Route path="activity" element={<ActivityPage />} />
                <Route path="account" element={<AccountPage />} />
              </Route>
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </AuthProvider>
        </BrowserRouter>
      </ToastProvider>
    </QueryClientProvider>
  </StrictMode>,
)
