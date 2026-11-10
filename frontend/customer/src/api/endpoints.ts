import { api } from './client'
import type {
  AccessDecision, AccessEvent, AppNotification, AuthResponse, Card, Customer, EventTicket, Page, Permit, Plan,
  RegisterRequest, ReminderOption, ServiceRequest, Session, Subscription, TopUp, TopUpOptions, VEvent, Wallet,
  WalletTransaction,
} from './types'

const C = '/api/customer'

// One function per backend endpoint the app uses. Nothing takes a customer id: the backend
// reads it from the login token, so one customer can never ask for another's data.
export const endpoints = {
  // ---------- auth ----------
  login: (email: string, password: string) =>
    api<AuthResponse>('/api/public/auth/customer/login', { method: 'POST', body: { email, password }, auth: false }),
  register: (body: RegisterRequest) =>
    api<AuthResponse>('/api/public/auth/customer/register', { method: 'POST', body, auth: false }),
  logout: (refreshToken: string) =>
    api<void>('/api/public/auth/logout', { method: 'POST', body: { refreshToken }, auth: false }),
  forgotPassword: (email: string) =>
    api<void>('/api/public/auth/customer/forgot-password', { method: 'POST', body: { email }, auth: false }),
  resetPassword: (token: string, password: string, confirmPassword: string) =>
    api<void>('/api/public/auth/reset-password', { method: 'POST', body: { token, password, confirmPassword }, auth: false }),
  me: () => api<Customer>(`${C}/me`),
  changePassword: (currentPassword: string, newPassword: string, confirmPassword: string) =>
    api<void>(`${C}/me/password`, { method: 'POST', body: { currentPassword, newPassword, confirmPassword } }),
  sessions: () => api<Session[]>(`${C}/sessions`),
  revokeSession: (id: number) => api<void>(`${C}/sessions/${id}`, { method: 'DELETE' }),
  revokeOtherSessions: () => api<{ revoked: number }>(`${C}/sessions?keepCurrent=true`, { method: 'DELETE' }),

  // ---------- card ----------
  cards: () => api<Card[]>(`${C}/cards`),
  reportLost: (id: number) => api<Card>(`${C}/cards/${id}/report-lost`, { method: 'POST' }),

  // ---------- permits & doors ----------
  permits: () => api<Permit[]>(`${C}/permits`),
  openDoor: (accessPointId: number) =>
    api<AccessDecision>(`${C}/access-points/${accessPointId}/open?direction=ENTRY`, { method: 'POST' }),
  accessLog: (page: number) => api<Page<AccessEvent>>(`${C}/access-log?size=20&page=${page}`),

  // ---------- wallet ----------
  wallet: () => api<Wallet>(`${C}/wallet`),
  transactions: (page: number) => api<Page<WalletTransaction>>(`${C}/wallet/transactions?size=15&page=${page}`),
  topUpOptions: () => api<TopUpOptions>(`${C}/wallet/top-up-options`),
  /** Card top-up: returns Moyasar's payment page (checkoutUrl). */
  startTopUp: (amount: number) => api<TopUp>(`${C}/wallet/top-ups`, { method: 'POST', body: { amount } }),
  topUpStatus: (reference: string) => api<TopUp>(`${C}/wallet/top-ups/${encodeURIComponent(reference)}`),
  /** Dev only: simulated money, no card. */
  topUp: (amount: number) => api<WalletTransaction>(`${C}/wallet/top-up`, { method: 'POST', body: { amount } }),
  transfer: (toVornxId: string, amount: number, note?: string) =>
    api<WalletTransaction>(`${C}/wallet/transfers`, { method: 'POST', body: { toVornxId, amount, note } }),

  // ---------- plans & subscriptions ----------
  plans: (page: number) => api<Page<Plan>>(`${C}/plans?size=20&page=${page}`),
  subscriptions: () => api<Subscription[]>(`${C}/subscriptions`),
  subscribe: (planId: number) => api<ServiceRequest>(`${C}/subscriptions`, { method: 'POST', body: { planId } }),
  renew: (subscriptionId: number) => api<ServiceRequest>(`${C}/subscriptions/${subscriptionId}/renew`, { method: 'POST' }),
  requests: () => api<ServiceRequest[]>(`${C}/subscription-requests`),
  cancelRequest: (id: number) => api<ServiceRequest>(`${C}/subscription-requests/${id}/cancel`, { method: 'POST' }),

  // ---------- events ----------
  events: (page: number) => api<Page<VEvent>>(`${C}/events?size=20&page=${page}`),
  registerEvent: (id: number) => api<EventTicket>(`${C}/events/${id}/register`, { method: 'POST' }),
  tickets: () => api<EventTicket[]>(`${C}/tickets`),
  cancelTicket: (id: number) => api<EventTicket>(`${C}/tickets/${id}/cancel`, { method: 'POST' }),
  setReminder: (id: number, reminder: ReminderOption | null) =>
    api<EventTicket>(`${C}/tickets/${id}/reminder`, { method: 'PUT', body: { reminder } }),

  // ---------- notifications ----------
  notifications: () => api<Page<AppNotification>>(`${C}/notifications?size=10`),
  unreadCount: () => api<{ unread: number }>(`${C}/notifications/unread-count`),
  readAllNotifications: () => api<{ marked: number }>(`${C}/notifications/read-all`, { method: 'POST' }),
}
