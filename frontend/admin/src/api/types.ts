// TypeScript mirrors of the backend's Java DTOs (records). If a backend DTO changes, change it here too.

export type Page<T> = { content: T[]; page: number; size: number; totalElements: number; totalPages: number }

export type ProblemDetail = {
  status: number
  title?: string
  detail?: string
  code?: string                    // business rule code, e.g. "CR_TAKEN"
  errors?: Record<string, string>  // field validation errors
}

export type AuthResponse = {
  accessToken: string
  tokenType: 'Bearer'
  expiresIn: number
  refreshToken: string
  refreshExpiresIn: number
  userType: 'ADMIN' | 'PARTNER_USER' | 'CUSTOMER'
  role: string | null
  sessionId: number
}

export type AdminRole = 'SUPER_ADMIN' | 'ACCOUNT_MANAGER' | 'SUPPORT_AGENT' | 'AUDITOR'
export type AccountStatus = 'INVITED' | 'ACTIVE' | 'SUSPENDED'

export type AdminUser = {
  id: number
  fullName: string
  email: string
  phone: string | null
  role: AdminRole
  status: AccountStatus
}

export type Sector = 'HOTEL' | 'BANK' | 'CLUB' | 'TRANSPORT' | 'PARKING' | 'WORKPLACE' | 'EVENTS' | 'AUTOMOTIVE'
export type PartnerStatus = 'ONBOARDING' | 'ACTIVE' | 'SUSPENDED'

export type Partner = {
  id: number
  name: string
  sector: Sector
  crNumber: string | null
  phone: string | null
  city: string | null
  country: string | null
  status: PartnerStatus
  notes: string | null
  accountManagerId: number | null
  accountManagerName: string | null
  createdAt: string
}

export type PartnerRequest = {
  name: string
  sector: Sector
  crNumber?: string
  phone?: string
  city?: string
  country?: string
  notes?: string
  accountManagerId?: number | null
}

export type PartnerRole = 'PARTNER_ADMIN' | 'MANAGER' | 'FRONT_DESK' | 'VIEWER'

export type PartnerUser = {
  id: number
  fullName: string
  email: string
  role: PartnerRole
  status: AccountStatus
  partnerId: number
  partnerName: string
  lastLoginAt: string | null
}

export type InviteResponse = { user: PartnerUser; inviteToken: string | null; inviteExpiresAt: string }

export type Site = {
  id: number
  name: string
  type: string
  address: string | null
  city: string | null
  country: string | null
  opensAt: string | null
  closesAt: string | null
  open24Hours: boolean
}

export type PlanTier = 'BASIC' | 'PROFESSIONAL' | 'ENTERPRISE'
export type ContractStatus = 'ACTIVE' | 'EXPIRING_SOON' | 'EXPIRED' | 'TERMINATED'

export type Contract = {
  id: number
  number: string
  partnerId: number
  partnerName: string
  planName: string
  tier: PlanTier
  monthlyFee: number
  maxBeneficiaries: number | null
  startDate: string
  endDate: string
  daysLeft: number
  progressPercent: number
  renewalCount: number
  status: ContractStatus
  notes: string | null
}

export type ContractRequest = {
  planName: string
  tier: PlanTier
  monthlyFee: number
  maxBeneficiaries?: number | null
  startDate: string
  endDate: string
  notes?: string
}

export type InvoiceStatus = 'DUE' | 'PAID' | 'OVERDUE'

export type Invoice = {
  id: number
  number: string
  partnerId: number
  partnerName: string
  contractNumber: string
  period: string
  amount: number
  currency: string
  issueDate: string
  dueDate: string
  paidAt: string | null
  status: InvoiceStatus
}

export type BillingSummary = {
  activePartners: number
  runningContracts: number
  monthlyRecurringRevenue: number
  contractsNeedingAttention: number
  overdueInvoices: number
  overdueAmount: number
  unpaidAmount: number
  openSupportTickets: number
}

export type TicketStatus = 'OPEN' | 'IN_PROGRESS' | 'CLOSED'
export type TicketPriority = 'LOW' | 'MEDIUM' | 'HIGH'
export type TicketCategory = 'DIGITAL_KEY_ISSUE' | 'NEW_SERVICE' | 'BILLING_AND_CONTRACT' | 'OTHER'
export type ActorType = 'ADMIN' | 'PARTNER_USER' | 'CUSTOMER' | 'SYSTEM'

export type SupportTicket = {
  id: number
  number: string
  partnerId: number
  partnerName: string
  category: TicketCategory
  subject: string
  priority: TicketPriority
  status: TicketStatus
  assignedTo: string | null
  createdAt: string
  lastMessageAt: string
  messages: { senderType: ActorType; senderName: string; body: string; sentAt: string }[] | null
}

export type ActivityLog = {
  id: number
  at: string
  actorType: ActorType
  actorId: number | null
  actorName: string
  partnerId: number | null
  action: string
  targetType: string | null
  targetId: number | null
  details: string | null
  ipAddress: string | null
}

export type AppNotification = {
  id: number
  type: string
  titleAr: string
  titleEn: string
  bodyAr: string
  bodyEn: string
  referenceType: string | null
  referenceId: number | null
  read: boolean
  createdAt: string
}

export type Session = {
  id: number
  userAgent: string | null
  ipAddress: string | null
  createdAt: string
  lastUsedAt: string
  expiresAt: string
  current: boolean
}
