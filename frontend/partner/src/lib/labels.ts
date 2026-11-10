// Arabic labels for the backend's enum values, and the color ("tone") each status gets.
import type {
  AccessLevel, AccessMethod, AccessPointStatus, AccessPointType, AccountStatus, ActorType, AttendeeRole, BillingPeriod,
  ContractStatus, DayOfWeek, DenyReason, EventStatus, EventTicketStatus, InvoiceStatus, PartnerRole, PermitStatus,
  PermitType, PlanTier, RequestStatus, RequestType, SiteType, SubscriptionStatus, TicketCategory, TicketPriority,
  TicketStatus,
} from '../api/types'

export type Tone = 'ok' | 'soon' | 'bad' | 'mu'
export type Label = { text: string; tone: Tone }

/** Object.keys with the right type: keys(SITE_TYPE) -> SiteType[] */
export const keys = <K extends string>(o: Record<K, unknown>) => Object.keys(o) as K[]

export const SITE_TYPE: Record<SiteType, string> = {
  HOTEL: 'فندق', OFFICE: 'مكتب / مقر عمل', CLUB: 'نادٍ', PARKING: 'مواقف', VENUE: 'قاعة / مكان فعاليات',
  TRANSIT_STATION: 'محطة نقل', HOME: 'سكن',
}

export const AP_TYPE: Record<AccessPointType, string> = {
  ROOM: 'غرفة', DOOR: 'باب', GATE: 'بوابة', PARKING_BARRIER: 'حاجز مواقف', TURNSTILE: 'بوابة دوّارة', VEHICLE: 'مركبة',
}

export const AP_STATUS: Record<AccessPointStatus, Label> = {
  AVAILABLE: { text: 'متاحة', tone: 'ok' },
  OCCUPIED: { text: 'مشغولة', tone: 'soon' },
  CLEANING: { text: 'قيد التنظيف', tone: 'soon' },
  OUT_OF_SERVICE: { text: 'خارج الخدمة', tone: 'bad' },
}

export const METHOD: Record<AccessMethod, string> = {
  CARD: 'البطاقة', MOBILE: 'الجوال', QR: 'رمز QR', FACE: 'الوجه', FINGERPRINT: 'البصمة', REMOTE: 'فتح عن بُعد',
}

export const PERMIT_TYPE: Record<PermitType, string> = {
  HOTEL_KEY: 'مفتاح غرفة', WORKPLACE: 'دوام', CLUB: 'عضوية نادٍ', PARKING: 'مواقف', TRANSIT: 'نقل',
  EVENT_PASS: 'تصريح فعالية', HOME: 'سكن', CAR: 'مركبة',
}

export const PERMIT_STATUS: Record<PermitStatus, Label> = {
  PENDING: { text: 'لم يبدأ بعد', tone: 'soon' },
  ACTIVE: { text: 'مفعّل', tone: 'ok' },
  SUSPENDED: { text: 'موقوف', tone: 'soon' },
  EXPIRED: { text: 'منتهٍ', tone: 'mu' },
  REVOKED: { text: 'ملغى', tone: 'bad' },
}

export const LEVEL: Record<AccessLevel, string> = { STANDARD: 'عادي', EXTENDED: 'موسّع', FULL: 'كامل' }

/** Saturday first, like a Saudi week. */
export const DAYS: [DayOfWeek, string][] = [
  ['SATURDAY', 'السبت'], ['SUNDAY', 'الأحد'], ['MONDAY', 'الاثنين'], ['TUESDAY', 'الثلاثاء'],
  ['WEDNESDAY', 'الأربعاء'], ['THURSDAY', 'الخميس'], ['FRIDAY', 'الجمعة'],
]
export const DAY: Record<DayOfWeek, string> = Object.fromEntries(DAYS) as Record<DayOfWeek, string>

export const DENY: Record<DenyReason, string> = {
  CARD_INVALID: 'بطاقة غير صالحة', ACCOUNT_INACTIVE: 'حساب غير نشط', METHOD_NOT_SUPPORTED: 'طريقة غير مدعومة',
  ACCESS_POINT_UNAVAILABLE: 'النقطة غير متاحة', NO_PERMIT: 'لا يوجد تصريح', PERMIT_INACTIVE: 'تصريح غير نشط',
  PERMIT_NOT_YET_VALID: 'التصريح لم يبدأ', PERMIT_EXPIRED: 'تصريح منتهٍ', DAY_NOT_ALLOWED: 'يوم غير مسموح',
  OUTSIDE_HOURS: 'خارج الأوقات المسموحة',
}

export const PERIOD: Record<BillingPeriod, string> = { WEEKLY: 'أسبوعي', MONTHLY: 'شهري', QUARTERLY: 'ربع سنوي', YEARLY: 'سنوي' }

export const REQUEST_TYPE: Record<RequestType, string> = { ACTIVATION: 'اشتراك جديد', RENEWAL: 'تجديد' }

export const REQUEST_STATUS: Record<RequestStatus, Label> = {
  PENDING: { text: 'بانتظار القرار', tone: 'soon' },
  APPROVED: { text: 'مقبول', tone: 'ok' },
  REJECTED: { text: 'مرفوض', tone: 'bad' },
  CANCELLED: { text: 'ألغاه العميل', tone: 'mu' },
}

export const SUB_STATUS: Record<SubscriptionStatus, Label> = {
  PENDING: { text: 'بانتظار الموافقة', tone: 'soon' },
  ACTIVE: { text: 'نشط', tone: 'ok' },
  EXPIRED: { text: 'منتهٍ', tone: 'mu' },
  CANCELLED: { text: 'ملغى', tone: 'mu' },
  REJECTED: { text: 'مرفوض', tone: 'bad' },
}

export const EVENT_STATUS: Record<EventStatus, Label> = {
  DRAFT: { text: 'مسودة', tone: 'mu' },
  PUBLISHED: { text: 'منشورة', tone: 'ok' },
  CANCELLED: { text: 'ملغاة', tone: 'bad' },
}

export const TICKET_STATE: Record<EventTicketStatus, Label> = {
  UNDER_REVIEW: { text: 'قيد المراجعة', tone: 'soon' },
  CONFIRMED: { text: 'مؤكدة', tone: 'ok' },
  CHECKED_IN: { text: 'حضر', tone: 'ok' },
  CANCELLED: { text: 'ملغاة', tone: 'mu' },
  REJECTED: { text: 'مرفوضة', tone: 'bad' },
}

export const ATTENDEE: Record<AttendeeRole, string> = {
  ATTENDEE: 'حضور', SPEAKER: 'متحدث', EXHIBITOR: 'عارض', FOUNDER: 'مؤسس', VIP: 'كبار الضيوف', ORGANIZER: 'منظّم',
}

export const CONTRACT_STATUS: Record<ContractStatus, Label> = {
  ACTIVE: { text: 'ساري', tone: 'ok' },
  EXPIRING_SOON: { text: 'ينتهي قريبًا', tone: 'soon' },
  EXPIRED: { text: 'منتهٍ', tone: 'bad' },
  TERMINATED: { text: 'ملغى', tone: 'mu' },
}

export const INVOICE_STATUS: Record<InvoiceStatus, Label> = {
  DUE: { text: 'مستحقة', tone: 'soon' },
  PAID: { text: 'مدفوعة', tone: 'ok' },
  OVERDUE: { text: 'متأخرة', tone: 'bad' },
}

export const TIER: Record<PlanTier, string> = { BASIC: 'أساسية', PROFESSIONAL: 'احترافية', ENTERPRISE: 'مؤسسات' }

export const TICKET_STATUS: Record<TicketStatus, Label> = {
  OPEN: { text: 'مفتوحة', tone: 'bad' },
  IN_PROGRESS: { text: 'قيد المعالجة', tone: 'soon' },
  CLOSED: { text: 'مغلقة', tone: 'ok' },
}

export const PRIORITY: Record<TicketPriority, Label> = {
  LOW: { text: 'منخفضة', tone: 'mu' },
  MEDIUM: { text: 'متوسطة', tone: 'soon' },
  HIGH: { text: 'عالية', tone: 'bad' },
}

export const CATEGORY: Record<TicketCategory, string> = {
  DIGITAL_KEY_ISSUE: 'مشكلة في مفتاح رقمي', NEW_SERVICE: 'طلب خدمة جديدة',
  BILLING_AND_CONTRACT: 'الفواتير والعقد', OTHER: 'أخرى',
}

export const ACCOUNT_STATUS: Record<AccountStatus, Label> = {
  INVITED: { text: 'مدعو', tone: 'soon' },
  ACTIVE: { text: 'نشط', tone: 'ok' },
  SUSPENDED: { text: 'معلّق', tone: 'bad' },
}

export const PARTNER_ROLE: Record<PartnerRole, string> = {
  PARTNER_ADMIN: 'مدير الجهة', MANAGER: 'مشرف', FRONT_DESK: 'استقبال', VIEWER: 'مشاهدة فقط',
}

/** What each role can do (shown in the team page so the admin picks the right one). */
export const ROLE_HINT: Record<PartnerRole, string> = {
  PARTNER_ADMIN: 'كل شيء، بما فيه الفريق ومفاتيح الأجهزة وطلب تجديد العقد',
  MANAGER: 'المواقع والباقات والفعاليات، والتصاريح والطلبات',
  FRONT_DESK: 'إصدار التصاريح والبت في الطلبات وحالة الغرف',
  VIEWER: 'عرض فقط',
}

export const ACTOR: Record<ActorType, string> = {
  ADMIN: 'فريق VORNX', PARTNER_USER: 'موظف', CUSTOMER: 'عميل', SYSTEM: 'النظام / جهاز',
}

/** Activity log actions -> readable Arabic. Unknown codes are shown as they are. */
export const ACTIONS: Record<string, string> = {
  TEAM_MEMBER_INVITED: 'دعوة موظف', TEAM_MEMBER_UPDATED: 'تعديل موظف',
  API_KEY_CREATED: 'إنشاء مفتاح جهاز', API_KEY_REVOKED: 'إلغاء مفتاح جهاز', SITE_CREATED: 'إضافة موقع',
  SITE_UPDATED: 'تعديل موقع', ACCESS_POINT_CREATED: 'إضافة نقطة دخول', ACCESS_POINT_UPDATED: 'تعديل نقطة دخول',
  ACCESS_POINT_STATUS_CHANGED: 'تغيير حالة نقطة دخول', PERMIT_ISSUED: 'إصدار تصريح', PERMIT_REVOKED: 'إلغاء تصريح',
  PERMIT_EXTENDED: 'تمديد تصريح', PLAN_CREATED: 'إنشاء باقة', PLAN_UPDATED: 'تعديل باقة',
  SUBSCRIPTION_REQUEST_APPROVED: 'قبول طلب اشتراك', SUBSCRIPTION_REQUEST_REJECTED: 'رفض طلب اشتراك',
  EVENT_CREATED: 'إنشاء فعالية', EVENT_UPDATED: 'تعديل فعالية', EVENT_PUBLISHED: 'نشر فعالية',
  EVENT_CANCELLED: 'إلغاء فعالية', EVENT_TICKET_APPROVED: 'قبول تذكرة', EVENT_TICKET_REJECTED: 'رفض تذكرة',
  EVENT_TICKET_UPDATED: 'تعديل تذكرة', CONTRACT_RENEWAL_REQUESTED: 'طلب تجديد العقد',
  SUPPORT_TICKET_OPENED: 'فتح طلب دعم', SUPPORT_REPLIED: 'رد على طلب دعم', PASSWORD_CHANGED: 'تغيير كلمة المرور',
  PARTNER_USER_INVITED: 'دعوة موظف (من VORNX)', PARTNER_STATUS_CHANGED: 'تغيير حالة الجهة', CONTRACT_CREATED: 'إنشاء العقد',
  CONTRACT_RENEWED: 'تجديد العقد', INVOICES_GENERATED: 'إصدار فاتورة', INVOICE_PAID: 'تسجيل دفع فاتورة',
}
