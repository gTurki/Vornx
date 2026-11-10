// Arabic labels for the backend's enum values, and the color ("tone") each status gets.
import type {
  AttendeeRole, BillingPeriod, CardStatus, DayOfWeek, DenyReason, EventTicketStatus, LoyaltyTier, PermitStatus,
  PermitType, ReminderOption, RequestStatus, RequestType, SubscriptionStatus, TransactionType,
} from '../api/types'

export type Tone = 'ok' | 'soon' | 'bad' | 'mu'
export type Label = { text: string; tone: Tone }

export const keys = <K extends string>(o: Record<K, unknown>) => Object.keys(o) as K[]

export const TIER: Record<LoyaltyTier, { text: string; next: number | null }> = {
  BRONZE: { text: 'برونزية', next: 1000 },
  SILVER: { text: 'فضية', next: 2500 },
  GOLD: { text: 'ذهبية', next: 5000 },
  PLATINUM: { text: 'بلاتينية', next: null },
}

export const CARD_STATUS: Record<CardStatus, Label> = {
  ACTIVE: { text: 'فعّالة', tone: 'ok' },
  BLOCKED: { text: 'موقوفة', tone: 'bad' },
  LOST: { text: 'مُبلّغ عن فقدها', tone: 'bad' },
  EXPIRED: { text: 'منتهية', tone: 'mu' },
}

export const PERMIT_TYPE: Record<PermitType, string> = {
  HOTEL_KEY: 'مفتاح غرفة', WORKPLACE: 'دوام', CLUB: 'نادٍ', PARKING: 'موقف', TRANSIT: 'نقل',
  EVENT_PASS: 'فعالية', HOME: 'المنزل', CAR: 'سيارة',
}

/** Same short codes as the icon set, one per permit type. */
export const PERMIT_ICON: Record<PermitType, 'bed' | 'work' | 'gym' | 'parking' | 'bus' | 'ticket' | 'home' | 'car'> = {
  HOTEL_KEY: 'bed', WORKPLACE: 'work', CLUB: 'gym', PARKING: 'parking', TRANSIT: 'bus',
  EVENT_PASS: 'ticket', HOME: 'home', CAR: 'car',
}

export const PERMIT_STATUS: Record<PermitStatus, Label> = {
  PENDING: { text: 'يبدأ لاحقًا', tone: 'soon' },
  ACTIVE: { text: 'فعّال', tone: 'ok' },
  SUSPENDED: { text: 'موقوف', tone: 'soon' },
  EXPIRED: { text: 'منتهٍ', tone: 'mu' },
  REVOKED: { text: 'ملغى', tone: 'bad' },
}

export const DAYS: [DayOfWeek, string][] = [
  ['SATURDAY', 'السبت'], ['SUNDAY', 'الأحد'], ['MONDAY', 'الاثنين'], ['TUESDAY', 'الثلاثاء'],
  ['WEDNESDAY', 'الأربعاء'], ['THURSDAY', 'الخميس'], ['FRIDAY', 'الجمعة'],
]
export const DAY: Record<DayOfWeek, string> = Object.fromEntries(DAYS) as Record<DayOfWeek, string>

/** Why a door said no, in words a customer understands. */
export const DENY: Record<DenyReason, string> = {
  CARD_INVALID: 'البطاقة غير صالحة',
  ACCOUNT_INACTIVE: 'حسابك غير نشط',
  METHOD_NOT_SUPPORTED: 'هذا الباب لا يدعم الفتح عن بُعد',
  ACCESS_POINT_UNAVAILABLE: 'الباب غير متاح الآن',
  NO_PERMIT: 'ليس لديك تصريح لهذا الباب',
  PERMIT_INACTIVE: 'تصريحك غير نشط',
  PERMIT_NOT_YET_VALID: 'تصريحك لم يبدأ بعد',
  PERMIT_EXPIRED: 'انتهى تصريحك',
  DAY_NOT_ALLOWED: 'تصريحك لا يشمل هذا اليوم',
  OUTSIDE_HOURS: 'خارج الأوقات المسموحة',
}

export const PERIOD: Record<BillingPeriod, string> = { WEEKLY: 'أسبوع', MONTHLY: 'شهر', QUARTERLY: '3 أشهر', YEARLY: 'سنة' }

export const REQUEST_TYPE: Record<RequestType, string> = { ACTIVATION: 'اشتراك جديد', RENEWAL: 'تجديد' }

export const REQUEST_STATUS: Record<RequestStatus, Label> = {
  PENDING: { text: 'قيد المراجعة', tone: 'soon' },
  APPROVED: { text: 'مقبول', tone: 'ok' },
  REJECTED: { text: 'مرفوض', tone: 'bad' },
  CANCELLED: { text: 'ألغيته', tone: 'mu' },
}

export const SUB_STATUS: Record<SubscriptionStatus, Label> = {
  PENDING: { text: 'بانتظار الموافقة', tone: 'soon' },
  ACTIVE: { text: 'فعّال', tone: 'ok' },
  EXPIRED: { text: 'منتهٍ', tone: 'mu' },
  CANCELLED: { text: 'ملغى', tone: 'mu' },
  REJECTED: { text: 'مرفوض', tone: 'bad' },
}

export const TICKET_STATE: Record<EventTicketStatus, Label> = {
  UNDER_REVIEW: { text: 'قيد المراجعة', tone: 'soon' },
  CONFIRMED: { text: 'مؤكدة', tone: 'ok' },
  CHECKED_IN: { text: 'حضرت', tone: 'ok' },
  CANCELLED: { text: 'ملغاة', tone: 'mu' },
  REJECTED: { text: 'مرفوضة', tone: 'bad' },
}

export const ATTENDEE: Record<AttendeeRole, string> = {
  ATTENDEE: 'حضور', SPEAKER: 'متحدث', EXHIBITOR: 'عارض', FOUNDER: 'مؤسس', VIP: 'كبار الضيوف', ORGANIZER: 'منظّم',
}

export const REMINDER: Record<ReminderOption, string> = { ONE_HOUR: 'قبل ساعة', ONE_DAY: 'قبل يوم', ONE_WEEK: 'قبل أسبوع' }

export const TX: Record<TransactionType, string> = {
  TOP_UP: 'شحن المحفظة', TRANSFER_IN: 'تحويل وارد', TRANSFER_OUT: 'تحويل صادر', PAYMENT: 'دفع',
  REFUND: 'استرداد', CASHBACK: 'استرداد نقدي',
}
