import { api } from './client'
import type {
  ActivityLog, AdminUser, AppNotification, AuthResponse, BillingSummary, Contract, ContractRequest,
  InviteResponse, Invoice, InvoiceStatus, Page, Partner, PartnerRequest, PartnerRole, PartnerStatus,
  PartnerUser, Sector, Session, Site, SupportTicket, TicketPriority, TicketStatus,
} from './types'

/** Builds "?a=1&b=x", skipping empty values. */
function qs(params: Record<string, string | number | boolean | undefined | null>) {
  const s = new URLSearchParams()
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') s.set(k, String(v))
  })
  const str = s.toString()
  return str ? `?${str}` : ''
}

// One function per backend endpoint used by the dashboard. The paths match the Spring controllers.
export const endpoints = {
  // ---------- auth ----------
  login: (email: string, password: string) =>
    api<AuthResponse>('/api/public/auth/admin/login', { method: 'POST', body: { email, password }, auth: false }),
  logout: (refreshToken: string) =>
    api<void>('/api/public/auth/logout', { method: 'POST', body: { refreshToken }, auth: false }),
  forgotPassword: (email: string) =>
    api<void>('/api/public/auth/admin/forgot-password', { method: 'POST', body: { email }, auth: false }),
  resetPassword: (token: string, password: string, confirmPassword: string) =>
    api<void>('/api/public/auth/reset-password', { method: 'POST', body: { token, password, confirmPassword }, auth: false }),
  me: () => api<AdminUser>('/api/admin/me'),
  changePassword: (currentPassword: string, newPassword: string, confirmPassword: string) =>
    api<void>('/api/admin/me/password', { method: 'POST', body: { currentPassword, newPassword, confirmPassword } }),
  sessions: () => api<Session[]>('/api/admin/sessions'),
  revokeSession: (id: number) => api<void>(`/api/admin/sessions/${id}`, { method: 'DELETE' }),
  revokeOtherSessions: () => api<{ revoked: number }>('/api/admin/sessions?keepCurrent=true', { method: 'DELETE' }),

  // ---------- dashboard ----------
  summary: () => api<BillingSummary>('/api/admin/billing/summary'),

  // ---------- partners ----------
  partners: (p: { sector?: Sector; status?: PartnerStatus; q?: string; page?: number }) =>
    api<Page<Partner>>(`/api/admin/partners${qs({ ...p, size: 15, sort: 'name,asc' })}`),
  partner: (id: number) => api<Partner>(`/api/admin/partners/${id}`),
  createPartner: (body: PartnerRequest) => api<Partner>('/api/admin/partners', { method: 'POST', body }),
  setPartnerStatus: (id: number, status: PartnerStatus) =>
    api<Partner>(`/api/admin/partners/${id}/status`, { method: 'PATCH', body: { status } }),
  partnerUsers: (id: number) => api<PartnerUser[]>(`/api/admin/partners/${id}/users`),
  invitePartnerUser: (id: number, body: { fullName: string; email: string; role: PartnerRole }) =>
    api<InviteResponse>(`/api/admin/partners/${id}/users`, { method: 'POST', body }),
  partnerSites: (id: number) => api<Site[]>(`/api/admin/partners/${id}/sites`),
  partnerContracts: (id: number) => api<Contract[]>(`/api/admin/partners/${id}/contracts`),
  createContract: (partnerId: number, body: ContractRequest) =>
    api<Contract>(`/api/admin/partners/${partnerId}/contracts`, { method: 'POST', body }),

  // ---------- contracts & invoices ----------
  contracts: (attention: boolean) => api<Contract[]>(`/api/admin/contracts${qs({ attention })}`),
  renewContract: (id: number, months = 12) =>
    api<Contract>(`/api/admin/contracts/${id}/renew`, { method: 'POST', body: { months } }),
  terminateContract: (id: number) => api<Contract>(`/api/admin/contracts/${id}/terminate`, { method: 'POST' }),
  invoices: (p: { status?: InvoiceStatus; page?: number }) =>
    api<Page<Invoice>>(`/api/admin/invoices${qs({ ...p, size: 15 })}`),
  generateInvoices: (period: string) =>
    api<Invoice[]>('/api/admin/invoices/generate', { method: 'POST', body: { period } }),
  markInvoicePaid: (id: number) => api<Invoice>(`/api/admin/invoices/${id}/mark-paid`, { method: 'POST' }),

  // ---------- support ----------
  tickets: (p: { status?: TicketStatus; priority?: TicketPriority; mine?: boolean; page?: number }) =>
    api<Page<SupportTicket>>(`/api/admin/support-tickets${qs({ ...p, size: 15 })}`),
  ticket: (id: number) => api<SupportTicket>(`/api/admin/support-tickets/${id}`),
  replyTicket: (id: number, body: string) =>
    api<SupportTicket>(`/api/admin/support-tickets/${id}/messages`, { method: 'POST', body: { body } }),
  updateTicket: (id: number, body: { status?: TicketStatus; priority?: TicketPriority }) =>
    api<SupportTicket>(`/api/admin/support-tickets/${id}`, { method: 'PATCH', body }),

  // ---------- activity & notifications ----------
  activity: (p: { action?: string; partnerId?: number; page?: number; size?: number }) =>
    api<Page<ActivityLog>>(`/api/admin/activity-log${qs({ size: 25, ...p })}`),
  notifications: () => api<Page<AppNotification>>('/api/admin/notifications?size=10'),
  unreadCount: () => api<{ unread: number }>('/api/admin/notifications/unread-count'),
  readAllNotifications: () => api<{ marked: number }>('/api/admin/notifications/read-all', { method: 'POST' }),
}
