// TypeScript mirrors of the backend's Java DTOs (records). If a backend DTO changes, change it here too.

export type Page<T> = { content: T[]; page: number; size: number; totalElements: number; totalPages: number }

export type ProblemDetail = {
  status: number
  title?: string
  detail?: string
  code?: string                    // business rule code, e.g. "CODE_TAKEN"
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

export type AccountStatus = 'INVITED' | 'ACTIVE' | 'SUSPENDED'
export type PartnerRole = 'PARTNER_ADMIN' | 'MANAGER' | 'FRONT_DESK' | 'VIEWER'

/** A staff member of the partner (also what /api/partner/me returns). */
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

// ---------- sites & access points ----------

export type SiteType = 'HOTEL' | 'OFFICE' | 'CLUB' | 'PARKING' | 'VENUE' | 'TRANSIT_STATION' | 'HOME'

export type Site = {
  id: number
  name: string
  type: SiteType
  address: string | null
  city: string | null
  country: string | null
  latitude: number | null
  longitude: number | null
  opensAt: string | null   // "08:00:00" (Java LocalTime)
  closesAt: string | null
  open24Hours: boolean
}

export type SiteRequest = {
  name: string
  type: SiteType
  address?: string
  city?: string
  country?: string
  latitude?: number | null
  longitude?: number | null
  opensAt?: string | null
  closesAt?: string | null
}

export type AccessPointType = 'ROOM' | 'DOOR' | 'GATE' | 'PARKING_BARRIER' | 'TURNSTILE' | 'VEHICLE'
export type AccessPointStatus = 'AVAILABLE' | 'OCCUPIED' | 'CLEANING' | 'OUT_OF_SERVICE'
export type AccessMethod = 'CARD' | 'MOBILE' | 'QR' | 'FACE' | 'FINGERPRINT' | 'REMOTE'

export type AccessPoint = {
  id: number
  siteId: number
  code: string
  name: string
  type: AccessPointType
  floor: string | null
  status: AccessPointStatus
  online: boolean
  supportedMethods: AccessMethod[]
}

export type AccessPointRequest = {
  code: string
  name: string
  type: AccessPointType
  floor?: string
  supportedMethods: AccessMethod[]
}

// ---------- permits (digital keys) ----------

export type PermitType = 'HOTEL_KEY' | 'WORKPLACE' | 'CLUB' | 'PARKING' | 'TRANSIT' | 'EVENT_PASS' | 'HOME' | 'CAR'
export type PermitStatus = 'PENDING' | 'ACTIVE' | 'SUSPENDED' | 'EXPIRED' | 'REVOKED'
export type AccessLevel = 'STANDARD' | 'EXTENDED' | 'FULL'
export type DayOfWeek = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY'

export type Permit = {
  id: number
  permitNumber: string
  verificationCode: string
  type: PermitType
  level: AccessLevel
  status: PermitStatus
  holderVornxId: string
  holderName: string
  partnerId: number
  partnerName: string
  siteId: number
  siteName: string
  wholeSite: boolean
  accessPoints: { id: number; code: string; name: string }[]
  validFrom: string
  validUntil: string
  allowedDays: DayOfWeek[]
  allowedFrom: string | null
  allowedTo: string | null
  conditions: string | null
}

export type PermitIssueRequest = {
  customerVornxId: string
  type: PermitType
  siteId: number
  accessPointIds: number[]
  level: AccessLevel
  validFrom?: string
  validUntil: string
  allowedDays: DayOfWeek[]
  allowedFrom?: string | null
  allowedTo?: string | null
  conditions?: string
}

export type Decision = 'ALLOW' | 'DENY'
export type Direction = 'ENTRY' | 'EXIT'
export type EnforcementResult = 'SUCCESS' | 'FAILED' | 'NOT_EXECUTED'
export type DenyReason =
  | 'CARD_INVALID' | 'ACCOUNT_INACTIVE' | 'METHOD_NOT_SUPPORTED' | 'ACCESS_POINT_UNAVAILABLE' | 'NO_PERMIT'
  | 'PERMIT_INACTIVE' | 'PERMIT_NOT_YET_VALID' | 'PERMIT_EXPIRED' | 'DAY_NOT_ALLOWED' | 'OUTSIDE_HOURS'

export type AccessEvent = {
  decisionNumber: string
  occurredAt: string
  siteId: number
  siteName: string
  accessPointCode: string
  accessPointName: string
  method: AccessMethod
  direction: Direction
  decision: Decision
  denyReason: DenyReason | null
  enforcementResult: EnforcementResult | null
  holderVornxId: string | null
  holderName: string | null
  permitNumber: string | null
}

// ---------- plans & subscriptions ----------

export type BillingPeriod = 'WEEKLY' | 'MONTHLY' | 'QUARTERLY' | 'YEARLY'

export type Plan = {
  id: number
  partnerId: number
  partnerName: string
  siteId: number
  siteName: string
  name: string
  description: string | null
  permitType: PermitType
  billingPeriod: BillingPeriod
  price: number
  currency: string
  requiresApproval: boolean
  wholeSite: boolean
  accessPointIds: number[]
  accessPointNames: string[]
  allowedDays: DayOfWeek[]
  allowedFrom: string | null
  allowedTo: string | null
  active: boolean
}

export type PlanRequest = {
  siteId: number
  name: string
  description?: string
  permitType: PermitType
  billingPeriod: BillingPeriod
  price: number
  requiresApproval: boolean
  accessPointIds: number[]
  allowedDays: DayOfWeek[]
  allowedFrom?: string | null
  allowedTo?: string | null
  active: boolean
}

export type RequestType = 'ACTIVATION' | 'RENEWAL'
export type RequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED'

export type ServiceRequest = {
  id: number
  requestNumber: string
  type: RequestType
  status: RequestStatus
  amount: number
  subscriptionId: number
  subscriptionNumber: string
  planName: string
  customerVornxId: string
  customerName: string
  rejectionReason: string | null
  createdAt: string
  decidedAt: string | null
}

export type SubscriptionStatus = 'PENDING' | 'ACTIVE' | 'EXPIRED' | 'CANCELLED' | 'REJECTED'

export type Subscription = {
  id: number
  number: string
  planId: number
  planName: string
  partnerName: string
  siteName: string
  billingPeriod: BillingPeriod
  renewalPrice: number
  status: SubscriptionStatus
  startAt: string | null
  endAt: string | null
  permitNumber: string | null
  customerVornxId: string
  customerName: string
}

// ---------- events ----------

export type EventStatus = 'DRAFT' | 'PUBLISHED' | 'CANCELLED'
export type AgendaItem = { time: string; title: string }

export type VEvent = {
  id: number
  title: string
  description: string | null
  category: string | null
  organizer: string
  siteId: number
  venue: string
  address: string | null
  latitude: number | null
  longitude: number | null
  startsAt: string
  endsAt: string
  doorsOpenAt: string | null
  fee: number
  capacity: number | null
  spotsLeft: number | null
  requiresApproval: boolean
  dressCode: string | null
  contactPhone: string | null
  status: EventStatus
  upcoming: boolean
  accessPointIds: number[]
  agenda: AgendaItem[]
}

export type EventRequest = {
  siteId: number
  title: string
  description?: string
  category?: string
  startsAt: string
  endsAt: string
  doorsOpenAt?: string | null
  fee: number
  capacity?: number | null
  requiresApproval: boolean
  dressCode?: string
  contactPhone?: string
  accessPointIds: number[]
  agenda: AgendaItem[]
}

export type AttendeeRole = 'ATTENDEE' | 'SPEAKER' | 'EXHIBITOR' | 'FOUNDER' | 'VIP' | 'ORGANIZER'
export type EventTicketStatus = 'UNDER_REVIEW' | 'CONFIRMED' | 'CHECKED_IN' | 'CANCELLED' | 'REJECTED'

export type EventTicket = {
  id: number
  ticketNumber: string
  eventId: number
  eventTitle: string
  venue: string
  startsAt: string
  endsAt: string
  upcoming: boolean
  role: AttendeeRole
  passType: string | null
  seat: string | null
  status: EventTicketStatus
  reminder: ReminderOption | null
  permitNumber: string | null
  verificationCode: string | null
  rejectionReason: string | null
  checkedInAt: string | null
  holderVornxId: string
  holderName: string
}

// ---------- billing ----------

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

// ---------- API keys (for the partner's door readers) ----------

export type ApiKey = {
  id: number
  name: string
  keyPrefix: string
  active: boolean
  lastUsedAt: string | null
  createdAt: string
}

export type ApiKeyCreated = { key: ApiKey; apiKey: string }

// ---------- support, activity, notifications, sessions ----------

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

// ---------- the customer ----------

export type LoyaltyTier = 'BRONZE' | 'SILVER' | 'GOLD' | 'PLATINUM'

export type Customer = {
  vornxId: string
  firstName: string
  lastName: string
  email: string
  phone: string
  language: 'AR' | 'EN'
  points: number
  tier: LoyaltyTier
}

export type RegisterRequest = {
  firstName: string
  lastName: string
  email: string
  phone: string
  password: string
  confirmPassword: string
  language: 'AR' | 'EN'
}

export type CardStatus = 'ACTIVE' | 'BLOCKED' | 'LOST' | 'EXPIRED'

export type Card = {
  id: number
  cardNumber: string
  last4: string
  issuedAt: string
  expiresAt: string
  status: CardStatus
}

export type Wallet = { iban: string; balance: number; currency: string }

export type TransactionType = 'TOP_UP' | 'TRANSFER_IN' | 'TRANSFER_OUT' | 'PAYMENT' | 'REFUND' | 'CASHBACK'

export type WalletTransaction = {
  reference: string
  type: TransactionType
  direction: 'IN' | 'OUT'
  amount: number
  balanceAfter: number
  description: string | null
  counterpartyName: string | null
  partnerName: string | null
  createdAt: string
}

/** The answer to "open this door from my phone". */
export type AccessDecision = {
  decisionNumber: string
  decision: Decision
  denyReason: DenyReason | null
  occurredAt: string
  accessPointId: number
  accessPointName: string
  permitNumber: string | null
  enforcementResult: EnforcementResult | null
}

export type ReminderOption = 'ONE_HOUR' | 'ONE_DAY' | 'ONE_WEEK'
