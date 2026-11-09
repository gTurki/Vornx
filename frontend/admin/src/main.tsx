import { StrictMode, type ReactNode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ApiError } from './api/client'
import { AuthProvider, useAuth } from './auth/AuthContext'
import { Layout } from './layout/Layout'
import { ActivityPage, AccountPage } from './pages/ActivityAndAccount'
import { ForgotPasswordPage, LoginPage, ResetPasswordPage } from './pages/AuthPages'
import { ContractsPage, InvoicesPage } from './pages/BillingPages'
import { HomePage } from './pages/HomePage'
import { PartnersPage } from './pages/PartnersPage'
import { SupportPage } from './pages/SupportPage'
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
              <Route element={<Protected><Layout /></Protected>}>
                <Route index element={<HomePage />} />
                <Route path="partners" element={<PartnersPage />} />
                <Route path="contracts" element={<ContractsPage />} />
                <Route path="invoices" element={<InvoicesPage />} />
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
