import { api } from './client'
import type {
  AccessEvent, AccessPoint, AccessPointRequest, AccessPointStatus, ActivityLog, ApiKey, ApiKeyCreated, AppNotification,
  AttendeeRole, AuthResponse, Contract, EventRequest, EventTicket, InviteResponse, Invoice, Page, PartnerRole, PartnerUser,
  AccountStatus, Permit, PermitIssueRequest, Plan, PlanRequest, RequestStatus, ServiceRequest, Session, Site, SiteRequest,
  Subscription, SupportTicket, TicketCategory, TicketPriority, VEvent,
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

const P = '/api/partner'

// One function per backend endpoint used by the portal. The paths match the Spring controllers.
// Note: no endpoint takes a partnerId. The backend reads it from the login token, so a partner
// can never ask for another partner's data (it gets 404).
export const endpoints = {
  // ---------- auth ----------
  login: (email: string, password: string) =>
    api<AuthResponse>('/api/public/auth/partner/login', { method: 'POST', body: { email, password }, auth: false }),
  acceptInvite: (token: string, password: string, confirmPassword: string) =>
    api<AuthResponse>('/api/public/auth/partner/accept-invite', { method: 'POST', body: { token, password, confirmPassword }, auth: false }),
  logout: (refreshToken: string) =>
    api<void>('/api/public/auth/logout', { method: 'POST', body: { refreshToken }, auth: false }),
  forgotPassword: (email: string) =>
    api<void>('/api/public/auth/partner/forgot-password', { method: 'POST', body: { email }, auth: false }),
  resetPassword: (token: string, password: string, confirmPassword: string) =>
    api<void>('/api/public/auth/reset-password', { method: 'POST', body: { token, password, confirmPassword }, auth: false }),
  me: () => api<PartnerUser>(`${P}/me`),
  changePassword: (currentPassword: string, newPassword: string, confirmPassword: string) =>
    api<void>(`${P}/me/password`, { method: 'POST', body: { currentPassword, newPassword, confirmPassword } }),
  sessions: () => api<Session[]>(`${P}/sessions`),
  revokeSession: (id: number) => api<void>(`${P}/sessions/${id}`, { method: 'DELETE' }),
  revokeOtherSessions: () => api<{ revoked: number }>(`${P}/sessions?keepCurrent=true`, { method: 'DELETE' }),

  // ---------- sites & access points ----------
  sites: () => api<Site[]>(`${P}/sites`),
  createSite: (body: SiteRequest) => api<Site>(`${P}/sites`, { method: 'POST', body }),
  updateSite: (id: number, body: SiteRequest) => api<Site>(`${P}/sites/${id}`, { method: 'PUT', body }),
  accessPoints: (siteId: number) => api<AccessPoint[]>(`${P}/sites/${siteId}/access-points`),
  createAccessPoint: (siteId: number, body: AccessPointRequest) =>
    api<AccessPoint>(`${P}/sites/${siteId}/access-points`, { method: 'POST', body }),
  updateAccessPoint: (id: number, body: AccessPointRequest) =>
    api<AccessPoint>(`${P}/access-points/${id}`, { method: 'PUT', body }),
  setAccessPointStatus: (id: number, status: AccessPointStatus) =>
    api<AccessPoint>(`${P}/access-points/${id}/status`, { method: 'PATCH', body: { status } }),

  // ---------- permits & live access ----------
  permits: (p: { siteId?: number; page?: number; size?: number }) =>
    api<Page<Permit>>(`${P}/permits${qs({ size: 15, ...p })}`),
  permit: (id: number) => api<Permit>(`${P}/permits/${id}`),
  issuePermit: (body: PermitIssueRequest) => api<Permit>(`${P}/permits`, { method: 'POST', body }),
  revokePermit: (id: number) => api<Permit>(`${P}/permits/${id}/revoke`, { method: 'POST' }),
  extendPermit: (id: number, validUntil: string) =>
    api<Permit>(`${P}/permits/${id}/extend`, { method: 'PATCH', body: { validUntil } }),
  accessEvents: (p: { siteId?: number; page?: number; size?: number }) =>
    api<Page<AccessEvent>>(`${P}/access-events${qs({ size: 30, ...p })}`),

  // ---------- plans & subscriptions ----------
  plans: () => api<Plan[]>(`${P}/plans`),
  createPlan: (body: PlanRequest) => api<Plan>(`${P}/plans`, { method: 'POST', body }),
  updatePlan: (id: number, body: PlanRequest) => api<Plan>(`${P}/plans/${id}`, { method: 'PUT', body }),
  requests: (p: { status?: RequestStatus; page?: number; size?: number }) =>
    api<Page<ServiceRequest>>(`${P}/subscription-requests${qs({ size: 15, ...p })}`),
  approveRequest: (id: number) => api<ServiceRequest>(`${P}/subscription-requests/${id}/approve`, { method: 'POST' }),
  rejectRequest: (id: number, reason: string) =>
    api<ServiceRequest>(`${P}/subscription-requests/${id}/reject`, { method: 'POST', body: { reason } }),
  subscriptions: (p: { page?: number }) => api<Page<Subscription>>(`${P}/subscriptions${qs({ size: 15, ...p })}`),

  // ---------- events ----------
  events: () => api<VEvent[]>(`${P}/events`),
  createEvent: (body: EventRequest) => api<VEvent>(`${P}/events`, { method: 'POST', body }),
  updateEvent: (id: number, body: EventRequest) => api<VEvent>(`${P}/events/${id}`, { method: 'PUT', body }),
  publishEvent: (id: number) => api<VEvent>(`${P}/events/${id}/publish`, { method: 'POST' }),
  cancelEvent: (id: number) => api<VEvent>(`${P}/events/${id}/cancel`, { method: 'POST' }),
  eventTickets: (id: number) => api<EventTicket[]>(`${P}/events/${id}/tickets`),
  approveTicket: (id: number) => api<EventTicket>(`${P}/events/tickets/${id}/approve`, { method: 'POST' }),
  rejectTicket: (id: number, reason: string) =>
    api<EventTicket>(`${P}/events/tickets/${id}/reject`, { method: 'POST', body: { reason } }),
  updateTicket: (id: number, body: { role?: AttendeeRole; passType?: string; seat?: string }) =>
    api<EventTicket>(`${P}/events/tickets/${id}`, { method: 'PATCH', body }),

  // ---------- contract & invoices ----------
  contract: () => api<Contract>(`${P}/contract`),
  invoices: () => api<Invoice[]>(`${P}/invoices`),
  requestRenewal: () => api<SupportTicket>(`${P}/contract/renewal-request`, { method: 'POST' }),

  // ---------- team & API keys ----------
  team: () => api<PartnerUser[]>(`${P}/team`),
  invite: (body: { fullName: string; email: string; role: PartnerRole }) =>
    api<InviteResponse>(`${P}/team`, { method: 'POST', body }),
  updateMember: (id: number, body: { role?: PartnerRole; status?: AccountStatus }) =>
    api<PartnerUser>(`${P}/team/${id}`, { method: 'PATCH', body }),
  apiKeys: () => api<ApiKey[]>(`${P}/api-keys`),
  createApiKey: (name: string) => api<ApiKeyCreated>(`${P}/api-keys`, { method: 'POST', body: { name } }),
  revokeApiKey: (id: number) => api<ApiKey>(`${P}/api-keys/${id}/revoke`, { method: 'POST' }),

  // ---------- support ----------
  tickets: (p: { page?: number }) => api<Page<SupportTicket>>(`${P}/support-tickets${qs({ size: 15, ...p })}`),
  ticket: (id: number) => api<SupportTicket>(`${P}/support-tickets/${id}`),
  openTicket: (body: { category: TicketCategory; subject: string; priority: TicketPriority; message: string }) =>
    api<SupportTicket>(`${P}/support-tickets`, { method: 'POST', body }),
  replyTicket: (id: number, body: string) =>
    api<SupportTicket>(`${P}/support-tickets/${id}/messages`, { method: 'POST', body: { body } }),

  // ---------- activity & notifications ----------
  activity: (p: { action?: string; page?: number; size?: number }) =>
    api<Page<ActivityLog>>(`${P}/activity-log${qs({ size: 25, ...p })}`),
  notifications: () => api<Page<AppNotification>>(`${P}/notifications?size=10`),
  unreadCount: () => api<{ unread: number }>(`${P}/notifications/unread-count`),
  readAllNotifications: () => api<{ marked: number }>(`${P}/notifications/read-all`, { method: 'POST' }),
}
