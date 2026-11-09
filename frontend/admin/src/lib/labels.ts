// Arabic labels for the backend's enum values, and the color ("tone") each status gets.
import type {
  AccountStatus, ActorType, AdminRole, ContractStatus, InvoiceStatus, PartnerRole, PartnerStatus, PlanTier,
  Sector, TicketCategory, TicketPriority, TicketStatus,
} from '../api/types'

export type Tone = 'ok' | 'soon' | 'bad' | 'mu'
export type Label = { text: string; tone: Tone }

export const SECTORS: Record<Sector, string> = {
  HOTEL: 'الفنادق', BANK: 'الدفع', CLUB: 'الأندية', TRANSPORT: 'النقل', PARKING: 'المواقف',
  WORKPLACE: 'الدوام', EVENTS: 'الفعاليات والمؤتمرات', AUTOMOTIVE: 'السيارات',
}

export const PARTNER_STATUS: Record<PartnerStatus, Label> = {
  ONBOARDING: { text: 'قيد التهيئة', tone: 'soon' },
  ACTIVE: { text: 'نشطة', tone: 'ok' },
  SUSPENDED: { text: 'معلّقة', tone: 'bad' },
}

export const CONTRACT_STATUS: Record<ContractStatus, Label> = {
  ACTIVE: { text: 'ساري', tone: 'ok' },
  EXPIRING_SOON: { text: 'ينتهي قريبًا', tone: 'soon' },
  EXPIRED: { text: 'متأخر', tone: 'bad' },
  TERMINATED: { text: 'ملغى', tone: 'mu' },
}

export const INVOICE_STATUS: Record<InvoiceStatus, Label> = {
  DUE: { text: 'مستحقة', tone: 'soon' },
  PAID: { text: 'مدفوعة', tone: 'ok' },
  OVERDUE: { text: 'متأخرة', tone: 'bad' },
}

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

export const TIER: Record<PlanTier, string> = { BASIC: 'أساسية', PROFESSIONAL: 'احترافية', ENTERPRISE: 'مؤسسات' }

export const ACCOUNT_STATUS: Record<AccountStatus, Label> = {
  INVITED: { text: 'مدعو', tone: 'soon' },
  ACTIVE: { text: 'نشط', tone: 'ok' },
  SUSPENDED: { text: 'معلّق', tone: 'bad' },
}

export const ADMIN_ROLE: Record<AdminRole, string> = {
  SUPER_ADMIN: 'مدير عام', ACCOUNT_MANAGER: 'مدير حسابات', SUPPORT_AGENT: 'دعم فني', AUDITOR: 'مدقق',
}

export const PARTNER_ROLE: Record<PartnerRole, string> = {
  PARTNER_ADMIN: 'مدير الجهة', MANAGER: 'مشرف', FRONT_DESK: 'استقبال', VIEWER: 'مشاهدة فقط',
}

export const ACTOR: Record<ActorType, string> = {
  ADMIN: 'فريق VORNX', PARTNER_USER: 'موظف جهة', CUSTOMER: 'عميل', SYSTEM: 'النظام / جهاز',
}

/** Activity log actions -> readable Arabic. Unknown codes are shown as they are. */
export const ACTIONS: Record<string, string> = {
  PARTNER_CREATED: 'إضافة جهة', PARTNER_UPDATED: 'تعديل جهة', PARTNER_STATUS_CHANGED: 'تغيير حالة جهة',
  PARTNER_USER_INVITED: 'دعوة موظف جهة', CARD_ISSUED: 'إصدار بطاقة', CONTRACT_CREATED: 'إنشاء عقد',
  CONTRACT_RENEWED: 'تجديد عقد', CONTRACT_TERMINATED: 'إلغاء عقد', INVOICES_GENERATED: 'إصدار فواتير',
  INVOICE_PAID: 'تسجيل دفع فاتورة', SUPPORT_REPLIED: 'رد على طلب دعم', SUPPORT_TICKET_UPDATED: 'تحديث طلب دعم',
  SUPPORT_TICKET_OPENED: 'فتح طلب دعم', TEAM_MEMBER_INVITED: 'دعوة موظف', TEAM_MEMBER_UPDATED: 'تعديل موظف',
  API_KEY_CREATED: 'إنشاء مفتاح API', API_KEY_REVOKED: 'إلغاء مفتاح API', SITE_CREATED: 'إضافة موقع',
  SITE_UPDATED: 'تعديل موقع', ACCESS_POINT_CREATED: 'إضافة نقطة دخول', ACCESS_POINT_UPDATED: 'تعديل نقطة دخول',
  ACCESS_POINT_STATUS_CHANGED: 'تغيير حالة نقطة دخول', PERMIT_ISSUED: 'إصدار تصريح', PERMIT_REVOKED: 'إلغاء تصريح',
  PERMIT_EXTENDED: 'تمديد تصريح', PLAN_CREATED: 'إنشاء باقة', PLAN_UPDATED: 'تعديل باقة',
  SUBSCRIPTION_REQUEST_APPROVED: 'قبول طلب اشتراك', SUBSCRIPTION_REQUEST_REJECTED: 'رفض طلب اشتراك',
  EVENT_CREATED: 'إنشاء فعالية', EVENT_UPDATED: 'تعديل فعالية', EVENT_PUBLISHED: 'نشر فعالية',
  EVENT_CANCELLED: 'إلغاء فعالية', EVENT_TICKET_APPROVED: 'قبول تذكرة', EVENT_TICKET_REJECTED: 'رفض تذكرة',
  EVENT_TICKET_UPDATED: 'تعديل تذكرة', CONTRACT_RENEWAL_REQUESTED: 'طلب تجديد عقد',
  CARD_REPORTED_LOST: 'الإبلاغ عن فقد بطاقة', PASSWORD_CHANGED: 'تغيير كلمة المرور',
}
